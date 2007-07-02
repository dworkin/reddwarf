/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
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

#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <poll.h>
#include <string.h>
#include <unistd.h>
#include "sgs_connection_impl.h"
#include "sgs_context.h"
#include "sgs_error_codes.h"

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static void conn_closed(sgs_connection_impl *connection);
static int consume_data(sgs_connection_impl *connection);

/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_CONNECTION.H
 */

/*
 * sgs_connection_create()
 */
sgs_connection_impl *sgs_connection_create(sgs_context *ctx)
{
  sgs_connection_impl *connection;
  
  connection = (sgs_connection_impl*)malloc(sizeof(struct sgs_connection_impl));
  if (connection == NULL) return NULL;
  
  connection->socket_fd = -1;
  connection->expecting_disconnect = 0;
  connection->state = SGS_CONNECTION_IMPL_DISCONNECTED;
  connection->ctx = ctx;
  connection->session = sgs_session_impl_create(connection);
  connection->inbuf = sgs_buffer_create(SGS_CONNECTION_IMPL_IO_BUFSIZE);
  connection->outbuf = sgs_buffer_create(SGS_CONNECTION_IMPL_IO_BUFSIZE);
  
  /** Check if any create() calls failed. */
  if (connection->session == NULL || connection->inbuf == NULL ||
      connection->outbuf == NULL) {
    
    /** Allocation of at least one object failed. */
    if (connection->session != NULL)
      sgs_session_impl_destroy(connection->session);
    if (connection->inbuf != NULL) sgs_buffer_destroy(connection->inbuf);
    if (connection->outbuf != NULL) sgs_buffer_destroy(connection->outbuf);
    free(connection);
    return NULL;
  }
  
  return connection;
}

/*
 * sgs_connection_destroy()
 */
void sgs_connection_destroy(sgs_connection_impl *connection) {
  sgs_session_impl_destroy(connection->session);
  sgs_buffer_destroy(connection->inbuf);
  sgs_buffer_destroy(connection->outbuf);
  free(connection);
}

/*
 * sgs_connection_do_io()
 */
int sgs_connection_do_io(sgs_connection_impl *connection, int fd, short events) {
  int result;
  socklen_t optlen;
  
  if (connection->state == SGS_CONNECTION_IMPL_DISCONNECTED) {
    /** Error: should not call do_io() when disconnected. */
    errno = ENOTCONN;
    return -1;
  }
  
  if (fd != connection->socket_fd) {
    /** Error: this FD was never registered by this connection. */
    errno = SGS_ERR_BAD_FD;
    return -1;
  }
  
  if ((events & POLLIN) == POLLIN) {
    /** Read stuff off the socket and write it to the in-buffer. */
    result = sgs_buffer_read_from_fd(connection->inbuf, connection->socket_fd);
    if (result == -1) return -1;
    
    if (result == 0) {
      conn_closed(connection);   /** The server closed the socket. */
      return 0;
    }
    
    /** Try to pull out messages from the inbuf and process them. */
    if (consume_data(connection) == -1) return -1;
  }
  
  if ((events & POLLOUT) == POLLOUT) {
    /** If we are waiting for connect() to complete, its done now. */
    if (connection->state == SGS_CONNECTION_IMPL_CONNECTING) {
      connection->state = SGS_CONNECTION_IMPL_CONNECTED;
    }
    
    /** Read stuff out of the out-buffer and write it to the socket. */
    result = sgs_buffer_write_to_fd(connection->outbuf, connection->socket_fd);
    if (result == -1) return -1;
  }
  
  if ((events & POLLERR) == POLLERR) {
    optlen = sizeof(errno);  /* SO_ERROR should return an int */
    if (getsockopt(connection->socket_fd, SOL_SOCKET, SO_ERROR,
                   &errno, &optlen) == -1) {
      return -1;
    }
    
    conn_closed(connection);
    return -1;
  }
  
  /** If there is room in inbuf, then register interest in socket reads. */
  if (sgs_buffer_remaining_capacity(connection->inbuf) > 0)
    connection->ctx->reg_fd_cb(connection, &connection->socket_fd, 1, POLLIN);
  else
    connection->ctx->unreg_fd_cb(connection, &connection->socket_fd, 1, POLLIN);
  
  /** If there is data in outbuf, then register interest in socket writes. */
  if (sgs_buffer_size(connection->outbuf) > 0)
    connection->ctx->reg_fd_cb(connection, &connection->socket_fd, 1, POLLOUT);
  else
    connection->ctx->unreg_fd_cb(connection, &connection->socket_fd, 1, POLLOUT);
  
  return 0;
}

/*
 * sgs_connection_login()
 */
int sgs_connection_login(sgs_connection_impl *connection, const char *login,
                         const char *password)
{
  int ioflags;
  struct hostent *server;
  struct sockaddr_in serv_addr;
  
  /** create the TCP socket (not connected yet). */
  connection->socket_fd = socket(AF_INET, SOCK_STREAM, 0);
  if (connection->socket_fd == -1) return -1;
  
  /** Set socket to non-blocking mode. */
  if ((ioflags = fcntl(connection->socket_fd, F_GETFL, 0)) == -1) return -1;
  if (fcntl(connection->socket_fd, F_SETFL, ioflags | O_NONBLOCK) == -1)
    return -1;
  
  /** Resolve hostname to IP(s). */
  /** TODO - does this work if hostname *IS* an IP? */
  server = gethostbyname(connection->ctx->hostname);
  if (server == NULL) {
    errno = SGS_ERR_CHECK_HERRNO;
    return -1;
  }
  
  /** Initialize server_addr to all zeroes, then fill in fields. */
  memset((char*) &serv_addr, '\0', sizeof(serv_addr));
  serv_addr.sin_family = AF_INET;
  memcpy(&serv_addr.sin_addr.s_addr, server->h_addr, server->h_length);
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
  if (connect(connection->socket_fd, (const struct sockaddr*) &serv_addr,
              sizeof(serv_addr)) != 0) {
    
    if (errno == EINPROGRESS) {
      /**
       * Connection in progress (not a real error); need to register interest in
       * writing to the socket because that's how we can tell once the socket
       * has finished connecting.
       */
      connection->ctx->reg_fd_cb(connection, &connection->socket_fd, 1, POLLOUT);
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
  connection->ctx->reg_fd_cb(connection, &connection->socket_fd, 1, POLLOUT);
  
  return 0;
}

/*
 * sgs_connection_logout()
 */
int sgs_connection_logout(sgs_connection_impl *connection, const int force) {
  if (force) {
    sgs_connection_impl_disconnect(connection);
  }
  else {
    connection->expecting_disconnect = 1;
    sgs_session_impl_logout(connection->session);
  }
  
  return 0;
}


/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_CONNECTION_IMPL.H
 */

/*
 * sgs_connection_impl_disconnect()
 */
void sgs_connection_impl_disconnect(sgs_connection_impl *connection) {
  /** Unregister interest in this socket.  (events = 0 means "all") */
  connection->ctx->unreg_fd_cb(connection, &connection->socket_fd, 1, 0);
  
  close(connection->socket_fd);
  connection->socket_fd = -1;
  connection->expecting_disconnect = 0;
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
  if (connection->state == SGS_CONNECTION_IMPL_CONNECTED) {
    connection->ctx->reg_fd_cb(connection, &connection->socket_fd, 1, POLLOUT);
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
  if (connection->expecting_disconnect) {
    /** Expected close of connection... */
    sgs_connection_impl_disconnect(connection);
    connection->expecting_disconnect = 0;
  } else {
    /** Unexpected close of connection... */
    sgs_connection_impl_disconnect(connection);
    
    if (connection->ctx->disconnected_cb != NULL)
      connection->ctx->disconnected_cb(connection);
  }
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
  
  while (sgs_buffer_peek(connection->inbuf, (uint8_t*)&len, 4) != -1) {
    len = ntohl(len);
    if (sgs_buffer_read(connection->inbuf, msgbuf, len + 4) == -1)
      break;  /* not enough data in buffer (not a complete message) */
    
    if (sgs_session_impl_recv_msg(connection->session) == -1) return -1;
  }
  
  return 0;
}
