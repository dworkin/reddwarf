/* 
 * File:   testCallbacks.h
 * Author: waldo
 *
 * Created on April 17, 2009, 1:02 PM
 */

#ifndef _TESTCALLBACKS_H
#define	_TESTCALLBACKS_H

#ifdef	__cplusplus
extern "C" {
#endif

#include <sys/select.h>
#include "sgs/connection.h"
#include "sgs/channel.h"
#include "sgs/session.h"

static fd_set g_master_readset, g_master_writeset, g_master_exceptset;
static int g_maxfd;
static char  loginName[] = "smokeTest";

/* A set of flags for the callback function tests-- the flag will
 * be set before a call that should trigger a callback, and then will
 * be reset by the callback; the main program can do the set, make the
 * call, wait on the callback, and then check the flag.
 */

int loginFailFail, loginDisconnectFail,
        loginFail, channelJoinFail, channelLeaveFail,
        channelMessageFail, sessionMessageFail;
int inputReceived;

/* Declarations of the callback functions associated with the
 * context of a C client session. These are declared in this file to
 * allow them to be implemented in a separate file from the driver
 * of the client smoketest.
 */
void channel_joined_cb(sgs_connection *conn,
            sgs_channel *channel);

void channel_left_cb(sgs_connection *conn,
            sgs_channel *channel);

void channel_recv_msg_cb(sgs_connection *conn,
        sgs_channel *channel, const uint8_t *msg, size_t msglen);

void disconnected_cb(sgs_connection *conn);

void logged_in_cb(sgs_connection *conn,
        sgs_session *session);

void login_failed_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen);

void reconnected_cb(sgs_connection *conn);

void recv_msg_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen);

void register_fd_cb(sgs_connection *conn, int fd, short events);

void unregister_fd_cb(sgs_connection *conn, int fd, short events);

#ifdef	__cplusplus
}
#endif

#endif	/* _TESTCALLBACKS_H */

