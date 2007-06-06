/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: May 30, 2007
 *
 * TODO - header comments explaining what the heck this file is
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "ServerSession.h"

#define MSG_DATABUF_SIZE   1024   // bytes

/*
 * TODO - when is CHANNEL_SEND_REQUEST sent?
 *
 * Stuff I send to server:
 * CHANNEL_SEND_REQUEST:   <channel-id, compact> <sequence number (8)> <recipients count (2)> [ <recip session ID, compact> ]*
 */

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static int connectToServer(const char *hostname, const int port);
static int consumeData(SGS_Session *session);
static void disconnect(SGS_Session *session);
static int processMessage(SGS_Session *session, SGS_Message *msg);
static uint16_t readLenHeader(uint8_t *data);
static int writeMsg(SGS_Session *session, const SGS_Message *msg);


/*
 * EXTERNAL FUNCTION IMPLEMENTATIONS
 * (these are functions that are exposed to the outside world).
 */

/*
 *
 */
SGS_Session *SGS_createSession(uint32_t msgBufSize) {
  SGS_Session *session;
  
  session = (SGS_Session *)malloc(sizeof(SGS_Session));
  
  if (session == NULL) {
    errno = ENOMEM;
    return NULL;
  }
  
  session->msgBuf = SGS_createMsg(msgBufSize);
  
  if (session->msgBuf == NULL) {
    // "roll back" allocation of memory to SGS_Session object
    free(session);
    errno = ENOMEM;
    return NULL;
  }
  
  session->socket_fd = 0;
  session->session_id.len = 0;
  session->reconnect_key.len = 0;
  SGS_initChannelList(&session->channel_list);
  session->expecting_disconnect = 0;
  session->inbuf_bytes = 0;
  
  // function pointers:
  session->channelJoinedF  = NULL;
  session->channelLeftF    = NULL;
  session->channelRecvMsgF = NULL;
  session->disconnectedF   = NULL;
  session->loggedInF       = NULL;
  session->loginFailedF    = NULL;
  session->reconnectedF    = NULL;
  session->recvMessageF    = NULL;
  
  return session;
}

void SGS_destroySession(SGS_Session *session) {
  // first destroy the msgBuf struct
  SGS_destroyMsg(session->msgBuf);
  
  // next empty the ChannelList
  SGS_emptyChannelList(&session->channel_list);
  
  // then deallocate the SGS_Session struct itself
  free(session);
}

/*
 * todo
 */
SGS_ID SGS_getSessionId(const SGS_Session *session) {
  return session->session_id;
}

/*
 * TODO
 */
int SGS_isConnected(const SGS_Session *session) {
  if (session->socket_fd == 0)
    return 0;
  else
    return 1;
}

/*
 * TODO - comments
 */
int SGS_login(const char *hostname, const int port, char* (*auth)(uint8_t), SGS_Session *session) {
  char *login, *pwd;
  int sockfd;
  SGS_Message *msg;
  
  // try to open socket to specified host and port
  sockfd = connectToServer(hostname, port);
  if (sockfd < 0) return -1;
  
  session->socket_fd = sockfd;
  
  // perform callback to get authentication information
  login = auth(0);
  pwd = auth(1);
  
  // create SGS_Message struct
  msg = SGS_createMsg(MSG_DATABUF_SIZE);
  if (msg == NULL) {
    close(sockfd);
    return -1;
  }
  
  // initialize message object's static fields
  if (SGS_initMsg(msg, SGS_LOGIN_REQUEST, SGS_APPLICATION_SERVICE) < 0) {
    SGS_destroyMsg(msg);
    close(sockfd);
    return -1;
  }
  
  // add "login" field to message
  if (SGS_addFixedMsgContent(msg, login, strlen(login)) < 0) {
    SGS_destroyMsg(msg);
    close(sockfd);
    return -1;
  }
  
  // add "password" field to message
  if (SGS_addFixedMsgContent(msg, pwd, strlen(pwd)) < 0) {
    SGS_destroyMsg(msg);
    close(sockfd);
    return -1;
  }
  
  // send message to server
  if (writeMsg(session, msg) < 0) {
    SGS_destroyMsg(msg);
    close(sockfd);
    return -1;
  }
  
  // done with this structure now
  SGS_destroyMsg(msg);
  
  return 0;
}

/*
 * TODO
 * if force, just disconnect, else, send message
*/
int SGS_logout(SGS_Session *session, const int force) {
  SGS_Message *msg;
  
  if (SGS_isConnected(session) == 0) {
    // error: socket is not connected
    errno = ENOTCONN;
    return -1;
  }
  
  if (force == 1) {
    disconnect(session);
    return 0;
  }
  else {
    msg = SGS_createMsg(MSG_DATABUF_SIZE);
    if (msg == NULL) return -1;
    
    // initialize message object's static fields
    if (SGS_initMsg(msg, SGS_LOGOUT_REQUEST, SGS_APPLICATION_SERVICE) < 0) {
      SGS_destroyMsg(msg);
      return -1;
    }
    
    // send message to server
    if (writeMsg(session, msg) < 0) {
      SGS_destroyMsg(msg);
      return -1;
    }
    
    // done with this structure now
    SGS_destroyMsg(msg);
    
    return 0;
  }
}

/**
 * TODO
 */
int SGS_receive(SGS_Session *session, const uint32_t timeout) {
  int result;
  fd_set readset;
  struct timeval timeout_tv;
  
  // check that this session is connected
  if (SGS_isConnected(session) == 0) {
    errno = ENOTCONN;
    return -1;
  }
  
  // initialize timeout struct
  timeout_tv.tv_sec = 0;
  timeout_tv.tv_usec = timeout*1000;
  
  FD_ZERO(&readset);
  FD_SET(session->socket_fd, &readset);
  result = select(session->socket_fd + 1, &readset, NULL, NULL, &timeout_tv);
  
  // error in select()
  if (result == -1) return -1;
  
  // timeout
  if (result == 0) return 0;
  
  if (FD_ISSET(session->socket_fd, &readset) == 0) {
    // bad state - this should never happen
    errno = ILLEGAL_STATE_ERR;
    return -1;
  }
  
  // socket has something to read; receive it
  
  if (session->inbuf_bytes == SESSION_BUFSIZE) {
    // ran out of room!
    errno = ENOBUFS;
    return -1;
  }
  
  result = read(session->socket_fd, session->inbuf, SESSION_BUFSIZE - session->inbuf_bytes);
  
  if (result == 0) {
    // the server closed the socket
    if (session->expecting_disconnect == 0) {
      // unexpected close of connection
      if (session->disconnectedF != NULL)
	session->disconnectedF();
    }
    
    disconnect(session);
    session->expecting_disconnect = 0;
    
    return 0;
  }
  else {
    session->inbuf_bytes += result;
    
    if (consumeData(session) == -1)
      return -1;
    
    return 0;
  }
}

/*
 * TODO
 */
int SGS_send(SGS_Session *session, const uint8_t *data, const uint32_t len) {
  SGS_Message *msg = SGS_createMsg(len + 8);
  if (msg == NULL) return -1;  // error
  
  if (SGS_initMsg(msg, SGS_SESSION_MESSAGE, SGS_APPLICATION_SERVICE) == -1)
    return -1; // error
  
  // TODO - first 8 bytes of msg->data should be a sequence number
  
  memcpy(msg->data + 8, data, len);
  msg->data_len = len + 8;
  
  if (SGS_isConnected(session) == 0) {
    // error: socket is not connected
    errno = ENOTCONN;
    return -1;
  }
  
  if (writeMsg(session, msg) < 0)
    return -1;
  else
    return 0;
  
  SGS_destroyMsg(msg);
}

/*
 * registration functions for event callbacks:
 */

// todo - list parameters for the function-pointers
void SGS_regChannelJoinedCallback(SGS_Session *session, void (*callback)(SGS_ID*)) {
  session->channelJoinedF = callback;
}

void SGS_regChannelLeftCallback(SGS_Session *session, void (*callback)(SGS_ID*)) {
  session->channelLeftF = callback;
}

void SGS_regChannelRecvMsgCallback(SGS_Session *session, void (*callback)(SGS_ID*, SGS_ID*, uint8_t*, uint16_t)) {
  session->channelRecvMsgF = callback;
}

void SGS_regDisconnectedCallback(SGS_Session *session, void (*callback)()) {
  session->disconnectedF = callback;
}

void SGS_regLoggedInCallback(SGS_Session *session, void (*callback)()) {
  session->loggedInF = callback;
}

void SGS_regLoginFailedCallback(SGS_Session *session, void (*callback)(uint8_t*, uint16_t)) {
  session->loginFailedF = callback;
}

void SGS_regReconnectedCallback(SGS_Session *session, void (*callback)()) {
  session->reconnectedF = callback;
}

void SGS_regRecvMsgCallback(SGS_Session *session, void (*callback)(uint8_t*, uint16_t)) {
  session->recvMessageF = callback;
}

/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */

/*
 * function: connectToServer()
 *
 * This function handles opening a socket and connecting it to a specified server.
 *
 * args:
 *  hostname: name of the host to connect to in string format
 *      port: network port to connect to on the remote host
 *
 * returns:
 *   >0: success (valid is a valid file descriptor)
 *   -1: failure (errno is set to specific error code)
 */
static int connectToServer(const char *hostname, const int port) {
  int sockfd, ioflags;
  struct hostent *server;
  struct sockaddr_in serv_addr;
  
  // open the socket (not connected yet)
  // note: alternative SOCK_DGRAM
  sockfd = socket(AF_INET, SOCK_STREAM, 0);
  if (sockfd < 0)
    return -1;
  
  // resolve hostname to IP(s)
  server = gethostbyname(hostname);
  if (server == NULL) {
    // note, there is no "host not found" error value for errno, so we use "No route to host"
    //  as a mediocre substitute
    errno = EHOSTUNREACH;  
    return -1;
  }
  
  // initialize server_addr to all zeroes, then fill in fields
  bzero((char *) &serv_addr, sizeof(serv_addr));
  serv_addr.sin_family = AF_INET;
  bcopy((char *)server->h_addr, (char *)&serv_addr.sin_addr.s_addr, server->h_length);
  serv_addr.sin_port = htons(port);
  
  // try to connect to server
  // (note, ok to cast sockaddr_in* to sockaddr* according to http://retran.com/beej/sockaddr_inman.html )
  if (connect(sockfd, (const struct sockaddr*) &serv_addr, sizeof(serv_addr)) < 0)
    return -1;
  
  // I'm not sure if fnctl sets errno on errors, so I'll reset it to 0 here so that at least it won't
  //  print an incorrect (old) error
  errno = 0;
  
  // set non-blocking mode
  if ((ioflags = fcntl(sockfd, F_GETFL, 0)) == -1)
    return -1;
  
  if (fcntl(sockfd, F_SETFL, ioflags | O_NONBLOCK) == -1)
    return -1;
  
  return sockfd;
}

/*
 * TODO
 */
// todo - if an error occurs in this function, the session buffers are left in a bad state
//  (I think the message will be reread?)
static int consumeData(SGS_Session *session) {
  int8_t result;
  uint32_t index = 0;
  uint32_t remaining;
  
  while (index < session->inbuf_bytes) {
    result = SGS_deserializeMsg(session->msgBuf, session->inbuf + index, session->inbuf_bytes);
    if (result == -1) return -1;  // error
    if (result == -2) break;  // data comprises only a partial message
    
    // else, full message was deserialized from buffer and read into msgBuf
    index += result;
    
    if (processMessage(session, session->msgBuf) == -1)
      return -1; // error
  }
  
  remaining = session->inbuf_bytes - index;
  
  if (remaining > 0) {
    // there is still some data leftover, but not a complete message (still more
    //  of it yet to be received)
    
    // if src and dest overlap (i.e. the amount to be copied is longer than the open
    //  space at the beginning of inbuf[]) then we have to use memmove() instead of
    //  memcpy()
    if (index > remaining)
      memcpy(session->inbuf, session->inbuf + index, remaining);
    else
      memmove(session->inbuf, session->inbuf + index, remaining);
  }
  
  session->inbuf_bytes = remaining;
  
  return 0;
}

/*
 * Closes the network connection.
 */
static void disconnect(SGS_Session *session) {
  close(session->socket_fd);
  session->socket_fd = 0;
}


/*
 * todo
 */
static int processMessage(SGS_Session *session, SGS_Message *msg) {
  int8_t result;
  uint16_t namelen, offset;
  SGS_ID channelId, senderId;  // temporary buffers for copying things into
  
  if (msg->version_id != SGS_VERSION) {
    errno = BAD_VERSION_ERR;
    return -1;
  }
  
  if (msg->service_id == SGS_APPLICATION_SERVICE) {
    switch (msg->op_code) {
    case SGS_LOGIN_SUCCESS:
      // field 1: session-id (compact-id format)
      result = SGS_decodeCompactId(msg->data, msg->data_len, &session->session_id);
      if (result == -1) return -1;  // error
      
      // field 2: reconnection-key (compact-id format)
      result = SGS_decodeCompactId(msg->data + result, msg->data_len - result, &session->reconnect_key);
      if (result == -1) return -1;  // error
      
      if (session->loggedInF != NULL)
	session->loggedInF();
      
      return 0;
      
    case SGS_LOGIN_FAILURE:
      // field 1: error string (first 2 bytes = length of string)
      if (session->loginFailedF != NULL)
	session->loginFailedF(msg->data + 2, readLenHeader(msg->data));
      
      return 0;
      
    case SGS_SESSION_MESSAGE:
      // field 1: sequence number (8 bytes)
      // TODO first 8 bytes are a sequence number that is currently ignored
      offset = 8;
      
      // field 2: message (first 2 bytes = length of message)
      if (session->recvMessageF != NULL)
	session->recvMessageF(msg->data + (offset+2), readLenHeader(msg->data + offset));
      
      return 0;
      
    case SGS_RECONNECT_SUCCESS:
      if (session->reconnectedF != NULL)
	session->reconnectedF();
      
      return 0;
      
    case SGS_RECONNECT_FAILURE:
      disconnect(session);
      return 0;
      
    case SGS_LOGOUT_SUCCESS:
      session->expecting_disconnect = 1;
      return 0;
      
    default:
      errno = BAD_OPCODE_ERR;
      return -1;
    }
  }
  else if (msg->service_id == SGS_CHANNEL_SERVICE) {
    switch (msg->op_code) {
    case SGS_CHANNEL_JOIN:
      // field 1: channel name (first 2 bytes = length of string)
      namelen = readLenHeader(msg->data);
      
      // field 2: channel-id (compact-id format)
      result = SGS_decodeCompactId(msg->data + namelen + 2, msg->data_len - namelen - 2, &channelId);
      if (result == -1) return -1; // error
      
      result = SGS_putChannelIfAbsent(&session->channel_list, &channelId, msg->data + 2, namelen);
      if (result == -1) return -1; // error
      
      if (result == 1 && session->channelJoinedF != NULL)
	session->channelJoinedF(&channelId);
      
      return 0;
      
    case SGS_CHANNEL_LEAVE:
      // field 1: channel-id (compact-id format)
      result = SGS_decodeCompactId(msg->data, msg->data_len, &channelId);
      if (result == -1) return -1; // error
      
      result = SGS_removeChannel(&session->channel_list, &channelId);
      // note: currently we do NOT abort if this function returns -1 (indicating that the specified channel
      //  does not appear in the channel-list)
      
      if (result == 0 && session->channelLeftF != NULL)
	session->channelLeftF(&channelId);
      
      return 0;
      
    case SGS_CHANNEL_MESSAGE:
      // field 1: channel-id (compact-id format)
      result = SGS_decodeCompactId(msg->data, msg->data_len, &channelId);
      if (result == -1) return -1; // error
      
      // field 2: sequence number (8 bytes)
      // TODO next 8 bytes are a sequence number that is currently ignored
      offset = result + 8;
      
      // field 3: session-id of sender (compact-id format)
      result = SGS_decodeCompactId(msg->data + offset, msg->data_len - offset, &senderId);
      if (result == -1) return -1; // error
      
      offset += result;
      
      // TODO - debugging code
      /*printf("MESSAGE:");
      
      for (result=0; result < msg->data_len; result++)
	printf(" %x", msg->data[result]);
      
      printf("\n");
      */
      
      // field 4: message (first 2 bytes = length of message)
      if (session->channelRecvMsgF != NULL) {
	if (SGS_equalsServerId(&senderId))
	  session->channelRecvMsgF(&channelId, NULL, msg->data + (offset+2), readLenHeader(msg->data + offset));
	else
	  session->channelRecvMsgF(&channelId, &senderId, msg->data + (offset+2), readLenHeader(msg->data + offset));
      }
      
      return 0;
      
    default:
      errno = BAD_OPCODE_ERR;
      return -1;
    }
  }
  else {
    errno = BAD_SERVICE_ERR;
    return -1;
  }
}

/*
 * todo
 */
static uint16_t readLenHeader(uint8_t *data) {
  uint16_t tmp;
  tmp = data[0];
  tmp = tmp*256 + data[1];
  return tmp;
}


/*
 * function: writeMsg()
 *
 * This function writes a SGS message to a file descriptor (typically a socket).
 *
 * args:
 *   session: current user session
 *       msg: the message to send
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int writeMsg(SGS_Session *session, const SGS_Message *msg) {
  int msglen, msgindex = 0, written = 0;
  
  msglen = SGS_serializeMsg(msg, session->outbuf, SESSION_BUFSIZE);
  
  if (msglen < 0)
    return -1;
  
  while (msgindex < msglen) {
    // note pointer arithmetic
    written = write(session->socket_fd, session->outbuf + written, (msglen - written));
    
    if (written < 0)
      return written;
    
    msgindex += written;
  }
  
  // flush file descriptor
  fsync(session->socket_fd);
  
  return 0;
}
