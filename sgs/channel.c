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
#include "sgs/private/message.h"
#include "sgs/private/channel_impl.h"
#include "sgs/private/session_impl.h"


/*
 * sgs_channel_name()
 */
const char* sgs_channel_name(const sgs_channel_impl *channel) {
    return channel->name;
}

/*
 * sgs_session_channel_send()
 */
int sgs_channel_send(sgs_channel_impl *channel, const uint8_t *data,
    size_t datalen)
{
    int result;
    sgs_session_impl *session = channel->session;
    sgs_message msg;
    
    /** Initialize static fields of message. */
    result = sgs_msg_init(&msg, session->msg_buf, sizeof(session->msg_buf), 
						  SGS_OPCODE_CHANNEL_MESSAGE);
    if (result < 0)
        return -1;
    
    /** Add channel-id data field to message. */
    if (sgs_msg_add_id(&msg, channel->id, 1) == -1)
        return -1;
	
    
    /** Add message payload to message. */
    if (sgs_msg_add_arb_content(&msg, data, datalen) == -1) return -1;
    
    /** Done assembling message; tell session to send it. */
    if (sgs_session_impl_send_msg(session) == -1) return -1;
    
    return 0;
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
const sgs_id* sgs_channel_impl_get_id(sgs_channel_impl *channel) {
    return channel->id;
}

/*
 * sgs_channel_impl_create()
 */
sgs_channel_impl* sgs_channel_impl_create(sgs_session_impl *session,
        sgs_id* id, const char* namebytes, size_t namelen) {
    sgs_channel_impl *channel;

    channel = malloc(sizeof (struct sgs_channel_impl));
    if (channel == NULL)
        return NULL;

    channel->session = session;
    channel->id = id;
    channel->name = malloc(namelen + 1);
    if (channel->name == NULL) {
        sgs_channel_impl_destroy(channel);
        return NULL;
    }

    strncpy(channel->name, namebytes, namelen);
    channel->name[namelen] = '\0';

    return channel;
}


