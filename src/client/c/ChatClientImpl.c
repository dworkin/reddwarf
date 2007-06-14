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

#include <stdlib.h>  // included for exit()
#include <stdio.h>
#include <sys/time.h>
#include <unistd.h>
#include "ServerSession.h"

#define RECV_TIMEOUT    0     // ms
#define FRAME_LEN       200   // duration of each frame in ms (equivalent to 1000 / framerate)
#define DEFAULT_HOST    "localhost"
#define DEFAULT_PORT    2502

/*
 * Message callbacks
 */
static void channelJoinedCallback(SGS_ID *channel);
static void channelLeftCallback(SGS_ID *channel);
static void channelRecvMsgCallback(SGS_ID *channel, SGS_ID *sender, uint8_t *msg, uint16_t msglen);
static void disconnectedCallback();
static void loggedInCallback();
static void loginFailedCallback(uint8_t *msg, uint16_t msglen);
static void reconnectedCallback();
static void recvMsgCallback(uint8_t* msg, uint16_t msglen);

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static void cleanup();
static void die(const char *msg);
static int fprintFixedLenString(FILE *stream, uint8_t *data, int datalen);
static char *getCredential(const uint8_t index);
static int getUserInput(char *buffer, const size_t buflen);
static int initVars();
static char *prefixStr(const char *prefix, const char *body, char *dst, const int size);
static void processUserInput(const char *input);

/*
 * STATIC GLOBAL VARIABLES
 *
 * Some of these are declared globally so that the various callback functions
 *  can access them; others are declared globally just so that they will persist
 *  between function calls without having to be redeclared/initialized each time.
 */
static SGS_Session *session;
static char *hostname;
static int port;
static char username[50];
static char password[50];
static int run;
static int needUserPrompt;

/*
 * function: main()
 */
int main(int argc, char *argv[]) {
  char strbuf[1024];
  int c, result, remaining;
  struct timeval now, lastframe;
  
  // stdout and stderr are normally line-buffered, but if they are redirected to a file (instead
  //  of the console) this may not be true; this annoys me so force them both to be line-buffered
  //  no matter what
  setlinebuf(stdout);
  setlinebuf(stderr);
  
  if (initVars() == -1) die("Error initializing variables");
  
  // process command line arguments
  while ((c = getopt(argc, argv, "h:p:u")) != -1) {
    switch (c) {
    case 'h':  /* hostname */
      hostname = optarg;
      break;
      
    case 'p':  /* port */
      port = atoi(optarg);
      break;
      
    case 'u':  /* usage */
      printf("Usage: %s [-h HOST] [-p PORT] [-u]\n  -h    Specify remote hostname (default: %s)\n  -p    Specify remote port (default: %d)\n  -u    Print usage\n", argv[0], DEFAULT_HOST, DEFAULT_PORT);
      return 0;
      
    // no default case necessary; an error will automatically be printed since opterr is 1
    }
  }
  
  printf("Starting up with host=%s and port=%d...\n", hostname, port);
  
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
    
    gettimeofday(&now, NULL);
    remaining = FRAME_LEN - ((now.tv_sec - lastframe.tv_sec)*1000 + (now.tv_usec - lastframe.tv_usec)/1000);
    
    if (remaining > 0) {
      // note that usleep() can exit early, but we do not try to handle this
      usleep(remaining*1000);
      
      gettimeofday(&lastframe, NULL);
    }
    else {
      lastframe = now;
    }
    
    // only call SGS_receive() if session is connected
    if (SGS_isConnected(session)) {
      result = SGS_receive(session, RECV_TIMEOUT);
      if (result == -1) die("Error in SGS_receive()");
    }
    
    result = getUserInput(strbuf, sizeof(strbuf));
    if (result == -1) die("Error reading user input");
    
    if (result > 0) {
      // there is user input to process
      processUserInput(strbuf);
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
  fprintf(stderr, " - Callback -   Joined channel %s.\n", SGS_printableCompactId(channel));
}

static void channelLeftCallback(SGS_ID *channel) {
  fprintf(stderr, " - Callback -   Left channel %s.\n", SGS_printableCompactId(channel));
}

static void channelRecvMsgCallback(SGS_ID *channel, SGS_ID *sender, uint8_t *msg, uint16_t msglen) {
  fprintf(stderr, " - Callback -   Received message on channel %s: ", SGS_printableCompactId(channel));
  fprintFixedLenString(stderr, msg, msglen);
  fprintf(stderr, "\n");
}

static void disconnectedCallback() {
  fprintf(stderr, " - Callback -   Disconnected.\n");
  run = 0;  // causes main loop to terminate so that main() will exit
}

static void loggedInCallback() {
  fprintf(stderr, " - Callback -   Logged in with sessionId %s.\n", SGS_printableCompactId(SGS_getSessionId(session)));
}

static void loginFailedCallback(uint8_t *msg, uint16_t msglen) {
  fprintf(stderr, " - Callback -   Login failed (");
  fprintFixedLenString(stderr, msg, msglen);
  fprintf(stderr, ").\n");
}

static void reconnectedCallback() {
  fprintf(stderr, " - Callback -   Reconnected.\n");
}

static void recvMsgCallback(uint8_t* msg, uint16_t msglen) {
  fprintf(stderr, " - Callback -   Received message: ");
  fprintFixedLenString(stderr, msg, msglen);
  fprintf(stderr, "\n");
}

/*
 * function: getCredential()
 *
 * Returns the request credential (login or password).
 *
 * TODO: maybe need to add SGS_Session* as the first parameter to this function
 */
static char* getCredential(const uint8_t index) {
  if (index == 0)
    return username;
  else if (index == 1)
    return password;
  else
    return NULL;
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
  perror(msg);
  cleanup();
  exit(-1);
}

/*
 * function: fprintFixedLenString()
 *
 * Prints a fixed length string to the specified stream; since the length of the
 *  string is passed as an argument, the string does not have to end with a null
 *  ('\0') character (and null characters encountered before the datalen-th
 *  character of the string will NOT terminate the copying).
 */
static int fprintFixedLenString(FILE *stream, uint8_t *data, int datalen) {
  int i;
  
  for (i=0; i < datalen; i++)
    fputc(data[i], stream);
  
  return datalen;
}

/*
 * function: getUserInput()
 *
 * Performs a non-blocking check for user input on STDIN.  If a line of text is ready
 *  to be read, it is read into buffer.
 *
 * returns:
 *  >=0: success (the value returned is the number of characters read into buffer)
 *   -1: failure (errno is set to specific error code)
 */
static int getUserInput(char *buffer, const size_t buflen) {
  struct timeval timeout_tv;
  timeout_tv.tv_sec = 0;
  timeout_tv.tv_usec = 0;
  
  fd_set readset;
  FD_ZERO(&readset);
  FD_SET(STDIN_FILENO, &readset);
  
  if (select(STDIN_FILENO+1, &readset, NULL, NULL, &timeout_tv) == -1)
    return -1;  // error
  
  if (FD_ISSET(STDIN_FILENO, &readset)) {
    // stdin is ready to be read
    
    if (fgets(buffer, buflen, stdin) == NULL) {
      if (feof(stdin) == 0) {
	// EOF is not set, so an error must have occurred
	return -1;
      }
      
      // EOF is set on stdin
      return 0;
    }
    
    needUserPrompt = 1;
    return strlen(buffer);
  }
  
  return 0;
}

/*
 * function: initVars()
 *
 * Initialize the global variables.
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int initVars() {
  // initialize session to a new SGS_Session struct
  session = SGS_createSession(1024);
  if (session == NULL) return -1;
  
  // hostname / port defaults
  hostname = DEFAULT_HOST;
  port = DEFAULT_PORT;
  
  run = 1;
  needUserPrompt = 1;
  
  return 0;
}

/*
 * function: prefixStr()
 *
 * Copies the concatenation of prefix and body into dst, returning dst.  It is explicitly
 *  supported that body may index into dst and the function will still perform correctly.
 *  The total capacity of dst is given by the size argument; if dst is not large enough
 *  to hold the concatenation, NULL is returned.
 */
static char *prefixStr(const char *prefix, const char *body, char *dst, const int size) {
  if (strlen(prefix) + strlen(body) + 1 > size) return (char*)NULL;
  
  memmove(dst + strlen(prefix), body, strlen(body) + 1);  // add 1 to copy null terminator
  memcpy(dst, prefix, strlen(prefix));
  return dst;
}

/*
 * function: processUserInput()
 *
 * Process and act on user input.
 *
 * args:
 *   input: string of text entered by the user
 */
static void processUserInput(const char *input) {
  char *token;
  char strbuf[1024];
  SGS_Channel *channel;
  SGS_ID channelId, userId;
  SGS_ID *recipientList[100];
  
  if (strlen(input) >= sizeof(strbuf)) {
    printf("Error: input string is too long.\n");
    return;
  }
  
  // should make a copy of input and operate on that because strtok makes
  //  changes to its argument
  strncpy(strbuf, input, sizeof(strbuf) - 1);
  
  // In this function, strtok is sometimes called with a delimiter list of { space, newline }
  //  and sometimes with a delimiter list of just { newline }.  Since these are easy to
  //  confused visually, take care to identify and use the correct one in each case.
  token = strtok(strbuf, " \n");
  
  if (token == NULL) {
    // nothing entered
  }
  else if (strcmp(token, "help") == 0) {
    printf("Available commands:\n");
    printf("  login <username> <password>: log into the server\n");
    printf("  logout: log out from the server (cleanly)\n");
    printf("  logoutf: log out from the server (forcibly)\n");
    printf("  srvsend <msg>: send a message directly to the server (not normally necessary)\n");
    printf("  psend <user-id> <msg>: send a private message to a user (alias: pm)\n");
    printf("  channels: print list of current channels\n");
    printf("  chsend <channel-id> <msg>: broadcast a message on a channel\n");
    printf("  chjoin <channel-name>: join a channel (alias: join)\n");
    printf("  chleave <channel-name>: leave a channel (alias: leave)\n");
    printf("\n");
  }
  else if (strcmp(token, "login") == 0) {
    token = strtok(NULL, " \n");
    
    if (token == NULL) {
      printf("Invalid command.  Syntax: login <username> <password>\n");
      return;
    }
    
    if (strlen(token) >= sizeof(username)) {
      printf("Error: username is too long.\n");
      return;
    }
    
    strncpy(username, token, sizeof(username) - 1);
    
    token = strtok(NULL, " \n");
    
    if (token == NULL) {
      printf("Invalid command.  Syntax: login <username> <password>\n");
      return;
    }
    
    if (strlen(token) >= sizeof(password)) {
      printf("Error: password is too long.\n");
      return;
    }
    
    strncpy(password, token, sizeof(password) - 1);
    
    // todo - the login() method in SimpleClient.java takes a Properties object as its
    //  lone argument; is this the best way to handle authentication?
    if (SGS_login(hostname, port, getCredential, session) == -1)
      die("Error logging in");
  }
  // make sure to check for logoutf before logout (otherwise logoutf commands will be caught
  //  by the logout case)
  else if (strcmp(token, "logoutf") == 0) {
    SGS_logout(session, 1);
    run = 0;
  }
  else if (strcmp(token, "logout") == 0) {
    SGS_logout(session, 0);
  }
  else if (strcmp(token, "srvsend") == 0) {
    token = strtok(NULL, "\n");
    
    if (token == NULL) {
      printf("Invalid command.  Syntax: srvsend <msg>\n");
      return;
    }
    
    if (SGS_sessionSend(session, (uint8_t*)token, strlen(token)) == -1)
      die("Error sending message to server");
  }
  else if (strcmp(token, "psend") == 0 || strcmp(token, "pm") == 0) {
    // for private messages, use the "Global" channel, which should be first in the list
    channel = SGS_nextChannel(SGS_getChannelList(session), NULL);
    
    token = strtok(NULL, " \n");
    
    if (token == NULL) {
      printf("Invalid command.  Syntax: psend <user> <msg>\n");
      return;
    }
    
    if (SGS_initCompactIdFromHex(token, &userId) == -1) {
      printf("Invalid user ID.\n");
      return;
    }
    
    token = strtok(NULL, "\n");
    
    if (token == NULL) {
      printf("Invalid command.  Syntax: psend <user> <msg>\n");
      return;
    }
    
    recipientList[0] = &userId;
    
    if (prefixStr("/pm ", token, strbuf, sizeof(strbuf)) == NULL)
      die("Error: ran out of buffer space (user input too big?).");
    
    if (SGS_channelSend(session, channel->id, (uint8_t*)strbuf, strlen(strbuf), recipientList, 1) == -1)
      die("Error sending channel message");
  }
  else if (strcmp(token, "chsend") == 0) {
    token = strtok(NULL, " \n");
    
    if (token == NULL) {
      printf("Invalid command.  Syntax: chsend <channel> <msg>\n");
      return;
    }
    
    if (SGS_initCompactIdFromHex(token, &channelId) == -1) {
      printf("Invalid channel ID.  Try 'channels' command.\n");
      return;
    }
    
    token = strtok(NULL, "\n");
    
    if (token == NULL) {
      printf("Invalid command.  Syntax: chsend <channel> <msg>\n");
      return;
    }
    
    // note: no prefix necessary for this command
    
    if (SGS_channelSend(session, &channelId, (uint8_t*)token, strlen(token), recipientList, 0) == -1)
      die("Error sending channel message");
  }
  else if (strcmp(token, "channels") == 0) {
    printf("Current channel list:\n");
    channel = NULL;
    
    while ((channel = SGS_nextChannel(SGS_getChannelList(session), channel)) != NULL) {
      printf("  %s: %s\n", SGS_printableCompactId(channel->id), channel->name);
    }
  }
  else if (strcmp(token, "chjoin") == 0 || strcmp(token, "join") == 0) {
    token = strtok(NULL, "\n");
    
    if (token == NULL) {
      printf("Invalid command.  Syntax: chjoin <channel-name>\n");
      return;
    }
    
    if (prefixStr("/join ", token, strbuf, sizeof(strbuf)) == NULL)
      die("Error: ran out of buffer space (user input too big?).");
    
    if (SGS_sessionSend(session, (uint8_t*)strbuf, strlen(strbuf)) == -1)
      die("Error sending message to server");
  }
  else if (strcmp(token, "chleave") == 0 || strcmp(token, "leave") == 0) {
    token = strtok(NULL, "\n");
    
    if (token == NULL) {
      printf("Invalid command.  Syntax: chleave <channel-name>\n");
      return;
    }
    
    if (prefixStr("/leave ", token, strbuf, sizeof(strbuf)) == NULL)
      die("Error: ran out of buffer space (user input too big?).");
    
    if (SGS_sessionSend(session, (uint8_t*)strbuf, strlen(strbuf)) == -1)
      die("Error sending message to server");
  }
  else {
    printf("Unrecognized command.  Try \"help\"\n");
  }
}
