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
 * This file provides an implementation of connection contexts.  Implements
 * functions declared in sgs_context.h.
 */

#include "sgs/config.h"

#include "sgs/channel.h"
#include "sgs/connection.h"
#include "sgs/id.h"
#include "sgs/private/context_impl.h"
#include "sgs/session.h"
#include "private/context_impl.h"

/*
 * sgs_ctx_destroy()
 */
void sgs_ctx_destroy(sgs_context_impl *ctx) {
    free(ctx->hostname);
    free(ctx);
}

/*
 * sgs_ctx_create_empty()
 * Note that this function assumes that the first argument, hostname,
 * points to a null-terminated character array
 */
sgs_context_impl *sgs_ctx_create_empty(const char* hostname, const int port){
    int name_len;

    sgs_context_impl *ctx = NULL;

    ctx = malloc(sizeof (struct sgs_context_impl));
    if (ctx == NULL) return NULL;

    //Get the length of the string, then add one for the null terminator
    name_len = strlen(hostname) + 1;
    ctx->hostname = malloc(name_len);
    if (ctx->hostname == NULL) {
        free(ctx);
        return NULL;
    }

    strncpy(ctx->hostname, hostname, name_len);
    ctx->port = port;

    /* initialize all callback functions to NULL */
    sgs_ctx_unset_all_cbs(ctx);
    return ctx;
}

/*
 * sgs_ctx_create()
 * Note that this function assumes that the first argument, hostname, points
 * to a null-terminated character array. 
 */
sgs_context_impl *sgs_ctx_create(const char *hostname, const int port,
        void (*reg_fd)(sgs_connection*, sgs_socket_t, short),
        void (*unreg_fd)(sgs_connection*, sgs_socket_t, short)) {
    sgs_context_impl *ctx = NULL;

    ctx = sgs_ctx_create_empty(hostname, port);
    if (ctx != NULL){
        ctx->reg_fd_cb = reg_fd;
        ctx->unreg_fd_cb = unreg_fd;
    }
    return ctx;
}

/*
 * sgs_ctx_set_channel_joined_cb()
 */
void sgs_ctx_set_channel_joined_cb(sgs_context_impl *ctx,
        void (*callback)(sgs_connection*, sgs_channel*)) {
    ctx->channel_joined_cb = callback;
}

/*
 * sgs_ctx_set_channel_left_cb()
 */
void sgs_ctx_set_channel_left_cb(sgs_context_impl *ctx,
        void (*callback)(sgs_connection*, sgs_channel*)) {
    ctx->channel_left_cb = callback;
}

/* 
 * sgs_ctx_set_channel_recv_msg_cb()
 */
void sgs_ctx_set_channel_recv_msg_cb(sgs_context_impl *ctx,
        void (*callback)(sgs_connection*, sgs_channel*, const uint8_t*, size_t)) {
    ctx->channel_recv_msg_cb = callback;
}

/*
 * sgs_ctx_set_disconnected_cb()
 */
void sgs_ctx_set_disconnected_cb(sgs_context_impl *ctx,
        void (*callback)(sgs_connection*)) {
    ctx->disconnected_cb = callback;
}

/*
 * sgs_ctx_set_logged_in_cb()
 */
void sgs_ctx_set_logged_in_cb(sgs_context_impl *ctx,
        void (*callback)(sgs_connection*, sgs_session*)) {
    ctx->logged_in_cb = callback;
}

/*
 * sgs_ctx_set_login_failed_cb()
 */
void sgs_ctx_set_login_failed_cb(sgs_context_impl *ctx,
        void (*callback)(sgs_connection*, const uint8_t*, size_t)) {
    ctx->login_failed_cb = callback;
}

/*
 * sgs_ctx_set_reconnected_cb()
 */
void sgs_ctx_set_reconnected_cb(sgs_context_impl *ctx,
        void (*callback)(sgs_connection*)) {
    ctx->reconnected_cb = callback;
}

/*
 * sgs_ctx_set_recv_msg_cb()
 */
void sgs_ctx_set_recv_msg_cb(sgs_context_impl *ctx,
        void (*callback)(sgs_connection*, const uint8_t*, size_t)) {
    ctx->recv_message_cb = callback;
}

/*
 * sgs_ctx_set_reg_fb_cb()
 */
void sgs_ctx_set_reg_fb_cb(sgs_context *ctx,
        void (*callback)(sgs_connection *, sgs_socket_t, short)) {
    ctx->reg_fd_cb = callback;
}

/*
 *sgs_ctx_set_unreg_fb_cb
 */
void sgs_ctx_set_unreg_fb_cb(sgs_context *ctx,
        void ( *callback)(sgs_connection *, sgs_socket_t, short)) {
    ctx->unreg_fd_cb = callback;
}

/*
 * sgs_ctx_unset_all_cbs()
 */
void sgs_ctx_unset_all_cbs(sgs_context_impl *ctx) {
    ctx->channel_joined_cb = NULL;
    ctx->channel_left_cb = NULL;
    ctx->channel_recv_msg_cb = NULL;
    ctx->disconnected_cb = NULL;
    ctx->logged_in_cb = NULL;
    ctx->login_failed_cb = NULL;
    ctx->reconnected_cb = NULL;
    ctx->recv_message_cb = NULL;
    ctx->reg_fd_cb = NULL;
    ctx->unreg_fd_cb = NULL;
}

