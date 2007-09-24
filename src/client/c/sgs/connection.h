/*
 * This file provides declarations relating to client-server network
 * connections.
 */

#ifndef SGS_CONNECTION_H
#define SGS_CONNECTION_H 1

typedef struct sgs_connection_impl sgs_connection;

#include "sgs/config.h"
#include "sgs/context.h"

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

#endif /* !SGS_CONNECTION_H */
