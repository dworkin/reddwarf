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
 * This file provides functions relating to the client's side of a session with
 *  a Sun Gaming Server (SGS).  Its functionality is similar to that in the java
 *  class com.sun.sgs.client.simple.SimpleClient.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "sgs/config.h"
#include "sgs/channel.h"
#include "sgs/error_codes.h"
#include "sgs/id.h"
#include "sgs/map.h"
#include "sgs/protocol.h"
#include "sgs/private/session_impl.h"
#include "sgs/private/connection_impl.h"
#include "sgs/private/context_impl.h"
#include "sgs/private/channel_impl.h"
#include "sgs/private/message.h"


/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_SESSION.H
 */

/*
 * sgs_session_direct_send()
 */
int sgs_session_direct_send(sgs_session_impl *session, const uint8_t *data,
        size_t datalen) {
    sgs_message msg;

    if (sgs_msg_init(&msg, session->msg_buf, sizeof (session->msg_buf),
            SGS_OPCODE_SESSION_MESSAGE) == -1)
        return -1;

    /** Add message payload to message. */
    if (sgs_msg_add_arb_content(&msg, data, datalen) == -1) return -1;

    /** Done assembling message; send message buffer to connection to be sent. */
    if (sgs_connection_impl_io_write(session->connection, session->msg_buf,
            sgs_msg_get_size(&msg)) == -1)
        return -1;

    return 0;
}

/*
 * sgs_session_get_reconnectkey()
 */
const sgs_id *sgs_session_get_reconnectkey(const sgs_session_impl *session) {
    return session->reconnect_key;
}

/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_SESSION_IMPL.H
 */

/*
 * sgs_session_impl_destroy()
 */
void sgs_session_impl_destroy(sgs_session_impl *session) {
    sgs_id_destroy(session->reconnect_key);
    sgs_map_destroy(session->channels);
    free(session->login);
    free(session->password);
    free(session);
}

/*
 * sgs_session_impl_login()
 */
int sgs_session_impl_login(sgs_session_impl *session, const char *login,
        const char *password) {
    sgs_message msg;
    uint8_t protocolVersion = SGS_MSG_VERSION;

    if (sgs_msg_init(&msg, session->msg_buf, sizeof (session->msg_buf),
            SGS_OPCODE_LOGIN_REQUEST) == -1)
        return -1;

    /** Add protocol version field to message */
    if (sgs_msg_add_arb_content(&msg, &protocolVersion, 1) == -1)
        return -1;

    /** Add "login" string field to message. */
    if (sgs_msg_add_string(&msg, login) == -1)
        return -1;

    /** Add "password" string to message. */
    if (sgs_msg_add_string(&msg, password) == -1)
        return -1;

    /**store the "login" and "password" fields in case there is a reconnect*/
    if ((session->login = malloc(strlen(login))) == NULL)
        return -1;
    memcpy(session->login, login, strlen(login));

    if ((session->password = malloc(strlen(password))) == NULL) {
        free(session->login);
        return -1;
    }
    memcpy(session->password, password, strlen(password));

    /** Done assembling message; send message buffer to connection to be sent. */
    if (sgs_connection_impl_io_write(session->connection, session->msg_buf,
            sgs_msg_get_size(&msg)) == -1)
        return -1;

    return 0;
}

/*
 * sgs_session_impl_logout()
 */
int sgs_session_impl_logout(sgs_session_impl *session) {
    sgs_message msg;

    if (sgs_msg_init(&msg, session->msg_buf, sizeof (session->msg_buf),
            SGS_OPCODE_LOGOUT_REQUEST) == -1)
        return -1;

    // Done assembling message; send message buffer to connection to be sent.
    if (sgs_connection_impl_io_write(session->connection, session->msg_buf,
            sgs_msg_get_size(&msg)) == -1)
        return -1;

    return 0;
}

/*
 * sgs_session_impl_create()
 */
sgs_session_impl *sgs_session_impl_create(sgs_connection_impl *connection) {
    sgs_session_impl *session;

    session = malloc(sizeof (struct sgs_session_impl));
    if (session == NULL) return NULL;

    /**
     * The map key (a channel ID) is just a pointer into the map value (an
     * sgs_channel struct), so we don't need an explicit deallocation function
     * for the key, just the value.  Also note that we have to cast our function
     * pointers to make them look like they take void* arguments instead of
     * their usual argument types.
     */
    session->channels =
            sgs_map_create((int(*)(const void*, const void*))sgs_id_compare);

    if (session->channels == NULL) {
        /** roll back allocation of session */
        free(session);
        return NULL;
    }

    session->connection = connection;
    session->reconnect_key = NULL;

    return session;
}

/*
 * sgs_session_impl_recv_msg()
 */
int sgs_session_impl_recv_msg(sgs_session_impl *session) {
    int8_t result;
    ssize_t readlen, offset;
    sgs_id* channel_id;
    sgs_message msg;
    const uint8_t* msg_data;
    sgs_channel* channel;
    size_t msg_datalen;
    sgs_connection_impl *old_connection;
    char *namebuffer;

    if (sgs_msg_deserialize(&msg, session->msg_buf,
            sizeof (session->msg_buf)) == -1)
        return -1;

    //sgs_msg_dump(&msg);

    /** Get the length of the payload, and a pointer to the beginning of the
     *  payload. The payload includes the opcode, so we will have to set an
     *  offset to insure that we pass in the beginning of the part of the
     *  buffer that we need read to the appropriate functions
     */
    msg_datalen = sgs_msg_get_datalen(&msg);
    msg_data = sgs_msg_get_data(&msg);

    /* set the offset to account for the opcode, which is the first byte
     * of the payload
     */
    offset = 1;

    switch (sgs_msg_get_opcode(&msg)) {
        case SGS_OPCODE_LOGIN_SUCCESS:
            /** reconnection-key; the key is a byte[] and, since it is the only
             *  payload, the length of the array is the same as the payload length
             */
            readlen = sgs_msg_read_id(&msg,offset + 2, 0,
                    &session->reconnect_key);
            if (readlen < 0) return -1;

            if (session->connection->ctx->logged_in_cb != NULL)
                session->connection->ctx->logged_in_cb(session->connection,
                    session);

            return 0;

        case SGS_OPCODE_LOGIN_FAILURE:
            /* Disconnect the current connection; this needs to be done
             * prior to calling the login_failed_cb in case that callback
             * tries to login again */
            old_connection = session->connection;
            sgs_connection_impl_disconnect(old_connection);

            /** error string (first 2 bytes = length of string) */
            if (session->connection->ctx->login_failed_cb != NULL)
                session->connection->ctx->login_failed_cb(session->connection,
                    msg_data + offset, msg_datalen - offset);
            return 0;

        case SGS_OPCODE_LOGIN_REDIRECT:
            /** start by getting the redirect host and port*/
            /** adjust the offset, since we will be using the message
             *  buffer rather than the msg_data */
            offset += 2;
            readlen = sgs_msg_read_string(&msg, offset,
                    &(session->connection->ctx->hostname));
            if (readlen < 0)
                return -1;
            else offset += readlen;

            readlen = sgs_msg_read_uint32(&msg, offset,
                    &(session->connection->ctx->port));
            if (readlen < 0)
                return -1;

            old_connection = session->connection;
            old_connection->in_redirect = 1;

            session->connection = sgs_connection_create(old_connection->ctx);
            sgs_connection_impl_disconnect(old_connection);

            /**finally, try to login on this connection, using the login information
             * that was stored on the initial login attempt
             */
            return sgs_connection_login(session->connection, session->login, session->password);

        case SGS_OPCODE_SESSION_MESSAGE:

            /** field 1: message (first 2 bytes = length of message) */
            if (session->connection->ctx->recv_message_cb != NULL)
                session->connection->ctx->recv_message_cb(session->connection,
                    msg_data + offset, msg_datalen - offset);

            return 0;

        case SGS_OPCODE_RECONNECT_SUCCESS:
            if (session->connection->ctx->reconnected_cb != NULL)
                session->connection->ctx->reconnected_cb(session->connection);

            return 0;

        case SGS_OPCODE_RECONNECT_FAILURE:
            sgs_connection_impl_disconnect(session->connection);
            return 0;

        case SGS_OPCODE_LOGOUT_SUCCESS:
            session->connection->expecting_disconnect = 1;
            /* Since the other connection data structures could be
             * reused on a new login, we will only shut down the
             * socket from which we disconnected */
            old_connection = session->connection;
            sgs_connection_impl_disconnect(old_connection);
            return 0;

        case SGS_OPCODE_CHANNEL_JOIN:
            /*first, get the channel name; bump the offset since we
             * are using the msg buffer rather than the data_buf
             */
            offset += 2;
            readlen = sgs_msg_read_string(&msg, offset, &namebuffer);
            if (readlen < 0)
                return -1;
            else offset += readlen;

            /* Now, get the channel id*/
            readlen = sgs_msg_read_id(&msg, offset, 0, &channel_id);
            if (readlen < 0)
                return -1;

            if (sgs_map_contains(session->channels, channel_id)) {
                errno = SGS_ERR_ILLEGAL_STATE;
                sgs_id_destroy(channel_id);
                return -1;
            }

            channel = sgs_channel_impl_create(session, channel_id,
                    namebuffer, strlen(namebuffer));

            if (channel == NULL) {
                sgs_id_destroy(channel_id);
                return -1;
            }

            result = sgs_map_put(session->channels, channel_id, channel);

            if (result == -1) {
                sgs_id_destroy(channel_id);
                return -1;
            }

            if (session->connection->ctx->channel_joined_cb != NULL)
                session->connection->ctx->channel_joined_cb(session->connection,
                    channel);

            return 0;

        case SGS_OPCODE_CHANNEL_LEAVE:
            /** field 1: channel-id */
            offset +=2;
            readlen = sgs_msg_read_id(&msg, offset, 0, &channel_id);
            if (channel_id == NULL) return -1;

            channel = sgs_map_get(session->channels, channel_id);
            sgs_id_destroy(channel_id);

            if (channel == NULL) {
                errno = SGS_ERR_UNKNOWN_CHANNEL;
                return -1;
            }

            if (session->connection->ctx->channel_left_cb != NULL)
                session->connection->ctx->channel_left_cb(session->connection,
                    channel);

            sgs_map_remove(session->channels,
                    sgs_channel_impl_get_id(channel));

            sgs_channel_impl_destroy(channel);

            return 0;

        case SGS_OPCODE_CHANNEL_MESSAGE:
            /** field 1: channel-id
             *  The first two bytes are the length of the byte array containing
             *  the ID, and the remainder is the id itself
             */
            readlen = sgs_msg_read_id(&msg, offset + 2, 1, &channel_id);
            if (readlen < 0)
                return -1;
            else offset += readlen;

            channel = sgs_map_get(session->channels, channel_id);
            sgs_id_destroy(channel_id);

            if (channel == NULL) {
                errno = SGS_ERR_UNKNOWN_CHANNEL;
                return -1;
            }

            if (session->connection->ctx->channel_recv_msg_cb != NULL) {
                /** field 2: message, which is from the end of the id to the end of the payload*/
                session->connection->ctx->channel_recv_msg_cb(session->connection,
                        channel,
                        msg_data + offset,
                        msg_datalen - offset);
            }

            return 0;

        default:
            errno = SGS_ERR_BAD_MSG_OPCODE;
            return -1;
    }
}

int sgs_session_impl_send_msg(sgs_session_impl *session) {
    size_t msg_size;
    sgs_message msg;

    if (sgs_msg_deserialize(&msg, session->msg_buf,
            sizeof (session->msg_buf)) == -1)
        return -1;

    msg_size = sgs_msg_get_size(&msg);

    /** Send message buffer to connection to be sent. */
    if (sgs_connection_impl_io_write(session->connection, session->msg_buf,
            msg_size) == -1)
        return -1;

    return 0;
}

void sgs_session_channel_clear (sgs_session_impl *session){
    sgs_map_clear(session->channels);
}
