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

/* Some global variables, declared to make life easier */

static fd_set g_master_readset, g_master_writeset, g_master_exceptset;
static int g_maxfd;
static char *g_hostname = DEFAULT_HOST;
static int g_port = DEFAULT_PORT;

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

        for (i=0; i <= g_maxfd; i++) {
            if (FD_ISSET(i, &g_master_readset) ||
                FD_ISSET(i, &g_master_writeset) ||
                FD_ISSET(i, &g_master_exceptset))
                new_max = i;
        }

        g_maxfd = new_max;
    }
}

void getCommandArgs(int count, char *args[]){
    int c;
    while ((c = getopt(count, args, "h:p:u"))){
        switch (c){
            case 'h': /* set the global hostname variable*/
                g_hostname = optarg;
                break;

            case 'p': /* set the global port variable */
                g_port = atoi(optarg);
                break;

            case 'u' : /*print usage*/
                printf("Usage: %s [-h HOST] [-p PORT] [-u] \n", args[0]);
                printf (""-h specify remote host for server (default %s)\n", DEFAULT_HOST);
                printf("-p specify port for server (default %d)\n", DEFAULT_PORT);
                printf("-u Print usage\n"); -
                 break;
        }
    }
}
int main(int argc, char** argv) {

    FD_ZERO(&g_master_readset);
    FD_ZERO(&g_master_writeset);
    FD_ZERO(&g_master_exceptset);

    getCommandArgs(argc, argv);

    printf("parsed command line; hostname = %s, port = %d", g_hostname, g_port);
    
    return (EXIT_SUCCESS);
}

