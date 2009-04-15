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

#ifndef SGS_SESSION_IMPL_H
#define SGS_SESSION_IMPL_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"

typedef struct sgs_session_impl sgs_session_impl;

#include "sgs/private/connection_impl.h"
#include "sgs/map.h"
#include "sgs/protocol.h"

struct sgs_session_impl {
    /** The underlying network connection. */
    sgs_connection_impl* connection;

    /** Server-assigned key used to reconnect after disconnect. */
    sgs_id* reconnect_key;

    /** Map of channels to which this session is currently a member. */
    sgs_map* channels;

	/** login information for this session. This is kept so that reconnect
	 *  can occur transparently for the user.
	 */
	char *login;
	char *password;

    /**
     * Used as the backing array for any sgs_messages (more efficient to just
     * declare once and keep it around than to malloc() every time we need one).
     */
    uint8_t msg_buf[SGS_MSG_MAX_LENGTH];
};

/*
 * function: sgs_session_impl_destroy()
 *
 * Performs any necessary memory deallocations to dispose of an sgs_session.
 */
void sgs_session_impl_destroy(sgs_session_impl* session);

/*
 * function: sgs_session_impl_login()
 *
 * Creates and sends a login request message to the server with the specified
 * login and password values.  Returns 0 on success and -1 on failure, with
 * errno set to the specific error code.
 */
int sgs_session_impl_login(sgs_session_impl* session, const char* login,
    const char* password);

/*
 * function: sgs_session_impl_logout()
 *
 * Creates and sends a logout request message to the server.  Returns 0 on
 * success and -1 on failure, with errno set to the specific error code.
 */
int sgs_session_impl_logout(sgs_session_impl* session);

/*
 * function: sgs_session_impl_create()
 *
 * Creates a new sgs_session from the specified connection.  Returns null on
 * failure.
 */
sgs_session_impl* sgs_session_impl_create(sgs_connection_impl* connection);

/*
 * function: sgs_session_impl_recv_msg()
 *
 * Notifies the session that a new sgs_message has been received and written
 * into the session's internal msg_buf field, ready to be processed.  Returns 0
 * on success and -1 on failure, with errno set to the specific error code.
 */
int sgs_session_impl_recv_msg(sgs_session_impl* session);

/*
 * function: sgs_session_impl_send_msg()
 *
 * Notifies the session that a new sgs_message has been created and written
 * into the session's internal msg_buf field, ready to be sent to the server.
 * Returns 0 on success and -1 on failure, with errno set to the specific error
 * code.
 */
int sgs_session_impl_send_msg(sgs_session_impl* session);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_SESSION_IMPL_H */
