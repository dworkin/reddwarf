/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations relating to client-server sessions.
 */

#ifndef SGS_SESSION_H
#define SGS_SESSION_H 1

/*
 * Opaque pointer (declare before any #includes)
 */
typedef struct sgs_session_impl *sgs_session;

/*
 * INCLUDES
 */
#include <stdint.h>
#include "sgs_id.h"

/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_session_channel_send()
 *
 * Sends a message on a specific channel.
 *
 * args:
 *     session: the session to send a message
 *   channelId: pointer to the ID of the channel on which to send the message
 *        data: array containing the message to send
 *     datalen: length of the message
 *  recipients: array of ID for all of the recipients to send to; empty
 *              (length=0) means "send to all members of the channel"
 *    reciplen: length of the recipients array
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_session_channel_send(sgs_session session, const sgs_id *pchannel_id,
    const uint8_t *data, size_t datalen, const sgs_id recipients[],
    size_t reciplen);

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
int sgs_session_direct_send(sgs_session session, const uint8_t *data,
                            size_t datalen);

/*
 * function: sgs_session_get_reconnectkey()
 *
 * Returns the reconnection-key for this session.
 */
const sgs_id *sgs_session_get_reconnectkey(const sgs_session session);

/*
 * function: sgs_session_get_id()
 *
 * Returns this session's unique ID.
 */
const sgs_id *sgs_session_get_id(const sgs_session session);

#endif  /** #ifndef SGS_SESSION_H */
