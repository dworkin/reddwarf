/*
 * Copyright (c) 2007 - 2009, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This file provides implementations of functions relating to client-server
 * network connections.  Implements functions declared in sgs_connection.h.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "sgs/config.h"

#include "sgs/buffer.h"
#include "sgs/context.h"
#include "sgs/error_codes.h"
#include "sgs/protocol.h"
#include "sgs/private/connection_impl.h"
#include "sgs/private/session_impl.h"
#include "sgs/private/io_utils.h"

#ifndef WIN32
#include <netdb.h>
#include <sys/select.h>
#endif /* !WIN32 */

#include <fcntl.h>

#define SGS_CONNECTION_IMPL_IO_BUFSIZE SGS_MSG_MAX_LENGTH

static void conn_closed(sgs_connection_impl *connection);
static int consume_data(sgs_connection_impl *connection);

/*
 * sgs_connection_do_work()
 */
int sgs_connection_do_work(sgs_connection_impl *connection) {
    fd_set readset, writeset, exceptset;
    struct timeval timeout_tv;
    int result;
    sgs_socket_t sockfd;
    socklen_t optlen;

    if (connection->state == SGS_CONNECTION_IMPL_DISCONNECTED) {
        /** Error: should not call do_io() when disconnected. */
        errno = ENOTCONN;
        return -1;
    }

    sockfd = connection->socket_fd;
    
    FD_ZERO(&readset);
    FD_ZERO(&writeset);
    FD_ZERO(&exceptset);
    
    FD_SET(sockfd, &readset);
    FD_SET(sockfd, &writeset);
    FD_SET(sockfd, &exceptset);
    
    timeout_tv.tv_sec = 0;
    timeout_tv.tv_usec = 0;
    
    
    result = select(sockfd + 1, &readset, &writeset, &exceptset, &timeout_tv);
    if (result <= 0) {
        return result;  /** -1 or 0 */
    }
    
    if (FD_ISSET(sockfd, &exceptset)) {
        optlen = sizeof(errno);  /* SO_ERROR should return an int */
        if (sgs_socket_getsockopt(sockfd, SOL_SOCKET, SO_ERROR, &errno, &optlen) == -1) {
            return -1;
        }
        
        conn_closed(connection);
        return -1;
    }
    
    if (FD_ISSET(sockfd, &readset)) {
        /** Read stuff off the socket and write it to the in-buffer. */
        result = sgs_impl_read_from_fd(connection->inbuf, sockfd);
        if (result == -1) {
            return -1;
        }
        
        /* Return value of 0 may or may not mean that EOF was read. */
        if ((result == 0) && (sgs_buffer_remaining(connection->inbuf) > 0)) {
            conn_closed(connection);   /** The server closed the socket. */
            return 0;
        }
        
        /** Try to pull out messages from the inbuf and process them. */
        if (consume_data(connection) == -1) return -1;
    }
    
    if (FD_ISSET(sockfd, &writeset)) {
        /** If we are waiting for connect() to complete, its done now. */
        if (connection->state == SGS_CONNECTION_IMPL_CONNECTING) {
            connection->state = SGS_CONNECTION_IMPL_CONNECTED;
        }
        
        /** Read stuff out of the out-buffer and write it to the socket. */
        result = sgs_impl_write_to_fd(connection->outbuf, sockfd);
        if (result == -1) {
            return -1;
        }
    }
    
    /** If there is room in inbuf, then register interest in socket reads. */
    if (connection->state == SGS_CONNECTION_IMPL_CONNECTED) {
        if (sgs_buffer_remaining(connection->inbuf) > 0){
            if (connection->input_enabled == 0){
                connection->ctx->reg_fd_cb(connection, sockfd, POLLIN);
                connection->input_enabled = 1;
            }
        } else if (connection->input_enabled == 1){
            connection->ctx->unreg_fd_cb(connection, sockfd, POLLIN);
            connection->input_enabled = 0;
        }

        /** If there is data in outbuf, then register interest in socket writes. */
        if (sgs_buffer_size(connection->outbuf) > 0) {
            if (connection->output_enabled == 0) {
                connection->ctx->reg_fd_cb(connection, sockfd, POLLOUT);
                connection->output_enabled = 1;
            }
        } else if (connection->output_enabled == 1){
            connection->ctx->unreg_fd_cb(connection, sockfd, POLLOUT);
            connection->output_enabled = 0;
        }
    }
    return 0;
}

/*
 * sgs_connection_destroy()
 */
void sgs_connection_destroy(sgs_connection_impl *connection) {
    sgs_buffer_destroy(connection->inbuf);
    sgs_buffer_destroy(connection->outbuf);
	
	/*if this isn't part of a redirect, free up the session and context*/
	if (connection->in_redirect == 0){
		sgs_session_impl_destroy(connection->session);
		sgs_ctx_destroy(connection->ctx);
	}

    free(connection);
}

/*
 * sgs_connection_login()
 */
int sgs_connection_login(sgs_connection_impl *connection, const char *login,
    const char *password)
{
    struct hostent *server;
    struct sockaddr_in serv_addr;
  
    /** create the TCP socket (not connected yet). */
    connection->socket_fd = sgs_socket_create(AF_INET, SOCK_STREAM, 0);
    if (connection->socket_fd == SGS_SOCKET_INVALID) return -1;
 
    /** Set socket to non-blocking mode. */
    if (sgs_socket_set_nonblocking(connection->socket_fd) == -1)
        return -1;
  
    /** Resolve hostname to IP(s).  This works even if hostname *is* an IP. */
    server = gethostbyname(connection->ctx->hostname);
    if (server == NULL) {
        errno = SGS_ERR_CHECK_HERRNO;
        return -1;
    }
  
    /** Initialize server_addr to all zeroes, then fill in fields. */
    memset((char*) &serv_addr, '\0', sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    memcpy(&serv_addr.sin_addr.s_addr, server->h_addr_list[0], server->h_length);
    serv_addr.sin_port = htons(connection->ctx->port);
  
    /**
     * As a "trick", we go ahead and write the login request to the out-buffer
     * now, even though the socket may not connect instantly (since we use a
     * non-blocking call to connect()).  However that is ok, since the socket will
     * not register as writable (and thus we won't actually try to write to it)
     * until it has connected.
     */
    if (sgs_session_impl_login(connection->session, login, password) == -1)
        return -1;
  
    /**
     * Try to connect to server.  (note, ok to cast sockaddr_in* to sockaddr*
     * according to http://retran.com/beej/sockaddr_inman.html )
     */
    if (sgs_socket_connect(connection->socket_fd, (const struct sockaddr*) &serv_addr,
            sizeof(serv_addr)) == -1) {
    
        if (errno == EINPROGRESS) {
            /**
             * Connection in progress (not a real error); need to register interest in
             * writing to the socket because that's how we can tell once the socket
             * has finished connecting.
             */
            connection->ctx->reg_fd_cb(connection, connection->socket_fd,
                POLLOUT);
            connection->output_enabled = 1;

            /*
             * Under Windows we also registerinterest in error checking the socket
             * because that's how some connect errors are reported.
             */
#ifdef WIN32
            connection->ctx->reg_fd_cb(connection, connection->socket_fd,
                POLLERR);
#endif /* WIN32 */
            
            connection->state = SGS_CONNECTION_IMPL_CONNECTING;
            return 0;
        }
        else {
            /** some kind of (real) error */
            return -1;
        }
    }
  
    /**
     * else, connect() completed "instantly" - this can happen when connecting to
     *  a port on the local machine, for example.
     */
    connection->state = SGS_CONNECTION_IMPL_CONNECTED;
  
    /** Register interest in socket writes to send the login request. */
    connection->ctx->reg_fd_cb(connection, connection->socket_fd, POLLOUT);
    connection->output_enabled = 1;
    
    return 0;
}
/*
 * sgs_connection_logout()
 */
int sgs_connection_logout(sgs_connection_impl *connection, int force) {
    if (force) {
        conn_closed(connection);
    } else {
        if (connection->state == SGS_CONNECTION_IMPL_DISCONNECTED) {
            errno = ENOTCONN;
            return -1;
        }

        connection->expecting_disconnect = 1;
        if (connection->in_redirect)
            return 0;
        sgs_session_impl_logout(connection->session);
    }

    return 0;
}

/*
 * sgs_connection_create()
 */
sgs_connection_impl *sgs_connection_create(sgs_context *ctx) {
    sgs_connection_impl *connection;
    
    connection = malloc(sizeof(struct sgs_connection_impl));
    if (connection == NULL) return NULL;

    connection->expecting_disconnect = 0;
    connection->in_redirect = 0;
    connection->state = SGS_CONNECTION_IMPL_DISCONNECTED;
    connection->ctx = ctx;
    connection->session = sgs_session_impl_create(connection);
    connection->inbuf = sgs_buffer_create(SGS_CONNECTION_IMPL_IO_BUFSIZE);
    connection->outbuf = sgs_buffer_create(SGS_CONNECTION_IMPL_IO_BUFSIZE);
    
    /** Check if any create() calls failed. */
    if (connection->session == NULL
        || connection->inbuf == NULL
        || connection->outbuf == NULL)
    {
        /** Allocation of at least one object failed. */
        sgs_connection_destroy(connection);
        return NULL;
    }
    connection->input_enabled = connection->output_enabled = 0;
  
    return connection;
}

/*
 *  sgs_connection_get_context
 */
sgs_context* sgs_connection_get_context(sgs_connection_impl *connection){
    return connection->ctx;
}

/*
 * sgs_connection_get_session
 */
sgs_session* sgs_connection_get_session(sgs_connection_impl* connection){
    return connection->session;
}
/*
 * PRIVATE IMPL FUNCTIONS
 */
/*
 * sgs_connection_impl_disconnect()
 */
void sgs_connection_impl_disconnect(sgs_connection_impl *connection) {
    /** Unregister interest in this socket */
    connection->ctx->unreg_fd_cb(connection, connection->socket_fd,
            POLLIN | POLLOUT | POLLERR);
    connection->input_enabled = connection->output_enabled = 0;

    sgs_socket_destroy(connection->socket_fd);
    connection->expecting_disconnect = 0;
    sgs_buffer_clear(connection->inbuf);
    sgs_buffer_clear(connection->outbuf);
    connection->state = SGS_CONNECTION_IMPL_DISCONNECTED;
    sgs_session_channel_clear(connection->session);
    if (connection->in_redirect == 1)
        sgs_connection_destroy(connection);
}


/*
 * sgs_connection_impl_io_write()
 */
int sgs_connection_impl_io_write(sgs_connection_impl *connection, uint8_t *buf,
    size_t buflen)
{
    if (buflen == 0) return 0;
    if (sgs_buffer_write(connection->outbuf, buf, buflen) == -1) return -1;
  
    /**
     * Make sure that we have registered interest in writing to the socket
     * (unless we have not yet connected).
     */
    if ((connection->state == SGS_CONNECTION_IMPL_CONNECTED) &&
            (connection->output_enabled == 0)) {
        connection->ctx->reg_fd_cb(connection, connection->socket_fd, POLLOUT);
        connection->output_enabled = 1;
    }
  
    return 0;
}


/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */
/*
 * function: conn_closed()
 *
 * Called whenever the connection is closed by the server.
 */
static void conn_closed(sgs_connection_impl *connection) {
    /**if this is part of a redirect sequence, do nothing*/
    if (connection->in_redirect == 1)
        return;

    sgs_connection_impl_disconnect(connection);

    if (connection->ctx->disconnected_cb != NULL)
        connection->ctx->disconnected_cb(connection);
}

/*
 * function: consume_data()
 *
 * Reads (and removes) chunks of data from the connection's in-buffer that
 * comprise complete messages and passes them on for processing.  Stops once the
 * data remaining in the in-buffer comprises only part of a message (or there is
 * no data remaining).
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int consume_data(sgs_connection_impl *connection) {
    uint8_t *msgbuf = connection->session->msg_buf;
    uint32_t len;
  
    while (sgs_buffer_peek(connection->inbuf, (uint8_t*)&len, SGS_MSG_LENGTH_OFFSET) != -1) {
        len = ntohs(len);
        if (sgs_buffer_read(connection->inbuf, msgbuf, len + SGS_MSG_LENGTH_OFFSET) == -1)
            break;  /* not enough data in buffer (not a complete message) */
    
        if (sgs_session_impl_recv_msg(connection->session) == -1) return -1;
    }
  
    return 0;
}
