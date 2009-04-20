
#include "testCallbacks.h"
#include "sgs/connection.h"
#include "sgs/channel.h"

void channel_joined_cb(sgs_connection *conn,
        sgs_channel *channel) {

}

void channel_left_cb(sgs_connection *conn,
        sgs_channel *channel){

}

void channel_recv_msg_cb(sgs_connection *conn,
        sgs_channel *channel, const uint8_t msg, size_t msglen)
{

}

void disconnected_cb(sgs_connection *conn)
{

}

void logged_in_cb(sgs_connection *conn,
        sgs_session *session)
{

}

void login_failed_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen)
{

}

void reconnected_cb(sgs_connection *conn)
{

}

void recv_msg_cb(sgs_connection *conn, const uint8_t *msg, size_t msglen)
{

}

/*
 * register_fd_cb()
 * This callback is used in the throttling code; this will be
 * called when throttling is turned off (that is, when reading
 * from the file descriptor is re-enabled after being turned off
 * via a call to unregister_fd_cb)
 */
void register_fd_cb(sgs_connection *conn, int fd, short events)
{

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

        for (i=0; i <= g_maxfd; i++) {
            if (FD_ISSET(i, &g_master_readset) ||
                FD_ISSET(i, &g_master_writeset) ||
                FD_ISSET(i, &g_master_exceptset))
                new_max = i;
        }

        g_maxfd = new_max;
    }
}
