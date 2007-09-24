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
#include <poll.h>
#include "sgs/private/channel_impl.h"
#include "sgs/error_codes.h"
#include "sgs/id.h"
#include "sgs/private/map.h"
#include "sgs/message.h"
#include "sgs/private/session_impl.h"

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static void increment_seqnum(sgs_session_impl *session);
static uint16_t read_len_header(const uint8_t *data);


/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_SESSION.H
 */

/*
 * sgs_session_direct_send()
 */
int sgs_session_direct_send(sgs_session_impl *session, const uint8_t *data,
    size_t datalen)
{
    sgs_message* msg =
        sgs_msg_create(session->msg_buf, sizeof(session->msg_buf),
            SGS_OPCODE_SESSION_MESSAGE, SGS_APPLICATION_SERVICE);
  
    if (msg == NULL)
        return -1;
  
    /** Add sequence number to message. */
    if (sgs_msg_add_uint32(msg, session->seqnum_hi) == -1) return -1;
    if (sgs_msg_add_uint32(msg, session->seqnum_lo) == -1) return -1;
  
    /** Add message payload to message. */
    if (sgs_msg_add_fixed_content(msg, data, datalen) == -1) return -1;
  
    /** Done assembling message; send message buffer to connection to be sent. */
    if (sgs_connection_impl_io_write(session->connection, session->msg_buf,
            sgs_msg_get_size(msg)) == -1)
        return -1;
  
    /** Only update sequence number if message is (appears) successfully sent */
    increment_seqnum(session);
  
    return 0;
}

/*
 * sgs_session_get_reconnectkey()
 */
const sgs_id *sgs_session_get_reconnectkey(const sgs_session_impl *session) {
    return session->reconnect_key;
}

/*
 * sgs_session_get_id()
 */
const sgs_id *sgs_session_get_id(const sgs_session_impl *session) {
    return session->session_id;
}


/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_SESSION_IMPL.H
 */

/*
 * sgs_session_impl_destroy()
 */
void sgs_session_impl_destroy(sgs_session_impl *session) {
    sgs_id_destroy(session->session_id);
    sgs_id_destroy(session->reconnect_key);
    free(session);
}

/*
 * sgs_session_impl_login()
 */
int sgs_session_impl_login(sgs_session_impl *session, const char *login,
    const char *password)
{
    sgs_message* msg =
        sgs_msg_create(session->msg_buf, sizeof(session->msg_buf),
            SGS_OPCODE_LOGIN_REQUEST, SGS_APPLICATION_SERVICE);
  
    if (msg == NULL)
        return -1;
  
    /** Add "login" data field to message. */
    if (sgs_msg_add_fixed_content(msg, (uint8_t*)login, strlen(login)) == -1)
        return -1;
  
    /** Add "password" data field to message. */
    if (sgs_msg_add_fixed_content(msg, (uint8_t*)password,
            strlen(password)) == -1)
        return -1;
  
    /** Done assembling message; send message buffer to connection to be sent. */
    if (sgs_connection_impl_io_write(session->connection, session->msg_buf,
            sgs_msg_get_size(msg)) == -1)
        return -1;
  
    return 0;
}

/*
 * sgs_session_impl_logout()
 */
int sgs_session_impl_logout(sgs_session_impl *session) {
    sgs_message* msg =
        sgs_msg_create(session->msg_buf, sizeof(session->msg_buf),
            SGS_OPCODE_LOGOUT_REQUEST, SGS_APPLICATION_SERVICE);
  
    if (msg == NULL)
        return -1;
  
    // Done assembling message; send message buffer to connection to be sent.
    if (sgs_connection_impl_io_write(session->connection, session->msg_buf,
            sgs_msg_get_size(msg)) == -1)
        return -1;
  
    return 0;
}

/*
 * sgs_session_impl_create()
 */
sgs_session_impl *sgs_session_impl_create(sgs_connection_impl *connection) {
    sgs_session_impl *session;
    
    session = malloc(sizeof(struct sgs_session_impl));
    if (session == NULL) return NULL;
    
    /**
     * The map key (a channel ID) is just a pointer into the map value (an
     * sgs_channel struct), so we don't need an explicit deallocation function
     * for the key, just the value.  Also note that we have to cast our function
     * pointers to make them look like they take void* arguments instead of
     * their usual argument types.
     */
    session->channels =
        sgs_map_create((int(*)(const void*, const void*))sgs_id_compare,
            NULL, (void (*)(void *))sgs_channel_impl_destroy);
    
    if (session->channels == NULL) {
        /** roll back allocation of session */
        free(session);
        return NULL;
    }
    
    session->connection = connection;
    session->session_id = sgs_id_create(NULL, 0, NULL);
    session->reconnect_key = sgs_id_create(NULL, 0, NULL);
    session->seqnum_hi = 0;
    session->seqnum_lo = 0;

    if (session->session_id == NULL
        || session->reconnect_key == NULL)
    {
        sgs_session_impl_destroy(session);
        return NULL;
    }
    
    return session;
}

/*
 * sgs_session_impl_recv_msg()
 */
int sgs_session_impl_recv_msg(sgs_session_impl *session) {
    int8_t result;
    ssize_t namelen, offset;
    sgs_id* channel_id;
    sgs_id* sender_id;
    sgs_message* msg;
    const uint8_t* msg_data;
    sgs_channel* channel;
    size_t msg_datalen;
    
    msg = sgs_msg_deserialize(session->msg_buf, sizeof(session->msg_buf));
    if (msg == NULL)
        return -1;

    //sgs_msg_dump(msg);
    
    msg_datalen = sgs_msg_get_datalen(msg);
    msg_data = sgs_msg_get_data(msg);
    
    if (sgs_msg_get_version(msg) != SGS_MSG_VERSION) {
        errno = SGS_ERR_BAD_MSG_VERSION;
        return -1;
    }
    
    if (sgs_msg_get_service(msg) == SGS_APPLICATION_SERVICE) {
        switch (sgs_msg_get_opcode(msg)) {
        case SGS_OPCODE_LOGIN_SUCCESS:
            /** field 1: session-id (compact-id format) */
            session->session_id =
                sgs_id_create(msg_data, msg_datalen, &offset);
            if (session->session_id == NULL) return -1;
            
            /** field 2: reconnection-key (compact-id format) */
            session->reconnect_key =
                sgs_id_create(msg_data + offset, msg_datalen - offset, NULL);
            if (session->reconnect_key == NULL) return -1;
            
            if (session->connection->ctx->logged_in_cb != NULL)
                session->connection->ctx->logged_in_cb(session->connection,
                    session);
            
            return 0;
            
        case SGS_OPCODE_LOGIN_FAILURE:
            /** field 1: error string (first 2 bytes = length of string) */
            if (session->connection->ctx->login_failed_cb != NULL)
                session->connection->ctx->login_failed_cb(session->connection,
                    msg_data + 2, read_len_header(msg_data));
      
            return 0;
      
        case SGS_OPCODE_SESSION_MESSAGE:
            /**
             * field 1: sequence number (8 bytes)
             * TODO first 8 bytes are a sequence number that is currently
             * ignored
             */
            offset = 8;
      
            /** field 2: message (first 2 bytes = length of message) */
            if (session->connection->ctx->recv_message_cb != NULL)
                session->connection->ctx->recv_message_cb(session->connection,
                    msg_data + offset + 2, read_len_header(msg_data + offset));
      
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
            return 0;
      
        default:
            errno = SGS_ERR_BAD_MSG_OPCODE;
            return -1;
        }
    }
    else if (sgs_msg_get_service(msg) == SGS_CHANNEL_SERVICE) {
        switch (sgs_msg_get_opcode(msg)) {
        case SGS_OPCODE_CHANNEL_JOIN:
            /** field 1: channel name (first 2 bytes = length of string) */
            namelen = read_len_header(msg_data);
            
            /** field 2: channel-id (compact-id format) */
            channel_id =
                sgs_id_create(msg_data + namelen + 2,
                    msg_datalen - namelen - 2, NULL);
            if (channel_id == NULL) return -1;
            
            channel = sgs_channel_impl_create(session, channel_id,
                (const char*)(msg_data + 2), namelen);
            
            if (channel == NULL) return -1;
            
            result = sgs_map_put(session->channels,
                sgs_channel_impl_get_id(channel), channel);
            
            if (result == -1) return -1;
            
            if (session->connection->ctx->channel_joined_cb != NULL)
                session->connection->ctx->channel_joined_cb(session->connection,
                    channel);
            
            return 0;
      
        case SGS_OPCODE_CHANNEL_LEAVE:
            /** field 1: channel-id (compact-id format) */
            channel_id = sgs_id_create(msg_data, msg_datalen, NULL);
            if (channel_id == NULL) return -1;
            
            channel = sgs_map_get(session->channels, channel_id);
            if (channel == NULL) {
                errno = SGS_ERR_UNKNOWN_CHANNEL;
                return -1;
            }
 
            if (session->connection->ctx->channel_left_cb != NULL)
                session->connection->ctx->channel_left_cb(session->connection,
                    channel);
            
            sgs_map_remove(session->channels, channel_id);
            
            return 0;
            
        case SGS_OPCODE_CHANNEL_MESSAGE:
            /** field 1: channel-id (compact-id format) */
            channel_id = sgs_id_create(msg_data, msg_datalen, &offset);
            if (channel_id == NULL) return -1;

            /**
             * field 2: sequence number (8 bytes)
             * TODO next 8 bytes are a sequence number that is currently ignored
             */
            offset += 8;

            ssize_t offset2;
            
            /** field 3: session-id of sender (compact-id format) */
            sender_id = sgs_id_create(msg_data + offset, msg_datalen - offset,
                &offset2);
            if (sender_id == NULL) return -1;
                
            offset += offset2;
            
            channel = sgs_map_get(session->channels, channel_id);
            if (channel == NULL) {
                errno = SGS_ERR_UNKNOWN_CHANNEL;
                return -1;
            }
            
            if (session->connection->ctx->channel_recv_msg_cb != NULL) {
                sender_id = sgs_id_is_server(sender_id) ? NULL : sender_id;
                
                /** field 4: message (first 2 bytes = length of message) */
                session->connection->ctx->channel_recv_msg_cb(session->connection,
                    channel, sender_id, msg_data + offset + 2,
                    read_len_header(msg_data + offset));
            }
            
            return 0;
      
        default:
            errno = SGS_ERR_BAD_MSG_OPCODE;
            return -1;
        }
    }
    else {
        errno = SGS_ERR_BAD_MSG_SERVICE;
        return -1;
    }
}

int sgs_session_impl_send_msg(sgs_session_impl *session) {
    sgs_message* msg = sgs_msg_deserialize(session->msg_buf,
            sizeof(session->msg_buf));
    
    if (msg == NULL)
        return -1;

    size_t msg_size = sgs_msg_get_size(msg);
    sgs_msg_destroy(msg);
    
    /** Send message buffer to connection to be sent. */
    if (sgs_connection_impl_io_write(session->connection, session->msg_buf,
            msg_size) == -1)
        return -1;
  
    /** Only update sequence number if message is (appears) successfully sent */
    increment_seqnum(session);
  
    return 0;
}


/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */

/*
 * function: increment_seqnum()
 *
 * Increments the session's internal sequence number.
 */
static void increment_seqnum(sgs_session_impl *session) {
    if (session->seqnum_lo == UINT32_MAX) {
        session->seqnum_hi++;
        session->seqnum_lo = 0;
    } else {
        session->seqnum_lo++;
    }
}

/*
 * function: read_len_header()
 *
 * Reads two bytes from data argument and interprets them as a 2-byte integer
 * field, returning the result.
 */
static uint16_t read_len_header(const uint8_t *data) {
    uint16_t tmp;
    tmp = data[0];
    tmp = tmp*256 + data[1];
    return tmp;
}
