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
#include "testCallbacks.h"


/*
 * 
 */

/* The default server connections; these can be
 * over-ridden on the command line
 */
#define DEFAULT_HOST "localhost"
#define DEFAULT_PORT 1139

/* Some global variables, declared to make life easier */

static char *g_hostname = DEFAULT_HOST;
static int g_port = DEFAULT_PORT;


void getCommandArgs(int count, char *args[]){
    int c;
    while ((c = getopt(count, args, "h:p:u")) != -1){
        switch (c){
            case 'h': /* set the global hostname variable*/
                g_hostname = optarg;
                break;

            case 'p': /* set the global port variable */
                g_port = atoi(optarg);
                break;

            case 'u' : /*print usage*/
                printf("Usage: %s [-h HOST] [-p PORT] [-u] \n", args[0]);
                printf ("-h specify remote host for server (default %s)\n", DEFAULT_HOST);
                printf("-p specify port for server (default %d)\n", DEFAULT_PORT);
                printf("-u Print usage\n");
                 break;
        }
    }
}


int main(int argc, char** argv) {
    sgs_context *context;
    sgs_connection *connection;
    sgs_connection *session;

    FD_ZERO(&g_master_readset);
    FD_ZERO(&g_master_writeset);
    FD_ZERO(&g_master_exceptset);

    getCommandArgs(argc, argv);

    printf("parsed command line; hostname = %s, port = %d\n", g_hostname, g_port);

    context = sgs_ctx_create(g_hostname, g_port, register_fd_cb, unregister_fd_cb);
    if (context == NULL) {
        printf("error in context create\n");
        exit(1);
    }


    
    return (EXIT_SUCCESS);
}

