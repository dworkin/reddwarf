/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides functions relating to the client's side of a session with
 *  a Sun Gaming Server (SGS).  Its functionality is similar to that in the java
 *  class com.sun.sgs.client.simple.SimpleClient.
 */

#ifndef SGS_SESSION_IMPL_H
#define SGS_SESSION_IMPL_H 1

/*
 * Opaque pointer (declare before any #includes)
 */
typedef struct sgs_session_impl *sgs_session_impl;

/*
 * INCLUDES
 */
#include <stdint.h>
#include "sgs_connection_impl.h"
#include "sgs_id.h"
#include "sgs_message.h"

/*
 * STRUCTS
 */
struct sgs_session_impl {
  /** The underlying network connection. */
  sgs_connection_impl connection;
  
  /** Server-assigned unique ID for this session. */
  sgs_id session_id;
  
  /** Server-assigned key used to reconnect after disconnect. */
  sgs_id reconnect_key;
  
  /**
   * Sequence number used in some messages (increment after each use).  We have
   * to store this as two 32-bit ints instead of just a single 64-bit int so
   * that we can use htonl() and ntohl() of which there are no 64-bit versions.
   */
  uint32_t seqnum_hi;
  uint32_t seqnum_lo;
  
  /**
   * Used as the backing array for any sgs_messages (more efficient to just
   * declare once and keep it around than to malloc() every time we need one).
   */
  uint8_t msg_buf[SGS_MSG_MAX_LENGTH];
};

// TODO - declaration comments

sgs_session_impl sgs_session_impl_create(sgs_connection_impl connection);
void sgs_session_impl_destroy(sgs_session_impl session);
void sgs_session_impl_incr_seqnum(sgs_session_impl session);
int sgs_session_impl_login(sgs_session_impl session, const char *login, const char *password);
int sgs_session_impl_logout(sgs_session_impl session);
int sgs_session_impl_recv_msg(sgs_session_impl session);

#endif  /** #ifndef SGS_SESSION_IMPL_H */
