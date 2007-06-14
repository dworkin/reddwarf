/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: May 30, 2007
 *
 * This file provides functions relating to the client's side of a session with
 *  a Sun Gaming Server (SGS).  Its functionality is similar to that in the java
 *  class com.sun.sgs.client.simple.SimpleClient.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "ServerSession.h"

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
 * function: SGS_channelSend()
 *
 * Sends a CHANNEL_SEND_REQUEST message to the server, which is used when sending
 *  a message on a specific channel.
 *
 * args:
 *     session: pointer to the current user session
 *   channelId: pointer to the ID of the channel on which to send
 *        data: pointer to an array of data to send
 *     datalen: length of the data array
 *  recipients: array of pointers to IDs of recipients to send to; empty (length=0) implies "all"
 *    reciplen: length of the recipients array
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int SGS_channelSend(SGS_Session *session, SGS_ID *channelId, const uint8_t *data, const uint16_t datalen, SGS_ID *recipients[], const uint16_t reciplen) {
  int i;
  uint8_t tmp[2];
  
  if (SGS_initMsg(session->msgBuf, SGS_CHANNEL_SEND_REQUEST, SGS_CHANNEL_SERVICE) == -1) return -1;
  
  // add channel-id to message
  if (SGS_addArbMsgContent(session->msgBuf, channelId->compressed, channelId->compressedlen) == -1) return -1;
    
  // add sequence number to message
  if (SGS_addArbMsgContent(session->msgBuf, (uint8_t*)(&session->seqNum), 8) == -1) return -1;
  session->seqNum++;
  
  // add recipient-count to message
  tmp[0] = reciplen / 256;
  tmp[1] = reciplen % 256;
  if (SGS_addArbMsgContent(session->msgBuf, tmp, 2) == -1) return -1;
  
  // add recipient-ids to message
  for (i=0; i < reciplen; i++) {
    if (SGS_addArbMsgContent(session->msgBuf, recipients[i]->compressed, recipients[i]->compressedlen) == -1) return -1;
  }
  
  // add data to message
  if (SGS_addFixedMsgContent(session->msgBuf, data, datalen) == -1) return -1;
  
  if (SGS_isConnected(session) == 0) {
    // error: socket is not connected
    errno = ENOTCONN;
    return -1;
  }
  
  if (writeMsg(session, session->msgBuf) < 0)
    return -1;
  else
    return 0;
}

/*
 * function: SGS_createSession()
 *
 * Creates a new session object.  Any struct created (returned) by this function should be destroyed
 *  by calling SGS_destroySession() before it goes out of scope to avoid memory leaks.
 *
 * args:
 *  msgBufSize: maximum buffer size for optional data in any messages sent during this session
 *
 * returns:
 *  On any error, errno is set to the specfific error code and NULL is returned.  Otherwise,
 *    a pointer to a new allocated SGS_Session is returned.
 */
SGS_Session *SGS_createSession(size_t msgBufSize) {
  SGS_Session *session;
  session = (SGS_Session *)malloc(sizeof(SGS_Session));
  
  if (session == NULL) {
    errno = ENOMEM;
    return NULL;
  }
  
  // set all pointer fields in session to NULL
  session->msgBuf        = NULL;
  session->session_id    = NULL;
  session->reconnect_key = NULL;
  session->channel_list  = NULL;
  
  // do (attempt) all memory allocations
  session->msgBuf        = SGS_createMsg(msgBufSize);
  session->session_id    = (SGS_ID *)malloc(sizeof(SGS_ID));
  session->reconnect_key = (SGS_ID *)malloc(sizeof(SGS_ID));
  session->channel_list  = (SGS_ChannelList *)malloc(sizeof(SGS_ChannelList));
  
  // check if any memory allocation call failed
  if (session->msgBuf == NULL || session->session_id == NULL || session->reconnect_key == NULL || session->channel_list == NULL) {
    // "roll back" any successful memory allocations
    if (session->msgBuf != NULL)        SGS_destroyMsg(session->msgBuf);
    if (session->session_id != NULL)    free(session->session_id);
    if (session->reconnect_key != NULL) free(session->reconnect_key);
    if (session->channel_list != NULL)  free(session->channel_list);
    
    // "roll back" allocation of memory to the SGS_Session object itself
    free(session);
    errno = ENOMEM;
    return NULL;
  }
  
  // initialize fields
  session->socket_fd = 0;
  session->session_id->datalen = 0;
  session->reconnect_key->datalen = 0;
  SGS_initChannelList(session->channel_list);
  session->expecting_disconnect = 0;
  session->seqNum = 0;
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

/*
 * function: SGS_destroySession()
 *
 * Takes care of any/all cleanup when an SGS_Session struct is going to be thrown away.
 *
 * args:
 *   session: pointer to the SGS_Session to destroy
 *
 * returns: nothing
 */
void SGS_destroySession(SGS_Session *session) {
  // empty the ChannelList
  SGS_emptyChannelList(session->channel_list);
  
  // deallocate dynamically-allocated structures
  SGS_destroyMsg(session->msgBuf);
  free(session->session_id);
  free(session->reconnect_key);
  
  
  // deallocate the SGS_Session struct itself
  free(session);
}

/*
 * function: SGS_getChannelList()
 *
 * Returns the SGS_ChannelList object for the specified session.
 *
 * args:
 *   session: pointer to the current user session
 */
SGS_ChannelList *SGS_getChannelList(const SGS_Session *session) {
  return session->channel_list;
}

/*
 * function: SGS_getReconnectKey()
 *
 * Returns the reconnection key for the specified session.
 *
 * args:
 *   session: pointer to the current user session
 */
SGS_ID *SGS_getReconnectKey(const SGS_Session *session) {
  return session->reconnect_key;
}

/*
 * function: SGS_getSessionId()
 *
 * Returns the session-id for the specified session.
 *
 * args:
 *   session: pointer to the current user session
 */
SGS_ID *SGS_getSessionId(const SGS_Session *session) {
  return session->session_id;
}

/*
 * function: SGS_isConnected()
 *
 * Returns whether the specified session is connected to a server.
 *
 * args:
 *   session: pointer to the current user session
 *
 * returns:
 *    1: session is connected
 *    0: session is not connected
 */
int SGS_isConnected(const SGS_Session *session) {
  if (session->socket_fd == 0)
    return 0;
  else
    return 1;
}

/*
 * function: SGS_login()
 *
 * Sends a login request to the specified server.
 *
 * args:
 *   hostname: the name of the server to connect to
 *       port: the TCP port to connect to
 *       auth: function to return the users' login and password (when passed 0 or 1,
 *             respectively, as arguments)
 *    session: pointer to a session object to login with
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 *     
 * TODO - this model of passing an "auth" function is meant to emulate the Java API in which
 *  a Properties object is passed (which contains login and password entries), but seems rather
 *  awkward.  Perhaps it should just be changed to login and password arguments to this function.
 */
int SGS_login(const char *hostname, const int port, char* (*auth)(uint8_t), SGS_Session *session) {
  char *login, *pwd;
  int sockfd;
  
  // try to open socket to specified host and port
  sockfd = connectToServer(hostname, port);
  if (sockfd < 0) return -1;
  
  session->socket_fd = sockfd;
  
  // perform callback to get authentication information
  login = auth(0);
  pwd = auth(1);
  
  // initialize message object's static fields
  if (SGS_initMsg(session->msgBuf, SGS_LOGIN_REQUEST, SGS_APPLICATION_SERVICE) < 0) {
    close(sockfd);
    return -1;
  }
  
  // add "login" field to message
  if (SGS_addFixedMsgContent(session->msgBuf, (uint8_t*)login, strlen(login)) < 0) {
    close(sockfd);
    return -1;
  }
  
  // add "password" field to message
  if (SGS_addFixedMsgContent(session->msgBuf, (uint8_t*)pwd, strlen(pwd)) < 0) {
    close(sockfd);
    return -1;
  }
  
  // send message to server
  if (writeMsg(session, session->msgBuf) < 0) {
    close(sockfd);
    return -1;
  }
  
  return 0;
}

/*
 * function: SGS_logout()
 *
 * Sends a logout request to the server (if force == 0) or forcibly drops the
 *  connection to the server (if force != 0).
 *
 * args:
 *   session: pointer to the current user session
 *     force: whether to forcibly drop the connection rather than (gracefully) request
 *            a logout from the server
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
*/
int SGS_logout(SGS_Session *session, const int force) {
  if (SGS_isConnected(session) == 0) {
    // error: socket is not connected
    errno = ENOTCONN;
    return -1;
  }
  
  if (force) {
    disconnect(session);
    return 0;
  }
  else {
    // initialize message object's static fields
    if (SGS_initMsg(session->msgBuf, SGS_LOGOUT_REQUEST, SGS_APPLICATION_SERVICE) < 0) return -1;
    
    // send message to server
    if (writeMsg(session, session->msgBuf) < 0) return -1;
    
    return 0;
  }
}

/*
 * function: SGS_receive()
 *
 * Attempts to perform a non-blocking read on the socket connection to the server.
 *  Received data is buffered until a complete message is received, at which time
 *  the message is parsed and processed accordingly.
 *
 * args:
 *   session: pointer to the current user session
 *   timeout: amount of time (in milliseconds) to wait when checking the socket
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
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
  
  if (session->inbuf_bytes == sizeof(session->inbuf)) {
    // ran out of room!
    errno = ENOBUFS;
    return -1;
  }
  
  result = read(session->socket_fd, session->inbuf, sizeof(session->inbuf) - session->inbuf_bytes);
  
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
    if (consumeData(session) == -1) return -1;
    
    return 0;
  }
}

/*
 * function: SGS_sessionSend()
 *
 * Sends a message directly to the server (i.e. not on a channel).  This is oftentimes
 *  used to implement application-specific messaging protocol.
 *
 * args:
 *   session: pointer to the current user session
 *      data: array of message payload to send
 *   datalen: length of data array
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int SGS_sessionSend(SGS_Session *session, const uint8_t *data, const uint16_t datalen) {
  if (SGS_initMsg(session->msgBuf, SGS_SESSION_MESSAGE, SGS_APPLICATION_SERVICE) == -1) return -1;
  
  // add sequence number to message
  if (SGS_addArbMsgContent(session->msgBuf, (uint8_t*)(&session->seqNum), 8) == -1) return -1;
  session->seqNum++;
  
  // add data to message
  if (datalen > 0) {
    if (SGS_addFixedMsgContent(session->msgBuf, data, datalen) == -1) return -1;
  }
  
  if (SGS_isConnected(session) == 0) {
    // error: socket is not connected
    errno = ENOTCONN;
    return -1;
  }
  
  if (writeMsg(session, session->msgBuf) < 0)
    return -1;
  else
    return 0;
}

/*
 * registration functions for event callbacks:
 */

/*
 * parameters to callback() function:
 *  1) ID of channel that was joined
 */
void SGS_regChannelJoinedCallback(SGS_Session *session, void (*callback)(SGS_ID*)) {
  session->channelJoinedF = callback;
}

/*
 * parameters to callback() function:
 *  1) ID of channel that was left
 */
void SGS_regChannelLeftCallback(SGS_Session *session, void (*callback)(SGS_ID*)) {
  session->channelLeftF = callback;
}

/* 
 * parameters to callback() function:
 *  1) ID of channel that message was received on
 *  2) ID of sender
 *  3) byte-array containing message
 *  4) length of message byte-array
 */
void SGS_regChannelRecvMsgCallback(SGS_Session *session, void (*callback)(SGS_ID*, SGS_ID*, uint8_t*, uint16_t)) {
  session->channelRecvMsgF = callback;
}

/*
 * parameters to callback() function:   (none)
 */
void SGS_regDisconnectedCallback(SGS_Session *session, void (*callback)()) {
  session->disconnectedF = callback;
}

/*
 * parameters to callback() function:   (none)
 */
void SGS_regLoggedInCallback(SGS_Session *session, void (*callback)()) {
  session->loggedInF = callback;
}

/*
 * parameters to callback() function:
 *  1) error message
 *  2) length of error message
 */
void SGS_regLoginFailedCallback(SGS_Session *session, void (*callback)(uint8_t*, uint16_t)) {
  session->loginFailedF = callback;
}

/*
 * parameters to callback() function:   (none)
 */
void SGS_regReconnectedCallback(SGS_Session *session, void (*callback)()) {
  session->reconnectedF = callback;
}

/*
 * parameters to callback() function:
 *  1) message received from server
 *  2) length of message
 */
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
 * Handles opening a socket and connecting it to a specified server.
 *
 * args:
 *  hostname: name of the host to connect to in string format
 *      port: network port to connect to on the remote host
 *
 * returns:
 *   >0: success (return value is a valid file descriptor)
 *   -1: failure (errno is set to specific error code)
 */
static int connectToServer(const char *hostname, const int port) {
  int sockfd, ioflags;
  struct hostent *server;
  struct sockaddr_in serv_addr;
  
  // open the socket (not connected yet)
  // note: alternative SOCK_DGRAM
  sockfd = socket(AF_INET, SOCK_STREAM, 0);
  if (sockfd < 0) return -1;
  
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
  if (connect(sockfd, (const struct sockaddr*) &serv_addr, sizeof(serv_addr)) < 0) return -1;
  
  // I'm not sure if fnctl sets errno on errors, so I'll reset it to 0 here so that at least it won't
  //  return an incorrect (old) error
  errno = 0;
  
  // set non-blocking mode
  if ((ioflags = fcntl(sockfd, F_GETFL, 0)) == -1) return -1;
  if (fcntl(sockfd, F_SETFL, ioflags | O_NONBLOCK) == -1) return -1;
  
  return sockfd;
}

/*
 * function: consumeData()
 *
 * Reads (and removes) chunks of data from the session's in-buffer that comprise complete
 *  messages and passes them on for processing.  Stops once the data remaining in the in-
 *  buffer comprises only part of a message (or there is no data remaining).
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 *
 * TODO - if an error occurs in this function, the session buffers are left in a bad state
 *  (I think the message will be reread?)
 */
static int consumeData(SGS_Session *session) {
  int8_t result;
  uint32_t index = 0;
  uint32_t remaining;
  
  while (index < session->inbuf_bytes) {
    result = SGS_deserializeMsg(session->msgBuf, session->inbuf + index, session->inbuf_bytes);
    if (result == -1) return -1;
    if (result == -2) break;  // data comprises only a partial message
    
    // else, full message was deserialized from buffer and read into msgBuf
    index += result;
    
    if (processMessage(session, session->msgBuf) == -1) return -1;
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
 * function: disconnect()
 *
 * Closes the network connection of a session.
 */
static void disconnect(SGS_Session *session) {
  close(session->socket_fd);
  session->socket_fd = 0;
}


/*
 * function: processMessage()
 *
 * Examines the fields of the specified message and processes accordingly (typically
 *  by calling the appropriate callback function, if registered).
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
      result = SGS_initCompactIdFromCompressed(msg->data, msg->data_len, session->session_id);
      if (result == -1) return -1;
      
      // field 2: reconnection-key (compact-id format)
      result = SGS_initCompactIdFromCompressed(msg->data + result, msg->data_len - result, session->reconnect_key);
      if (result == -1) return -1;
      
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
      result = SGS_initCompactIdFromCompressed(msg->data + namelen + 2, msg->data_len - namelen - 2, &channelId);
      if (result == -1) return -1;
      
      result = SGS_putChannelIfAbsent(session->channel_list, &channelId, (char*)msg->data + 2, (int)namelen);
      if (result == -1) return -1;
      
      if (result == 1 && session->channelJoinedF != NULL)
	session->channelJoinedF(&channelId);
      
      return 0;
      
    case SGS_CHANNEL_LEAVE:
      // field 1: channel-id (compact-id format)
      result = SGS_initCompactIdFromCompressed(msg->data, msg->data_len, &channelId);
      if (result == -1) return -1;
      
      result = SGS_removeChannel(session->channel_list, &channelId);
      // note: currently we do NOT abort if this function returns -1 (indicating that the specified channel
      //  does not appear in the channel-list)
      
      if (result == 0 && session->channelLeftF != NULL)
	session->channelLeftF(&channelId);
      
      return 0;
      
    case SGS_CHANNEL_MESSAGE:
      // field 1: channel-id (compact-id format)
      result = SGS_initCompactIdFromCompressed(msg->data, msg->data_len, &channelId);
      if (result == -1) return -1;
      
      // field 2: sequence number (8 bytes)
      // TODO next 8 bytes are a sequence number that is currently ignored
      offset = result + 8;
      
      // field 3: session-id of sender (compact-id format)
      result = SGS_initCompactIdFromCompressed(msg->data + offset, msg->data_len - offset, &senderId);
      if (result == -1) return -1;
      
      offset += result;
      
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
 * function: readLenHeader()
 *
 * Reads two bytes from data argument and interprets them as a 2-byte integer field,
 *  returning the result.
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
 * Writes a message to a file descriptor (typically a socket).
 *
 * args:
 *   session: pointer to the current user session
 *       msg: the message to send
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int writeMsg(SGS_Session *session, const SGS_Message *msg) {
  int msglen, msgindex = 0, written = 0;
  
  msglen = SGS_serializeMsg(msg, session->outbuf, SESSION_BUFSIZE);
  if (msglen < 0) return -1;
  
  while (msgindex < msglen) {
    // note pointer arithmetic
    written = write(session->socket_fd, session->outbuf + written, (msglen - written));
    if (written < 0) return written;
    
    msgindex += written;
  }
  
  // flush file descriptor
  fsync(session->socket_fd);
  
  return 0;
}
