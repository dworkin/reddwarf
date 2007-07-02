/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations for an implementation of functions relating
 * to client-server network connections.
 */

#ifndef SGS_CONNECTION_IMPL_H
#define SGS_CONNECTION_IMPL_H 1

/*
 * sgs_connect_impl typdef (declare before any #includes)
 */
typedef struct sgs_connection_impl sgs_connection_impl;

/*
 * INCLUDES
 */
#include <stdint.h>
#include "sgs_buffer.h"
#include "sgs_context_impl.h"
#include "sgs_session_impl.h"
#include "sgs_wire_protocol.h"

/*
 * DEFINES
 */
#define SGS_CONNECTION_IMPL_IO_BUFSIZE SGS_MSG_MAX_LENGTH

/*
 * TYPEDEFS
 */
typedef enum {
  SGS_CONNECTION_IMPL_DISCONNECTED,
  SGS_CONNECTION_IMPL_CONNECTING,
  SGS_CONNECTION_IMPL_CONNECTED,
} sgs_connection_state;

/*
 * STRUCTS
 */
struct sgs_connection_impl {
  /** File descriptor for the network socket to the server. */
  int socket_fd;
  
  /** Whether we expect the server to close the socket: 1 = yes, 0 = no */
  char expecting_disconnect;
  
  /** The current state of the connection. */
  sgs_connection_state state;
  
  /** The login context (contains all callback functions). */
  sgs_context_impl *ctx;
  
  /** The session with the server (once connected). */
  sgs_session_impl *session;
  
  /** Reusable I/O buffers for reading/writing from/to the network connection. */
  sgs_buffer *inbuf, *outbuf;
};

/*
 * function: sgs_connection_impl_disconnect()
 *
 * Closes the network connection of a connection.
 */
void sgs_connection_impl_disconnect(sgs_connection_impl *connection);

/*
 * function: sgs_connection_impl_io_write()
 *
 * Writes buflen bytes from the buf array to the connection's underlying socket.
 */
int sgs_connection_impl_io_write(sgs_connection_impl *connection, uint8_t *buf,
                                 size_t buflen);

#endif  /** #ifndef SGS_CONNECTION_IMPL_H */
