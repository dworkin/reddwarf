/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: June 1, 2007
 *
 * This file exercises SimpleClient.c to test its API.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include <stdio.h>
#include <unistd.h>
#include "ServerSession.h"

#define RECV_TIMEOUT      50  // ms
#define MSG_BUF_SIZE    1024  // bytes
#define INPUT_BUF_SIZE  1024  // bytes

/*
 * Message callbacks
 */
static void channelJoinedCallback(SGS_ID *channel);
static void channelLeftCallback(SGS_ID *channel);
static void channelRecvMsgCallback(SGS_ID *channel, SGS_ID *sender, uint8_t *msg, uint16_t len);
static void disconnectedCallback();
static void loggedInCallback();
static void loginFailedCallback(uint8_t *msg, uint16_t len);
static void reconnectedCallback();
static void recvMsgCallback(uint8_t* msg, uint16_t len);

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static void cleanup();
static void die(const char *msg);
static char* getCredential(const uint8_t index);
static int getUserInput(char *buf, int buflen);
static int initVars();

/*
 * STATIC GLOBAL VARIABLES
 *
 * Some of these are declared globally so that the various callback functions
 *  can access them; others are declared globally just so that they will persist
 *  between function calls without having to be redeclared/initialized each time.
 */
static SGS_Session *session;
static char credentials[][100] = {"ian", "password"};
static struct timeval timeout_tv;
static int run;
static int needUserPrompt;

/*
 * function: main()
 */
int main(int argc, char *argv[]) {
  char input[INPUT_BUF_SIZE];
  int result;
  
  if (argc != 3) {
    printf("usage %s hostname port\n", argv[0]);
    exit(0);
  }
  
  // stdout and stderr are normally line-buffered, but if they are redirected to a file (instead
  //  of the console) this may not be true; this annoys me so force them both to be line-buffered
  //  no matter what
  setlinebuf(stdout);
  setlinebuf(stderr);
  
  printf("Starting up...\n");
  
  if (initVars() == -1) die("Error initializing variables");
  
  // register callbacks for events
  SGS_regChannelJoinedCallback(session, channelJoinedCallback);
  SGS_regChannelLeftCallback(session, channelLeftCallback);
  SGS_regChannelRecvMsgCallback(session, channelRecvMsgCallback);
  SGS_regDisconnectedCallback(session, disconnectedCallback);
  SGS_regLoggedInCallback(session, loggedInCallback);
  SGS_regLoginFailedCallback(session, loginFailedCallback);
  SGS_regReconnectedCallback(session, reconnectedCallback);
  SGS_regRecvMsgCallback(session, recvMsgCallback);
  
  // note - select() is actually being called twice per loop (once explicitly for STDIN
  //  and once via SGS_receive() -- perhaps this is inefficient and SGS_receive() should
  //  be changed so that select() has to be called by clients explicitly)
  while (run) {
    if (needUserPrompt) {
      printf("Command: ");
      fflush(stdout);
      needUserPrompt = 0;
    }
    
    // todo
    //printf("sleeping for 3s\n");
    sleep(1);
    
    // only call SGS_receive() if session is connected
    if (SGS_isConnected(session)) {
      result = SGS_receive(session, RECV_TIMEOUT);
      if (result == -1) die("Error in SGS_receive()");
    }
    
    result = getUserInput(input, INPUT_BUF_SIZE);
    if (result == -1) die("Error reading user input");
    
    if (result > 0) {
      // there is user input to process
      
      printf("checking user input: %s\n", input);
      
      if (strncmp(input, "debug", 5) == 0) {
	
      }
      else if (strncmp(input, "help", 4) == 0) {
	printf("Available commands:\n");
	printf("  login <username> <password>: logs into the server\n");
	printf("  logout: logs out from the server (cleanly)\n");
	printf("  logoutf: logs out from the server (forcibly)\n");
	printf("\n");
      }
      else if (strncmp(input, "login", 5) == 0) {
	// todo - the login() method in SimpleClient.java takes a Properties object as its
	//  lone argument; is this the best way to handle authentication?
	if (SGS_login(argv[1], atoi(argv[2]), getCredential, session) == -1)
	  die("Error logging in");
      }
      // make sure to check for logoutf before logout (otherwise logoutf commands will be caught
      //  by the logout case)
      else if (strncmp(input, "logoutf", 7) == 0) {
	SGS_logout(session, 1);
	run = 0;
      }
      else if (strncmp(input, "logout", 6) == 0) {
	SGS_logout(session, 0);
      }
      else {
	printf("Unrecognized command.  Try \"help\"\n");
      }
    }
  }
  
  // perform cleanup!
  cleanup();
  
  printf("Goodbye!\n");
  
  return 0;
}


/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */

// TODO - maybe I need to add SGS_Session as the first argument to each of these?


// In general these functions (since they occur seemingly "asynchronously" with regard to
//  the user's interaction with the command prompt) generally print to stderr instead of
//  stdout;  for example, this allows you to redirect stderr to a file and then (in a new
//  terminal) display it with "tail -f"
static void channelJoinedCallback(SGS_ID *channel) {
  fprintf(stderr, "channelJoinedCallback on channel %s.\n", channel->hexstr);
}

static void channelLeftCallback(SGS_ID *channel) {
  fprintf(stderr, "channelLeftCallback on channel %s.\n", channel->hexstr);
}

static void channelRecvMsgCallback(SGS_ID *channel, SGS_ID *sender, uint8_t *msg, uint16_t len) {
  fprintf(stderr, "channelRecvMsgCallback on channel %s: %s\n", channel->hexstr, msg);
}

static void disconnectedCallback() {
  fprintf(stderr, "disconnectedCallback\n");
  run = 0;  // causes main loop to terminate so that main() will exit
}

static void loggedInCallback() {
  fprintf(stderr, "loggedInCallback.  sessionId=%s\n", session->session_id.hexstr);
}

static void loginFailedCallback(uint8_t *msg, uint16_t len) {
  fprintf(stderr, "loginFailedCallback: %s\n", msg);
}

static void reconnectedCallback() {
  fprintf(stderr, "reconnectedCallback\n");
}

static void recvMsgCallback(uint8_t* msg, uint16_t len) {
  fprintf(stderr, "recvMsgCallback: %s\n", msg);
}

/*
 * TODO
 */
// maybe need to add SGS_Session* as the first parameter to this function
static char* getCredential(const uint8_t index) {
  return credentials[index];
}

/*
 * function: cleanup()
 *
 * Takes care of any and all housekeeping before the program exits.
 */
static void cleanup() {
  SGS_destroySession(session);
}

/*
 * function: die()
 *
 * Prints an error and quits.
 *
 * args:
 *   msg: String to print to stderr.
 *
 */
static void die(const char *msg) {
  /*if (IS_CUSTOM_ERR(errno)) {
    if (msg != NULL && *msg != '\0') {
      fprintf(stderr, "%s: Custom Error #%d", msg, errno);
    }
    else {
      fprintf(stderr, "Custom Error #%d", errno);
    }
  }
  else {*/
    perror(msg);
    //}
  
  cleanup();
  exit(-1);
}

/*
 * todo
 */
static int getUserInput(char *buf, int buflen) {
  fd_set readset;
  FD_ZERO(&readset);
  FD_SET(STDIN_FILENO, &readset);
  
  if (select(STDIN_FILENO+1, &readset, NULL, NULL, &timeout_tv) == -1)
    return -1;  // error
  
  if (FD_ISSET(STDIN_FILENO, &readset)) {
    // stdin is ready to be read
    
    if (fgets(buf, buflen, stdin) == NULL) {
      if (feof(stdin) == 0) {
	// EOF is not set, so an error must have occurred
	return -1;
      }
      
      // EOF is set on stdin
      return 0;
    }
    
    needUserPrompt = 1;
    return strlen(buf);
  }
  
  return 0;
}

/*
 * This function initializes the global variables.
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int initVars() {
  // initialize session to a new SGS_Session struct
  session = SGS_createSession(MSG_BUF_SIZE);
  if (session == NULL) return -1;
  
  // initialize timeout_tv to 0
  timeout_tv.tv_sec = 0;
  timeout_tv.tv_usec = 0;
  
  run = 1;
  needUserPrompt = 1;
  
  return 0;
}
