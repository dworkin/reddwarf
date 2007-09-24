/*
 * This file provides implementations of functions relating to sgs_channels.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "sgs/config.h"
#include "sgs/error_codes.h"
#include "sgs/id.h"
#include "sgs/message.h"
#include "sgs/private/channel_impl.h"
#include "sgs/private/session_impl.h"

#include <arpa/inet.h>
#include <wchar.h>

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static int send_msg_general(sgs_channel_impl *channel, const uint8_t *data,
    size_t datalen, const sgs_id *recipients[], size_t recipslen);

/*
 * sgs_channel_name()
 */
const wchar_t* sgs_channel_name(const sgs_channel_impl *channel) {
    return channel->name;
}

/*
 * sgs_session_channel_send_all()
 */
int sgs_channel_send_all(sgs_channel_impl *channel, const uint8_t *data,
    size_t datalen)
{
    return send_msg_general(channel, data, datalen, (const sgs_id**)NULL, 0);
}

/*
 * sgs_session_channel_send_multi()
 */
int sgs_channel_send_multi(sgs_channel_impl *channel, const uint8_t *data,
    size_t datalen, const sgs_id *recipients[], size_t recipslen)
{
    return send_msg_general(channel, data, datalen, recipients,
        recipslen);
}

/*
 * sgs_session_channel_send_one()
 */
int sgs_channel_send_one(sgs_channel_impl *channel, const uint8_t *data,
    size_t datalen, const sgs_id *recipient)
{
    const sgs_id *recipients[] = { recipient };
    
    return send_msg_general(channel, data, datalen, recipients, 1);
}


/*
 * PRIVATE IMPL FUNCTIONS
 */


/*
 * sgs_channel_impl_destroy()
 */
void sgs_channel_impl_destroy(sgs_channel_impl *channel) {
    sgs_id_destroy(channel->id);
    free(channel->name);
    free(channel);
}

/*
 * sgs_channel_impl_get_id()
 */
sgs_id *sgs_channel_impl_get_id(sgs_channel_impl *channel) {
    return channel->id;
}

/*
 * sgs_channel_impl_create()
 */
sgs_channel_impl* sgs_channel_impl_create(sgs_session_impl *session,
    const sgs_id* id, const char* namebytes, size_t namelen)
{
    sgs_channel_impl *channel;
    channel = malloc(sizeof(struct sgs_channel_impl));
    if (channel == NULL)
        return NULL;

    channel->session = session;
    channel->id = sgs_id_duplicate(id);
    channel->name = malloc(sizeof(wchar_t) * (namelen + 1));
    
    if (channel->id == NULL ||
        channel->name == NULL)
    {
        sgs_channel_impl_destroy(channel);
        return NULL;
    }
    
    mbstowcs(channel->name, namebytes, namelen);
    channel->name[namelen] = '\0';
    
    return channel;
}


/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */

/*
 * function: send_msg()
 *
 * Acts as a common implementation for all of sgs_channel_send_all(),
 * sgs_channel_send_one(), and sgs_channel_send_multi().
 */
static int send_msg_general(sgs_channel_impl *channel, const uint8_t *data,
    size_t datalen, const sgs_id *recipients[], size_t recipslen)
{
    size_t i;
    uint16_t _uint16_tmp;
    sgs_session_impl *session = channel->session;
    sgs_message* msg;
    
    /** Initialize static fields of message. */
    msg = sgs_msg_create(session->msg_buf, sizeof(session->msg_buf), 
            SGS_OPCODE_CHANNEL_SEND_REQUEST, SGS_CHANNEL_SERVICE);
    if (msg == NULL)
        return -1;
    
    /** Add channel-id data field to message. */
    if (sgs_msg_add_id(msg, channel->id) == -1)
        return -1;
    
    /** Add sequence number to message. */
    if (sgs_msg_add_uint32(msg, session->seqnum_hi) == -1) return -1;
    if (sgs_msg_add_uint32(msg, session->seqnum_lo) == -1) return -1;
    
    /** Add recipient-count to message. */
    if (recipslen > UINT16_MAX) { errno = SGS_ERR_SIZE_ARG_TOO_LARGE; return -1; }
    _uint16_tmp = htons(recipslen);
    if (sgs_msg_add_arb_content(msg, (uint8_t*)(&_uint16_tmp), 2) == -1)
        return -1;
    
    /** Add each recipient-id to message. */
    for (i=0; i < recipslen; i++) {
        if (sgs_msg_add_id(msg, recipients[i]) == -1)
            return -1;
    }
    
    /** Add message payload to message. */
    if (sgs_msg_add_fixed_content(msg, data, datalen) == -1) return -1;
    
    /** Done assembling message; tell session to send it. */
    if (sgs_session_impl_send_msg(session) == -1) return -1;
    
    return 0;
}

