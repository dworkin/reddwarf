/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations for the sgs_channel interface, which
 * represents a client's view of a channel.  A channel is a communication group,
 * consisting of multiple clients and the server.
 *
 * The server is solely responsible for creating channels and adding and
 * removing clients from channels.  If desired, a client can request that a
 * channel be created by sending an application-specific message to the server
 * (using its sgs_session struct).
 */

#ifndef SGS_CHANNEL_H
#define SGS_CHANNEL_H 1

/*
 * sgs_channel_impl provides the implementation for the sgs_channel interface
 * (declare before any #includes)
 */
typedef struct sgs_channel_impl sgs_channel;

/*
 * INCLUDES
 */
#include "sgs_id.h"

/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_channel_get_name()
 *
 * Returns the name of the specified channel.
 */
const char *sgs_channel_get_name(const sgs_channel *channel);

/*
 * function: sgs_channel_send_all()
 *
 * Sends the given message to all channel members.  If this channel has no
 * members other than the sender, then no action is taken.
 *
 * args:
 *     channel: pointer to the channel on which to send the message
 *        data: array containing the message to send
 *     datalen: length of the message
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_channel_send_all(sgs_channel* channel, const uint8_t *data,
    size_t datalen);

/*
 * function: sgs_channel_send_one()
 *
 * Sends the given message to the recipient on this channel.  If the recipient
 * is not a member of this channel, then no action is taken.
 *
 * args:
 *     channel: pointer to the channel on which to send the message
 *        data: array containing the message to send
 *     datalen: length of the message
 *   recipient: the channel member that should receive the message
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_channel_send_one(sgs_channel* channel, const uint8_t *data,
    size_t datalen, const sgs_id recipient);

/*
 * function: sgs_channel_send_multi()
 *
 * Sends the given message data to the specified recipients on this channe. Any
 * recipients that are not members of this channel are ignored.
 *
 * args:
 *     channel: pointer to the channel on which to send the message
 *        data: array containing the message to send
 *     datalen: length of the message
 *  recipients: the subset of channel members that should receive the message
 *    recipslen: length of the recipients array
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_channel_send_multi(sgs_channel* channel, const uint8_t *data,
    size_t datalen, const sgs_id recipients[], size_t recipslen);

#endif  /** #ifndef SGS_CHANNEL_H */
