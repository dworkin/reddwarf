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
 * DEFINES
 */

/** Size of chunks that are read from the socket at one time (max). */
#define SGS_CONNECTION_READ_BLOCK 1024

/** Size of chunks that are written to the socket at one time (max). */
#define SGS_CONNECTION_WRITE_BLOCK 1024

/*
 * FUNCTION DECLARATIONS
 */
// todo - comments
sgs_connection sgs_connection_create(sgs_context ctx);

void sgs_connection_destroy(sgs_connection connection);

int sgs_connection_do_io(sgs_connection connection, int fd, short events);

int sgs_connection_login(sgs_connection connection, const char *login, const char *password);

int sgs_connection_logout(sgs_connection connection, const int force);

#endif  /** #ifndef SGS_CONNECTION_H */
