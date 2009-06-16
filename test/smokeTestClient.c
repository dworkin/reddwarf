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


/* The default server connections; these can be
 * over-ridden on the command line
 */
#define DEFAULT_HOST "localhost"
#define DEFAULT_PORT 1139

/* Some global variables, declared to make life easier */

static char* g_hostname = DEFAULT_HOST;
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

void waitForInput(sgs_connection* connection)
{
    inputReceived = 1;
    while (inputReceived){
        sgs_connection_do_work(connection);
    }
}

void loadContext(sgs_context *context)
{
    sgs_ctx_set_channel_joined_cb(context, channel_joined_cb);
    sgs_ctx_set_channel_left_cb(context, channel_left_cb);
    sgs_ctx_set_channel_recv_msg_cb(context, channel_recv_msg_cb);
    sgs_ctx_set_disconnected_cb(context, disconnected_cb);
    sgs_ctx_set_logged_in_cb(context, logged_in_cb);
    sgs_ctx_set_login_failed_cb(context, login_failed_cb);
    sgs_ctx_set_reconnected_cb(context, reconnected_cb);
    sgs_ctx_set_recv_msg_cb(context, recv_msg_cb);
}

int testLogin(sgs_connection *connection)
{
    sgs_connection_login(connection, "kickme", "password1");
    waitForInput(connection);
    if (loginFailFail == 1){
        printf("Log in failure test failed\n");
    } else {
        printf("Log in failure test passed\n");
    }

    sgs_connection_login(connection, "discme", "password2");
    waitForInput(connection);
    if (loginDisconnectFail == 1){
        printf("Log in disconnect test failed\n");
    } else {
        printf("Log in disconnect test passed\n");
    }
    if (loginFailFail || loginDisconnectFail){
        return 1;
    } else {
        return 0;
    }
}

int printResults(){
    int failed = 0;

    if (loginFailFail){
        printf ("Login failure test failed\n");
        failed++;
    }
    if (loginDisconnectFail){
        printf("Login disconnect test failed\n");
        failed++;
    }
    if (loginFail){
        printf("Login test failed\n");
        failed++;
    }
    if (channelJoinFail){
        printf("Channel join test failed\n");
        failed++;
    }
    if (channelMessageFail){
        printf("Channel message test failed\n");
        failed++;
    }
    if (channelLeaveFail){
        printf("Channel leave test failed\n");
        failed++;
    }
    if (sessionMessageFail){
        printf("Session message test failed\n");
        failed++;
    }
    if (failed){
        printf("Client smoketest failed with %d failures\n", failed);
    } else {
        printf("Client smoketest passed\n");
    }
    return failed;
}

int main(int argc, char** argv) {
    sgs_context *context;
    sgs_connection *connection;
    sgs_connection *session;

    /* Begin by initializing the read sets for reading,
     * writing, and exceptions; these sets are all sets
     * of file descriptors
     */
    FD_ZERO(&g_master_readset);
    FD_ZERO(&g_master_writeset);
    FD_ZERO(&g_master_exceptset);

    /* Now, initialize all of the flags that will be
     * used to keep track of which tests pass and which
     * tests fail
     */
    loginFailFail = loginDisconnectFail = loginFail = 1;
    channelJoinFail = channelLeaveFail = channelMessageFail = 1;
    sessionMessageFail = 1;

    /* Get any command line argumentss, and
     * set the appropriate (global) variables. Currently,
     * the command line can only specify the host and port
     * of the server, and ask for the usage message
     * to be printed
     */
    g_hostname = DEFAULT_HOST;
    g_port = DEFAULT_PORT;
    getCommandArgs(argc, argv);

    /* Create a context object, and load it up with the right set
     * of callbacks. The register_fd and unregister_fd callbacks
     * are loaded as part of the create call for historical purposes
     */
    context = sgs_ctx_create(g_hostname, g_port, register_fd_cb, unregister_fd_cb);
    if (context == NULL) {
        printf("error in context create\n");
        exit(1);
    }
    loadContext(context);
    /*Now, create a connection to the server; if this doesn't work things
     * are messed up enough to require simply printing an error message
     * and getting out
     */
    connection = sgs_connection_create(context);
    if (connection == NULL){
        printf ("error in creating a connection to the server\n");
        exit(1);
    }

    if (testLogin(connection) != 0) {
        printf ("Failed at least one login test\n");
        exit(1);
    }

    sgs_connection_login(connection, loginName, loginName);

    waitForInput(connection);

    return(printResults());
}