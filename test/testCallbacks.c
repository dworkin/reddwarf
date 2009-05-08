#include <stdio.h>
#include <string.h>
#include "sgs/connection.h"
#include "sgs/channel.h"
#include "testCallbacks.h"

static char messageBuffer[256] = "";

void clearMessageBuffer(){
    int i, j;
    j = strlen(messageBuffer);
    for (i = 0; i < j; i++){
        *messageBuffer = '\0';
    }
}
void channel_joined_cb(sgs_connection *conn,
        sgs_channel *channel) {
    clearMessageBuffer();
    messageBuffer = strncat(messageBuffer, "joinedChannel:", strlen("joinedChannel"));
    messageBuffer = strncat(messageBuffer, sgs_channel_name(channel),
            strlen(sgs_channel_name(channel))) ;
    channelJoinFail = 0;
    printf("received channel join callback\n");
}

void channel_left_cb(sgs_connection *conn,
        sgs_channel *channel) {
    printf("received channle left callback\n");
}

void channel_recv_msg_cb(sgs_connection *conn,
        sgs_channel *channel, const uint8_t *msg, size_t msglen) {

}

void disconnected_cb(sgs_connection *conn) {
    loginDisconnectFail = 0;
    inputReceived = 0;
    printf("received disconnected callback\n");

}

void logged_in_cb(sgs_connection *conn,
        sgs_session *session) {
    clearMessageBuffer();
    messageBuffer = strncat(messageBuffer, "loggedIn:", strlen("loggedIn"));
    messageBuffer = strncat(messageBuffer, loginName, strlen(loginName));;
    if (sgs_session_direct_send(session, messageBuffer, strlen(messageBuffer) )== -1){
        printf("error in sending login response to server\n");
    }
    loginFail = 0;
    printf("received logged in callback\n");

}

void login_failed_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen) {
    inputReceived = 0;
    printf("received login failed callback\n");
    loginFailFail = 0;
}

void reconnected_cb(sgs_connection *conn) {
    inputReceived--;
    printf("received reconnected callback \n");
}

void recv_msg_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen) {
    inputReceived--;
    printf("received message callback\n");
}

/*
 * register_fd_cb()
 * This callback is used in the throttling code; this will be
 * called when throttling is turned off (that is, when reading
 * from the file descriptor is re-enabled after being turned off
 * via a call to unregister_fd_cb)
 */
void register_fd_cb(sgs_connection *conn, int fd, short events) {

    if ((events & POLLIN) == POLLIN)
        FD_SET(fd, &g_master_readset);

    if ((events & POLLOUT) == POLLOUT)
        FD_SET(fd, &g_master_writeset);

    if ((events & POLLERR) == POLLERR)
        FD_SET(fd, &g_master_exceptset);

    if (fd > g_maxfd) g_maxfd = fd;
}

/*
 * unregister_fd_cb()
 * This callback is used in the throttling code; this will be
 * called when things are getting clogged to turn off the
 * reading of the indicated file descriptor.
 */
void unregister_fd_cb(sgs_connection *conn, int fd, short events) {
    int i, new_max;

    if ((events & POLLIN) == POLLIN)
        FD_CLR(fd, &g_master_readset);

    if ((events & POLLOUT) == POLLOUT)
        FD_CLR(fd, &g_master_writeset);

    if ((events & POLLERR) == POLLERR)
        FD_CLR(fd, &g_master_exceptset);

    /** Check if a new max-fd needs to be calculated. */
    if (fd == g_maxfd) {
        new_max = 0;

        for (i = 0; i <= g_maxfd; i++) {
            if (FD_ISSET(i, &g_master_readset) ||
                    FD_ISSET(i, &g_master_writeset) ||
                    FD_ISSET(i, &g_master_exceptset))
                new_max = i;
        }

        g_maxfd = new_max;
    }
}
