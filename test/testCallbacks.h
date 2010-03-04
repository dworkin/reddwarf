/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
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
 *
 */

#ifndef _TESTCALLBACKS_H
#define	_TESTCALLBACKS_H

#ifdef	__cplusplus
extern "C" {
#endif

#include <sys/select.h>
#include "sgs/connection.h"
#include "sgs/channel.h"
#include "sgs/session.h"

static fd_set g_master_readset, g_master_writeset, g_master_exceptset;
static int g_maxfd;
static char  loginName[] = "smokeTest";

/* A set of flags for the callback function tests-- the flag will
 * be set before a call that should trigger a callback, and then will
 * be reset by the callback; the main program can do the set, make the
 * call, wait on the callback, and then check the flag.
 */

int loginFailFail, loginDisconnectFail,
        loginFail, channelJoinFail, channelLeaveFail,
        channelMessageFail, sessionMessageFail;
int inputReceived;

/* Declarations of the callback functions associated with the
 * context of a C client session. These are declared in this file to
 * allow them to be implemented in a separate file from the driver
 * of the client smoketest.
 */
void channel_joined_cb(sgs_connection *conn,
            sgs_channel *channel);

void channel_left_cb(sgs_connection *conn,
            sgs_channel *channel);

void channel_recv_msg_cb(sgs_connection *conn,
        sgs_channel *channel, const uint8_t *msg, size_t msglen);

void disconnected_cb(sgs_connection *conn);

void logged_in_cb(sgs_connection *conn,
        sgs_session *session);

void login_failed_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen);

void reconnected_cb(sgs_connection *conn);

void recv_msg_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen);

void register_fd_cb(sgs_connection *conn, int fd, short events);

void unregister_fd_cb(sgs_connection *conn, int fd, short events);

#ifdef	__cplusplus
}
#endif

#endif	/* _TESTCALLBACKS_H */

