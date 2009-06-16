#include <stdio.h>
#include <string.h>
#include "sgs/connection.h"
#include "sgs/channel.h"
#include "sgs/connection.h"
#include "testCallbacks.h"

static uint8_t messageBuffer[256] ;


/* Called when a request to join a channel has been received.
 *  This requires sending back to the server a message of the
 *  form "joinedChannel:" with the channel name.
 */
void channel_joined_cb(sgs_connection *conn,
        sgs_channel *channel) {
    char* channelName;
    char* buf;
    char prefix[] = "joinedChannel:";

    buf = (char*)messageBuffer;
    *buf = '\0';
    buf = strncat(buf, prefix, strlen(prefix));
    channelName = sgs_channel_name(channel);
    buf = strncat(buf, channelName, strlen(channelName));
    if (sgs_channel_send(channel, messageBuffer, strlen(buf)) == -1){
        printf("error in sending response to channel join message\n");
        return;
    }
    channelJoinFail = 0;
}
/* Called when a request to leave a channel is made. This requires
 *  sending back a message to the smoke-test server of the form "leftChannel:"
 *  followed by the name of the channel that has been left. For internal purposes,
 *  successful completion of the functional also sets the channelLeaveFail flag
 *  to 0, indicating that the channel leave test has been successfully completed
 *  from the client's point of view.
 */
void channel_left_cb(sgs_connection *conn,
        sgs_channel *channel) {
    char prefix[] = "leftChannel:";
    char* buf;
    char* channelName;

    buf = (char*)messageBuffer;
    *buf = '\0';
    buf = strncat(buf, prefix, strlen(prefix));
 
    channelName = sgs_channel_name(channel);
    buf = strncat(buf, channelName, strlen(channelName));
    if (sgs_session_direct_send(sgs_connection_get_session(conn),
            messageBuffer, strlen(buf)) == -1){
        printf("error in sending response to a channel left message\n");
        return;
    }
    channelLeaveFail = 0;
}
/*
 *  Called when a channel message is received. When such
 *  a message is received, we need to send back a message
 *  of the form "receivedChannelMessage:" with the channel name,
 *  followed by a space and then the contents of the channel message.
 * This message should be sent back on the channel from which it was
 * received. 
 *  For internal purposes, we also set the channelMessageFail flag to 0
 *  when this function has successfully completed; this means that the
 *  channel receive smoke test has been called and passed (from the
 *  client's point of view)
 */
void channel_recv_msg_cb(sgs_connection *conn,
        sgs_channel *channel, const uint8_t *msg, size_t msglen) {
    char prefix[] = "receivedChannelMessage:";
    char* buf;
    char* channelName = sgs_channel_name(channel);
    uint8_t* copyBuf;
    int len;
    
    buf = (char*)messageBuffer;
    *buf = '\0';
    buf = strncat(buf, prefix, strlen(prefix));
    buf = strncat(buf, channelName, strlen(channelName));
    buf = strncat(buf, " ", 1);
    len = strlen(buf);
    copyBuf = buf + len;
    memcpy(copyBuf, msg, msglen);
    if (sgs_channel_send(channel, messageBuffer, len+msglen) == -1){
        printf("error in sending channel receive ack to server\n");
        return;
    }
    channelMessageFail = 0;
    return;

}

/* This function is called when the server disconnects the
 *  client. This is one of the tests run early in the smoketest.
 *  All this function does is indicate that the disconnect function
 * has been properly called, and sets the loginDisconnectFail flag
 * to 0. It also sets the inputReceived flag to zero so that control
 * will be passed back to the client program.
 */
void disconnected_cb(sgs_connection *conn) {
    loginDisconnectFail = 0;
    inputReceived = 0;
}

/*This callback is called upon successful login. It responds to the
 * server by sending a direct message made up of "loggedIn;", and the
 * login name used. The function also sets the loginFail flag to 0, indicating
 * that the login callback was successfully called.
 */
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
}
/* Callback for failed login, which is tested early in the smoke test.
 * When called, this will change the value of loginFailFail to 0, which indicates
 * that the loginFail test has been passed, and will set the global variable
 * inputeReceived to 0, allowing control to be passed back to the main
 * part of the client program.
 */
void login_failed_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen) {
    inputReceived = 0;
    loginFailFail = 0;
}

/*Callback indicating that a reconnected message has been received. This
 * will send back to the smoketest server the message "reconnected:". Note
 * that this test is currently unimplemented in the server.
 */
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
    return;
}
/* Callback indicating that a session message has been received from the
 * smoketest server. The response to such a message is to send a direct message
 * to the server consisting of "receivedMessage:" along with the content of the
 * message. The exception to this is if the message is "logout", in which case
 * the correct response for the client is to logout. On correctly performing
 * logout, the function will set inputReceived to 0, returning control to the
 * main function of the client.
 */
void recv_msg_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen) {
    char *buf;
    char prefix[] = "receivedMessage:";
    char logoutCmd[] = "logout";
    int i;

    if (strncmp(logoutCmd, (char*)msg, strlen(logoutCmd)) == 0){
        if (sgs_connection_logout(conn, 0) == 0){
            inputReceived = 0;
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
    sessionMessageFail = 0;
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
