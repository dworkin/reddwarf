/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: June 4, 2007
 *
 * This file provides functions relating to the client's side of a session with
 *  a Sun Gaming Server (SGS).  Its functionality is similar to that in the java
 *  class com.sun.sgs.client.simple.SimpleClient.
 */

#ifndef _SERVERSESSION_H
#define _SERVERSESSION_H 1

/*
 * INCLUDES
 */
#include <stdint.h>
#include <string.h>   // included for strlen(), memcpy(), memmove(), memset()
#include <netdb.h>    // included for gethostbyname()
#include <fcntl.h>    // included for F_GETFL, F_SETFL and O_NONBLOCK constants
#include <unistd.h>   // included for write(), fsync()
#include "Channels.h"
#include "CompactId.h"
#include "MessageProtocol.h"

/*
 * DEFINES
 */
#define SESSION_BUFSIZE   1024  // bytes

// customer errors
#define ILLEGAL_STATE_ERR  180  // the code has reached a state that it shouldn't
#define BAD_VERSION_ERR    181  // a message was received with an invalid version-id field
#define BAD_SERVICE_ERR    182  // a message was received with an unrecognized service-id field
#define BAD_OPCODE_ERR     183  // a message was received with an unrecognized opcode field

#define IS_CUSTOM_ERR(x) (x == ILLEGAL_STATE_ERR || x == BAD_VERSION_ERR || x == BAD_SERVICE_ERR || x == BAD_OPCODE_ERR)

/*
 * TYPEDEFS
 */
typedef struct {
  int socket_fd;             // file descriptor of the socket to the server
  SGS_ID *session_id;
  SGS_ID *reconnect_key;
  SGS_ChannelList *channel_list;
  uint8_t expecting_disconnect;
  uint64_t seqNum;
  
  /*
   * Rather than redeclaring memory every time we want to send or receive a message, we just declare
   *  re-usable global buffers once.  Note that this depends on synchronous (e.g. single-threaded)
   *  code so that there aren't race conditions on either buffer.
   */
  uint8_t inbuf[SESSION_BUFSIZE];
  uint8_t outbuf[SESSION_BUFSIZE];
  size_t inbuf_bytes;  // number of bytes read into inbuf[] so far
  
  // buffer used during performing callbacks when messages are received
  SGS_Message *msgBuf;
  
  // function pointers
  void (*channelJoinedF)(SGS_ID*);
  void (*channelLeftF)(SGS_ID*);
  void (*channelRecvMsgF)(SGS_ID*, SGS_ID*, uint8_t*, uint16_t);
  void (*disconnectedF)();
  void (*loggedInF)();
  void (*loginFailedF)(uint8_t*, uint16_t);
  void (*reconnectedF)();
  void (*recvMessageF)(uint8_t*, uint16_t);
} SGS_Session;


/*
 * GLOBAL VARIABLES
 */


/*
 * FUNCTION DECLARATIONS
 * (implementations are in ServerSession.c)
 */
int SGS_channelSend(SGS_Session *session, SGS_ID *channelId, const uint8_t *data, const uint16_t datalen, SGS_ID *recipients[], const uint16_t reciplen);
SGS_Session *SGS_createSession(size_t msgBufSize);
void SGS_destroySession(SGS_Session *session);
SGS_ChannelList *SGS_getChannelList(const SGS_Session *session);
SGS_ID *SGS_getReconnectKey(const SGS_Session *session);
SGS_ID *SGS_getSessionId(const SGS_Session *session);
inline int SGS_isConnected(const SGS_Session *session);
int SGS_login(const char *hostname, const int port, char* (*auth)(uint8_t), SGS_Session *session);
int SGS_logout(SGS_Session *session, const int force);
int SGS_receive(SGS_Session *session, const uint32_t timeout);
int SGS_sessionSend(SGS_Session *session, const uint8_t *data, const uint16_t datalen);

// functions for registering event callbacks:
inline void SGS_regChannelJoinedCallback(SGS_Session *session, void (*callback)(SGS_ID*));
inline void SGS_regChannelLeftCallback(SGS_Session *session, void (*callback)(SGS_ID*));
inline void SGS_regChannelRecvMsgCallback(SGS_Session *session, void (*callback)(SGS_ID*, SGS_ID*, uint8_t*, uint16_t));
inline void SGS_regDisconnectedCallback(SGS_Session *session, void (*callback)());
inline void SGS_regLoggedInCallback(SGS_Session *session, void (*callback)());
inline void SGS_regLoginFailedCallback(SGS_Session *session, void (*callback)(uint8_t*, uint16_t));
inline void SGS_regReconnectedCallback(SGS_Session *session, void (*callback)());
inline void SGS_regRecvMsgCallback(SGS_Session *session, void (*callback)(uint8_t*, uint16_t));

#endif
