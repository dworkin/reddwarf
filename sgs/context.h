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
 * This file provides declarations relating to session contexts.
 */

#ifndef SGS_CONTEXT_H
#define SGS_CONTEXT_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"

typedef struct sgs_context_impl sgs_context;

#include "sgs/channel.h"
#include "sgs/id.h"
#include "sgs/connection.h"
#include "sgs/session.h"
#include "sgs/socket.h"

/*
 * function: sgs_ctx_destroy()
 *
 * Performs any necessary memory deallocations to dispose of an sgs_context.
 */
void sgs_ctx_destroy(sgs_context* ctx);

/*
 * function sgs_ctx_create_empty()
 *
 *Creates a new login context, without filling in any callbacks.
 * Returns null on failure, and a pointer to the new context on
 * success.
 *
 * args:
 *  hostname: the hostname of the server to connect to. Note that the
 *      function assumes that this argument points to a null-terminated
 *      character array.
 *  port: the network port to connect to
 *
 */
sgs_context* sgs_ctx_create_empty(const char*hostname,
                            const int port);

/*
 * function: sgs_ctx_create()
 *
 * Creates a new login context.  Returns null on failure.
 *
 * args:
 *  hostname: the hostname of the server to connect to. Note that the
 *      function assumes that this argument points to a null-terminated
 *      character array.
 *  port: the network port of the server to connect to
 *  reg_fd: a callback function used by a sgs_connection (when created with
 *            this sgs_context) to register interest in a file descriptor
 *  unreg_fd: a callback function used by a sgs_connection (when created with
 *            this sgs_context) to unregister interest in a file descriptor 
 *
 * arguments to reg_fd and unreg_fd:
 *   sgs_connection*: the connection making this callback
 *               int: a file descriptor
 *             short: events for which interest is being (un)registered for the
 *                    specified file descriptor
 *
 * Note that for compatibility with select(2) instead of just poll(2), the only
 * events that will be ever be specified in reg_fd and unreg_fd will be POLLIN,
 * POLLOUT, and/or POLLERR.
 */
sgs_context* sgs_ctx_create(const char* hostname, const int port,
    void (*reg_fd)(sgs_connection*, sgs_socket_t, short),
    void (*unreg_fd)(sgs_connection*, sgs_socket_t, short));

/*
 * function: sgs_ctx_set_channel_joined_cb()
 *
 * Registers a function to be called whenever a channel is joined.  The function
 * should take the following arguments:
 *   sgs_connection*: the connection making this callback
 *      sgs_channel*: the channel that was joined
 */
void sgs_ctx_set_channel_joined_cb(sgs_context* ctx,
    void (*callback)(sgs_connection*, sgs_channel*));

/*
 * function: sgs_ctx_set_channel_left_cb()
 *
 * Registers a function to be called whenever a channel is joined.  The function
 * should take the following arguments:
 *   sgs_connection*: the connection making this callback
 *      sgs_channel*: the channel that was left
 */
void sgs_ctx_set_channel_left_cb(sgs_context* ctx,
    void (*callback)(sgs_connection*, sgs_channel*));

/*
 * function: sgs_ctx_set_channel_recv_msg_cb()
 *
 * Registers a function to be called whenever a message is received on a
 * channel.  The function should take the following arguments:
 *   sgs_connection*: the connection making this callback
 *      sgs_channel*: the channel on which the message was received
 *    const uint8_t*: the received message (not null-terminated)
 *            size_t: the length of the received message
 */
void sgs_ctx_set_channel_recv_msg_cb(sgs_context* ctx,
    void (*callback)(sgs_connection*, sgs_channel*, const uint8_t*, size_t));

/*
 * function: sgs_ctx_set_disconnected_cb()
 *
 * Registers a function to be called whenever the connection is disconnected.
 * The function should take the following arguments:
 *   sgs_connection: the connection making this callback
 */
void sgs_ctx_set_disconnected_cb(sgs_context* ctx,
    void (*callback)(sgs_connection*));

/*
 * function: sgs_ctx_set_logged_in_cb()
 *
 * Registers a function to be called whenever a session login completes with the
 * server.  The function should take the following arguments:
 *   sgs_connection: the connection making this callback
 *      sgs_session: a handle for making method calls on the session
 */
void sgs_ctx_set_logged_in_cb(sgs_context* ctx,
    void (*callback)(sgs_connection*, sgs_session*));

/*
 * function: sgs_ctx_set_login_failed_cb()
 *
 * Registers a function to be called whenever a login request fails.  The
 * function should take the following arguments:
 *   sgs_connection: the connection making this callback
 *   const uint8_t*: an explanatory message from the server (not \0-terminated)
 *           size_t: the length of the message from the server
 */
void sgs_ctx_set_login_failed_cb(sgs_context* ctx,
    void (*callback)(sgs_connection*, const uint8_t*, size_t));

/*
 * function: sgs_ctx_set_reconnected_cb()
 *
 * Registers a function to be called whenever the session with the server is
 * reconnected after losing connection.  The function should take the following
 * arguments:
 *   sgs_connection: the connection making this callback
 */
void sgs_ctx_set_reconnected_cb(sgs_context* ctx,
    void (*callback)(sgs_connection*));

/*
 * function: sgs_ctx_set_recv_msg_cb()
 *
 * Registers a function to be called whenever a message is received directly
 * from the server (i.e. not on a channel).  The function should take the
 * following arguments:
 *   sgs_connection: the connection making this callback
 *   const uint8_t*: the message sent by the server (not \0-terminated)
 *           size_t: the length of the message sent by the server
 */
void sgs_ctx_set_recv_msg_cb(sgs_context* ctx,
    void (*callback)(sgs_connection*, const uint8_t*, size_t));

/*
 *  function sgs_ctx_set_reg_fb_cb()
 *
 * Registers a function to be called when a file descriptor
 * is to be set to be read from. If the file descriptor is already
 * active for reading, this function should have no effect; if
 * it is currently inactive (most likely because input from this
 * descriptor needed to be throttled to keep from overwhelming
 * the client) then the file descriptor will be added to the
 * set of file descriptors that will be fed into the select calls
 */
void sgs_ctx_set_reg_fb_cb(sgs_context *ctx,
        void (*callback)(sgs_connection *, sgs_socket_t, short));

/*
 * function sgs_ctx_set_unreg_fb_cb()
 *
 * Registers a function to be called when a file descriptor
 * is to be removed from the set of descriptors to be read from.
 * If the file descriptor is already inactive for reading (most
 * likely to throttle input to avoid swamping the client) the
 * call will have no effect. Otherwise, the callback should
 * remove the file descriptor from the set of active file
 * descriptors
 */

void sgs_ctx_set_unreg_fb_cb(sgs_context *ctx,
        void ( *callback)(sgs_connection *, sgs_socket_t, short));

/*
 * function: sgs_ctx_unset_all_cbs()
 *
 * Unregisters all event callback functions on the specified login context.
 */
void sgs_ctx_unset_all_cbs(sgs_context* ctx);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_CONTEXT_H */
