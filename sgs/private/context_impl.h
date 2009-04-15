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

#ifndef SGS_CONTEXT_IMPL_H
#define SGS_CONTEXT_IMPL_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"
#include "sgs/context.h"
#include "sgs/connection.h"
#include "sgs/id.h"
#include "sgs/session.h"
#include "sgs/channel.h"
#include "sgs/socket.h"

typedef struct sgs_context_impl sgs_context_impl;

struct sgs_context_impl {
    /** Hostname and port number (of server) to connect to: */
    char *hostname;
    int port;
  
    /** function pointers to callbacks: */
    void (*reg_fd_cb)(sgs_connection*, sgs_socket_t, short);
    void (*unreg_fd_cb)(sgs_connection*, sgs_socket_t, short);
  
    void (*channel_joined_cb)(sgs_connection*, sgs_channel*);
    void (*channel_left_cb)(sgs_connection*, sgs_channel*);
    void (*channel_recv_msg_cb)(sgs_connection*, sgs_channel*, const uint8_t*, size_t);
    void (*disconnected_cb)(sgs_connection*);
    void (*logged_in_cb)(sgs_connection*, sgs_session*);
    void (*login_failed_cb)(sgs_connection*, const uint8_t*, size_t);
    void (*reconnected_cb)(sgs_connection*);
    void (*recv_message_cb)(sgs_connection*, const uint8_t*, size_t);
};

#ifdef __cplusplus
}
#endif

#endif /* !SGS_CONTEXT_IMPL_H */
