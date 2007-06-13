/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: June 1, 2007
 *
 * This file provides functions relating to the Sun Gaming Server (SGS) wire protocol.
 *
 */

#ifndef _MESSAGEPROTOCOL_H
#define _MESSAGEPROTOCOL_H  1

/*
 * INCLUDES
 */
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>  // included for malloc(), free()
#include <string.h>  // included for memcpy()
#include "SimpleSgsProtocol.h"

/* TODO: According to http://docs.hp.com/en/B9106-90012/inttypes.5.html stdint.h may not define
   all interger types that are are assuming it does; a more robust implementation would check
   whether each type is defined and define it if not. */

/*
 * TYPEDEFS
 */
typedef struct {
  uint8_t version_id;
  uint8_t service_id;
  uint8_t op_code;
  uint8_t *data;    // buffer for optional message data
  size_t data_len;  // number of bytes of data written into the "data" field
  size_t buf_size;  // total size of buffer allocated and assigned to "data" field
} SGS_Message;


/*
 * FUNCTION DECLARATIONS
 * (implementations are in MessageProtocol.c)
 */
int SGS_addArbMsgContent(SGS_Message *msg, const uint8_t *content, const uint16_t clen);
int SGS_addFixedMsgContent(SGS_Message *msg, const uint8_t *content, const uint16_t clen);
SGS_Message *SGS_createMsg(const size_t bufsize);
int SGS_deserializeMsg(SGS_Message *msg, const uint8_t *buffer, const size_t buflen);
void SGS_destroyMsg(SGS_Message *msg);
int SGS_initMsg(SGS_Message *msg, const SGS_OpCode opCode, const SGS_ServiceId serviceId);
int SGS_serializeMsg(const SGS_Message *msg, uint8_t *buffer, const size_t buflen);

#endif
