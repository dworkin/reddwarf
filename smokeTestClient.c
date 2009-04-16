/* 
 * File:   smokeTestClient.c
 * Author: waldo
 *
 * Created on April 16, 2009, 4:54 PM
 */

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <poll.h>
#include <string.h>
#include <unistd.h>
#include <sys/select.h>
#include <getopt.h>
#include <wchar.h>
#include "sgs/connection.h"
#include "sgs/context.h"
#include "sgs/session.h"
#include "sgs/map.h"


/*
 * 
 */

/* The default server connections; these can be
 * over-ridden on the command line
 */
#define DEFAULT_HOST "localhost"
#define DEFAULT_PORT 1139;

static fd_set g_master_readset, g_master_writeset, g_master_exceptset;
static int g_maxfd

/*
 * register_fd_cb()
 */
static void register_fd_cb(sgs_connection *conn, int fd, short events) {

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
 */
static void unregister_fd_cb(sgs_connection *conn, int fd, short events) {
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


int main(int argc, char** argv) {

    return (EXIT_SUCCESS);
}

