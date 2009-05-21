#include <stdio.h>
#include <string.h>
#include "sgs/connection.h"
#include "sgs/channel.h"
#include "sgs/connection.h"
#include "testCallbacks.h"

static uint8_t messageBuffer[256] ;


void channel_joined_cb(sgs_connection *conn,
        sgs_channel *channel) {
    char* channelName;
    char* buf;
    char prefix[] = "joinedChannel:";

    buf = (char*)messageBuffer;
    *buf = '\0';
    buf = strncat(buf, prefix, strlen(prefix));
    channelName = (char*)sgs_channel_name(channel);
    printf("channel name = %s\n", channelName);
    buf = strncat(buf, channelName, strlen(channelName));
    if (sgs_session_direct_send(sgs_connection_get_session(conn),
            messageBuffer, strlen(buf))== -1){
        printf("error in sending response to channel join message\n");
        return;
    }
    channelJoinFail = 0;
    printf("received channel join callback\n");
}

void channel_left_cb(sgs_connection *conn,
        sgs_channel *channel) {
    char prefix[] = "leftChannel:";
    char* buf;
    char* channelName;

    buf = (char*)messageBuffer;
    *buf = '\0';
    buf = strncat(buf, prefix, strlen(prefix));
    channelName = (char*) sgs_channel_name(channel);
    buf = strncat(buf, channelName, strlen(channelName));
    if (sgs_session_direct_send(sgs_connection_get_session(conn),
            messageBuffer, strlen(buf)) == -1){
        printf("error in sending response to a channel left message\n");
        return;
    }
    channelLeaveFail = 0;
    printf("received channel left callback\n");
}

void channel_recv_msg_cb(sgs_connection *conn,
        sgs_channel *channel, const uint8_t *msg, size_t msglen) {
    char prefix[] = "receivedChannelMessage:";
    char* buf;
    char* channelName = (char*) sgs_channel_name(channel);
    uint8_t* copyBuf;
    int len;
    
    buf = (char*)messageBuffer;
    *buf = '\0';
   /* channelName = (char*)sgs_channel_name(channel);*/
    buf = strncat(buf, prefix, strlen(prefix));
    buf = strncat(buf, channelName, strlen(channelName));
    buf = strncat(buf, " ", 1);
    len = strlen(buf);
    copyBuf = buf + len;
     memcpy(copyBuf, msg, msglen);
     printf("about to send message %s of length %d\n", messageBuffer, len+msglen);
    if (sgs_session_direct_send(sgs_connection_get_session(conn),
            messageBuffer,( len + msglen)-1) == -1){
        printf("error in sending channel receive ack to server\n");
        return;
    }
    channelMessageFail = 0;
    return;

}

void disconnected_cb(sgs_connection *conn) {
    loginDisconnectFail = 0;
    inputReceived = 0;
    printf("received disconnected callback\n");

}

void logged_in_cb(sgs_connection *conn,
        sgs_session *session) {
    char* buf;

    buf = (char*) messageBuffer;
    *buf = '\0';
    buf = strncat(buf, "loggedIn:", strlen("loggedIn:"));
    buf  = strncat(buf, loginName, strlen(loginName));
    if (sgs_session_direct_send(session, messageBuffer, strlen(messageBuffer) ) == -1){
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
    char* buf;
    char prefix[] = "reconnected";

    buf = (char*)messageBuffer;
    *buf = '\0';

    buf = strncat(buf, prefix, strlen(prefix));

    if (sgs_session_direct_send(sgs_connection_get_session(conn),
            messageBuffer, strlen(prefix)) == -1){
        printf("error in sending reconnected ack\n");
        return;
    }

    printf("received reconnected callback \n");
    return;
}

void recv_msg_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen) {
    char *buf;
    char prefix[] = "receivedMessage:";
    char logoutCmd[] = "logout";
    int i;

    if (strncmp(logoutCmd, (char*)msg, strlen(logoutCmd)) == 0){
        if (sgs_connection_logout(conn, 0) == 1){
            printf("received instructions to logout\n");
            return;
        }
    }
    
    buf = (char*)messageBuffer;
    *buf = '\0';
    i = strlen(prefix);
    buf = strncat(buf, prefix, i);
    buf = memcpy(buf+i, msg, msglen);

    if (sgs_session_direct_send(sgs_connection_get_session(conn),
            messageBuffer, i+msglen) == -1){
        printf("error sending receive message ack\n");
        return;
    }
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
