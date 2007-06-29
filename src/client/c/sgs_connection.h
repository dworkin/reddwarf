/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations relating to client-server network connections.
 */

#ifndef SGS_CONNECTION_H
#define SGS_CONNECTION_H 1

/*
 * Opaque pointer (declare before any #includes)
 */
typedef struct sgs_connection_impl *sgs_connection;

/*
 * INCLUDES
 */
#include "sgs_context.h"

/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_connection_create()
 *
 * Creates a new sgs_connection from the specified login context.  Returns null
 * on failure.
 */
sgs_connection sgs_connection_create(sgs_context ctx);

/*
 * function: sgs_connection_destroy()
 *
 * Performs any necessary memory deallocations to dispose of an sgs_connection.
 */
void sgs_connection_destroy(sgs_connection connection);

/*
 * function: sgs_connection_do_io()
 *
 * Informs the sgs_connection that the specified file descriptor has select()-ed
 * or poll()-ed for the specified bitmap of events.  This is used to implement
 * non-blocking IO on the sgs_connection's underlying socket connection.  The
 * sgs_connection will notify applications of file descriptor(s) that it is
 * interested in monitoring by calling the reg_fd() and unreg_fg() callback
 * functions that were specified as arguments to sgs_ctx_create() when the
 * connection's context was created.  Returns 0 on success and -1 on failure,
 * with errno set to the specific error code.
 */
int sgs_connection_do_io(sgs_connection connection, int fd, short events);

/*
 * function: sgs_connection_login()
 *
 * Creates and sends a login request message to the server with the specified
 * login and password values.  Returns 0 on success and -1 on failure, with
 * errno set to the specific error code.
 */
int sgs_connection_login(sgs_connection connection, const char *login,
                         const char *password);

/*
 * function: sgs_connection_logout()
 *
 * Creates and sends a login request message to the server.  Returns 0 on
 * success and -1 on failure, with errno set to the specific error code.
 */
int sgs_connection_logout(sgs_connection connection, const int force);

#endif  /** #ifndef SGS_CONNECTION_H */
