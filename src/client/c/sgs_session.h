/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

/*
 * This file provides declarations relating to client-server sessions.
 */

#ifndef SGS_SESSION_H
#define SGS_SESSION_H 1

/*
 * sgs_session_impl provides the implementation for the sgs_session interface
 * (declare before any #includes)
 */
typedef struct sgs_session_impl sgs_session;

/*
 * INCLUDES
 */
#include <stdint.h>
#include "sgs_id.h"

/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_session_direct_send()
 *
 * Sends a message directly to the server (i.e. not on a channel).  This is
 * sometimes used to implement application-specific messaging.
 *
 * args:
 *   session: the session to send a message
 *      data: array containing the message to send
 *   datalen: length of the message
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_session_direct_send(sgs_session *session, const uint8_t *data,
    size_t datalen);

/*
 * function: sgs_session_get_reconnectkey()
 *
 * Returns the reconnection-key for this session.
 */
const sgs_id *sgs_session_get_reconnectkey(const sgs_session *session);

/*
 * function: sgs_session_get_id()
 *
 * Returns this session's unique ID.
 */
const sgs_id *sgs_session_get_id(const sgs_session *session);

#endif  /** #ifndef SGS_SESSION_H */
