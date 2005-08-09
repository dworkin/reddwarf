/*  File: sun_sim_gtk.c
 *  Pupose:  This is a client interface file that wraps around the gtk2
 *           transport layer from Gamespy.
 *           This supports the client side of an extended communications
 *           model with a formal idea of "channels".  A channel is a
 *           communication group that cna be subscribed to by many
 *           network processes.  Data sent to a channel is heard by all
 *           subscribed to that channel.
 *  Author: Jeffrey P. Kesselman
 *  Copyright 2004 Sun Microsystems, All Rights Reserved
 */

#include "gt2.h"

static GT2ConnectionCallbacks *gameConnectionCallbacks;

// wedge functions
static void SSS_ClientConnectedCallback
(
        GT2Connection connection,
        GT2Result result,
        GT2Byte * message,
        int len
)
{
  gameConnectionCallbacks->connected(connection,result,message,len);
}

static void SSS_ClientReceivedCallback
(
        GT2Connection connection,
        GT2Byte * message,
        int len,
        GT2Bool reliable
)
{
  gameConnectionCallbacks->received(connection,message,len,reliable);
}

static void SSS_ClientClosedCallback
(
        GT2Connection connection,
        GT2CloseReason reason
)
{
  gameConnectionCallbacks->closed(connection,reason);
}

// Client Functions

// Function: sss_gt2Connect
// Purpose: Replacement for Gamespy gt2Connect. Call this with the same
//          parameters.

GT2Result sss_gt2Connect
(
        GT2Socket socket,  // the local socket to use for the connection
        GT2Connection * connection,  // if the result is GT2Success, and blocking is false, the connection  object handle is stored here
        const char * remoteAddress,  // the address to connect to
        const GT2Byte * message,  // an optional initial message (may be NULL)
        int len,  // length of the initial message (may be 0, or -1 for strlen)
        int timeout,  // timeout in milliseconds (may be 0 for infinite retries)
        GT2ConnectionCallbacks * callbacks,  // callbacks for connection related stuff
        GT2Bool blocking  // if true, don't return until complete (successfuly or unsuccessfuly)
)
{
	GT2ConnectionCallbacks connectionCallbacks;
    // store game's callbacks
    gameConnectionCallbacks = callbacks;
    // make callback interception block

    memset(&connectionCallbacks, 0, sizeof(GT2ConnectionCallbacks));
    connectionCallbacks.connected = SSS_ClientConnectedCallback;
    connectionCallbacks.received = SSS_ClientReceivedCallback;
    connectionCallbacks.closed = SSS_ClientClosedCallback;
    connectionCallbacks.ping = callbacks->ping;
    return gt2Connect(socket,connection,remoteAddress,message,len,timeout,
               &connectionCallbacks,blocking);

}
