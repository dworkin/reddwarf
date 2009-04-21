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

#include "sgs/connection.h";
#include "sgs/channel.h";
#include "sgs/session.h";"

static fd_set g_master_readset, g_master_writeset, g_master_exceptset;
static int g_maxfd;

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

