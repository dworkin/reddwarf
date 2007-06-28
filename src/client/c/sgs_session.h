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
 * function: SGS_channelSend()
 *
 * Sends a CHANNEL_SEND_REQUEST message to the server, which is used when sending
 *  a message on a specific channel.
 *
 * args:
 *     session: pointer to the current user session
 *   channelId: pointer to the ID of the channel on which to send
 *        data: pointer to an array of data to send
 *     datalen: length of the data array
 *  recipients: array of pointers to IDs of recipients to send to; empty (length=0) implies "all"
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
 * function: SGS_sessionSend()
 *
 * Sends a message directly to the server (i.e. not on a channel).  This is
 * often-times used to implement application-specific messaging.
 *
 * args:
 *   session: pointer to the current user session
 *      data: array of message payload to send
 *   datalen: length of data array
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_session_direct_send(sgs_session session, const uint8_t *data,
                            size_t datalen);

const sgs_id *sgs_session_get_reconnectkey(const sgs_session session);

const sgs_id *sgs_session_get_id(const sgs_session session);

#endif  /** #ifndef SGS_SESSION_H */
