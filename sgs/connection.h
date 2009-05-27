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
 * This file provides declarations relating to client-server network
 * connections.
 */

#ifndef SGS_CONNECTION_H
#define SGS_CONNECTION_H 1

#ifdef __cplusplus
extern "C" {
#endif

typedef struct sgs_connection_impl sgs_connection;

#include "sgs/config.h"
#include "sgs/context.h"
#include "sgs/session.h"

/*
 * function: sgs_connection_create()
 *
 * Creates a new sgs_connection from the specified login context.  Returns NULL
 * on failure.
 */
sgs_connection* sgs_connection_create(sgs_context* ctx);

/*
 * function: sgs_connection_destroy()
 *
 * Performs any necessary memory deallocations to dispose of an sgs_connection.
 */
void sgs_connection_destroy(sgs_connection* connection);

/*
 * function: sgs_connection_do_work()
 *
 * Informs the sgs_connection that one or more file descriptors are ready for
 * IO operations.  The sgs_connection will call select() or a similar method to
 * determine specifically which file descriptors are ready and for which
 * operations (e.g. writing or reading).  This is used to implement
 * non-blocking IO on the sgs_connection's underlying socket connection.  The
 * sgs_connection will notify applications of file descriptor(s) that it is
 * interested in monitoring by calling the reg_fd() and unreg_fg() callback
 * functions that were specified as arguments to sgs_ctx_new() when the
 * connection's context was created.  Returns 0 on success and -1 on failure,
 * with errno set to the specific error code.
 */
int sgs_connection_do_work(sgs_connection* connection);

/*
 * function: sgs_connection_login()
 *
 * Creates and sends a login request message to the server with the specified
 * login and password values.  Returns 0 on success and -1 on failure, with
 * errno set to the specific error code.
 */
int sgs_connection_login(sgs_connection* connection, const char* login,
    const char* password);

/*
 * function: sgs_connection_logout()
 *
 * Creates and sends a login request message to the server.  Returns 0 on
 * success and -1 on failure, with errno set to the specific error code.
 */
int sgs_connection_logout(sgs_connection* connection, int force);

/*
 * function sgs_connection_get_context()
 *
 * returns the context associated with this connection
 *
 */

sgs_context* sgs_connection_get_context(sgs_connection* connection);

/*
 *  function sgs_connection_get_session
 *
 *  returns the session associated with this connection
 */
sgs_session* sgs_connection_get_session(sgs_connection* connection);


#ifdef __cplusplus
}
#endif

#endif /* !SGS_CONNECTION_H */
