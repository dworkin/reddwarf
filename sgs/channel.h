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
 * This file provides declarations for the sgs_channel interface, which
 * represents a client's view of a channel.  A channel is a communication group,
 * consisting of multiple clients and the server.
 *
 * The server is solely responsible for creating channels and adding and
 * removing clients from channels.  If desired, a client can request that a
 * channel be created by sending an application-specific message to the server
 * (using its sgs_session struct).
 */

#ifndef SGS_CHANNEL_H
#define SGS_CHANNEL_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"

typedef struct sgs_channel_impl sgs_channel;

#include "sgs/id.h"

/*
 * function: sgs_channel_name()
 *
 * Returns the name of the specified channel.
 */
const char* sgs_channel_name(const sgs_channel* channel);

/*
 * function: sgs_channel_send()
 *
 * Sends the given message to all channel members.  If this channel has no
 * members other than the sender, then no action is taken.
 *
 * args:
 *     channel: pointer to the channel on which to send the message
 *        data: array containing the message to send
 *     datalen: length of the message
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_channel_send(sgs_channel* channel,
                         const uint8_t* data,
                         size_t datalen);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_CHANNEL_H */
