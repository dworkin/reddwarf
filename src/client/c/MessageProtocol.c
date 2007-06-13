/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: June 1, 2007
 *
 * This file provides functions relating to the Sun Gaming Server (SGS) wire protocol.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "MessageProtocol.h"

/*
 * function: SGS_addArbMsgContent()
 *
 * Adds an "arbitrary" chunk of data to an existing SGS message.
 *
 * args:
 *      msg: the SGS_Message to add data to; it may have any amount of optional data already
 *  content: byte array containing the content to add
 *     clen: length of the content array
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int SGS_addArbMsgContent(SGS_Message *msg, const uint8_t *content, const uint16_t clen) {
  // read current payload length; if we add this content, the payload length will increase by clen
  uint32_t newDataLen = msg->data_len + clen;
  
  // check that this data isn't too long
  if (newDataLen > SGS_MAX_MSG_DATA_LENGTH) {
    errno = EMSGSIZE;
    return -1;
  }
  
  // check that this SGS_Message has enough room allocated
  if (newDataLen > msg->buf_size) {
    errno = ENOBUFS;
    return -1;
  }
  
  // copy the content over (note pointer arithmetic)
  memcpy(msg->data + msg->data_len, content, clen);
  
  // update the data_len field with the new total length
  msg->data_len += clen;
  
  return 0;
}

/*
 * function: SGS_addFixedMsgContent()
 *
 * Adds a byte-array of data to an existing SGS message that is preceded by a 2-byte field
 *  containing the length of the data being added.
 *
 * args:
 *      msg: the SGS_Message to add data to; it may have any amount of optional data already
 *  content: byte array containing the content to add
 *     clen: length of the content array
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int SGS_addFixedMsgContent(SGS_Message *msg, const uint8_t *content, const uint16_t clen) {
  // read current payload length; if we add this content, the payload length will increase by clen+2
  uint32_t newDataLen = msg->data_len + clen + 2;
  
  // check that this data isn't too long
  if (newDataLen > SGS_MAX_MSG_DATA_LENGTH) {
    errno = EMSGSIZE;
    return -1;
  }
  
  // check that this SGS_Message has enough room allocated
  if (newDataLen > msg->buf_size) {
    errno = ENOBUFS;
    return -1;
  }
  
  // copy the content's length over
  msg->data[msg->data_len] = clen / 256;
  msg->data[msg->data_len + 1] = clen % 256;
  msg->data_len += 2;
  
  // copy the content over (note pointer arithmetic)
  memcpy(msg->data + msg->data_len, content, clen);
  
  // update the data_len field with the new total length
  msg->data_len += clen;
  
  return 0;
}

/*
 * function: SGS_createMsg()
 *
 * Creates an empty SGS message.  Any struct created (returned) by this function should be destroyed 
 *  by calling SGS_destroyMsg() before it goes out of scope to avoid memory leaks.
 *
 * args:
 *    bufsize: maximum buffer size for optional data (how much memory to allocate)
 *
 * returns:
 *   On any error, errno is set to the specfific error code and NULL is returned.  Otherwise,
 *    a pointer to a newly allocated SGS_Message is returned.
 */
SGS_Message *SGS_createMsg(const size_t bufsize) {
  SGS_Message *msg;
  
  msg = (SGS_Message *)malloc(sizeof(SGS_Message));
  
  if (msg == NULL) {
    errno = ENOMEM;
    return NULL;
  }
  
  msg->data = (uint8_t *)malloc(bufsize);
  msg->buf_size = bufsize;
  // other fields are set when SGS_initMsg() is called
  
  // check that malloc() succeeded
  if (msg->data == NULL) {
    // have to deallocate msg since we aren't returning it!
    free(msg);
    
    errno = ENOMEM;
    return NULL;
  }
  
  return msg;
}

/*
 * function: SGS_deserializeMsg()
 *
 * Creates an SGS message struct from a byte array representation.
 *
 * args:
 *     msg: the SGS_Message struct to fill with data
 *  buffer: the byte array to deserialize from
 *  buflen: total length of the buffer (if its too long, that's ok - extra bytes are ignored)
 *
 * returns:
 *   >0: success (returned value is the number of bytes of buffer that were read)
 *   -1: failure (errno is set to specific error code)
 *   -2: failure (buffer does not contain a complete SGS_Message; need more data)
 */
int SGS_deserializeMsg(SGS_Message *msg, const uint8_t *buffer, const size_t buflen) {
  int msgLen, i;
  void *ptmp;
  
  // buffer is not possibly long enough to contain a complete SGS_Message
  if (buflen < 7)
    return -2;
  
  // read message-length-field (first 4 bytes)
  msgLen = 0;
  
  for (i=0; i < 4; i++)
    msgLen += msgLen*256 + buffer[i];
  
  // the total number of bytes that comprise this message is (msgLen + 4)
  if (buflen < msgLen + 4)
    // not enough data in buffer to complete this message
    return -2;
  
  // invalid message: too big
  if (msgLen > SGS_MAX_MESSAGE_LENGTH) {
    errno = EMSGSIZE;
    return -1;
  }
  
  msg->version_id = buffer[4];
  msg->service_id = buffer[5];
  msg->op_code = buffer[6];
  msg->data_len = msgLen - 3;
  
  if (msg->data_len > buflen) {
    // data buffer previously allocated for this SGS_Message is too small; replace it with something bigger
    
    // save original array so we can roll back if memory allocation of new array fails
    ptmp = msg->data;
    
    msg->data = (uint8_t *)malloc(msg->data_len);
    
    if (msg->data == NULL) {
      msg->data = ptmp;
      errno = ENOMEM;
      return -1;
    }
    
    // else, memory allocation succeeded; delete old array
    free(ptmp);
    msg->buf_size = msg->data_len;
  }
  
  // note pointer arithmetic on source
  memcpy(msg->data, (buffer + 7), msg->data_len);
  
  return (7 + msg->data_len);
}

/*
 * function: SGS_destroyMsg()
 *
 * Takes care of any/all cleanup when an SGS_Message struct is going to be thrown away.
 *
 * args:
 *   msg: pointer to the SGS_Message to destroy
 *
 * returns: nothing
 */
void SGS_destroyMsg(SGS_Message *msg) {
  // first deallocate the "data" field
  free(msg->data);
  
  // then deallocate the SGS_Message struct itself
  free(msg);
}

/*
 * function: SGS_initMsg()
 *
 * Initializes the fields of an SGS message without any optional content.
 *
 * args:
 *     opCode: operation code for this message
 *  serviceId: service id for this message
 *        msg: pointer to the SGS_Message to initialize
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int SGS_initMsg(SGS_Message *msg, const SGS_OpCode opCode, const SGS_ServiceId serviceId) {
  msg->version_id = SGS_VERSION;
  msg->service_id = serviceId;
  msg->op_code = opCode;
  msg->data_len = 0;
  // do not touch msg->data or msg->buf_size
  
  return 0;
}

/*
 * function: SGS_serializeMsg()
 *
 * Writes a SGS message to a byte array representation.
 *
 * args:
 *     msg: the SGS_Message struct to serialize
 *  buffer: the byte array to serialize into
 *  buflen: the length of the buffer (is a longer buffer is required, the function fails)
 *
 * returns:
 *   >0: success (returned value is the number of bytes of buffer that were used)
 *   -1: failure (errno is set to specific error code)
 */
int SGS_serializeMsg(const SGS_Message *msg, uint8_t *buffer, const size_t buflen) {
  uint32_t msglen = msg->data_len + 3;
  
  // buffer is not big enough to hold this message in deserialized format
  if (buflen < (4 + msglen)) {
    errno = ENOBUFS;
    return -1;
  }
  
  // write message-length field (4 bytes) into buffer
  buffer[0] = msglen / 16777216L;        // note: 16777216 = 256*256*256
  buffer[1] = (msglen / 65536) & 2556;   // note: 65536 = 256*256
  buffer[2] = (msglen / 256) & 2556;
  buffer[3] = msglen & 255;
  
  buffer[4] = msg->version_id;
  buffer[5] = msg->service_id;
  buffer[6] = msg->op_code;
  
  // note pointer arithmetic on destination
  memcpy(buffer + 7, msg->data, msg->data_len);
  
  return msg->data_len + 7;
}
