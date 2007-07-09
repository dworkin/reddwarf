/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides an implementation of functions relating to network
 * messages.  Implements functions declared in sgs_message.h.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return 0 upon
 *  success and -1 upon failure, while also setting errno to the specific error
 *  code.
 */

#include <arpa/inet.h>
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "sgs_error_codes.h"
#include "sgs_message.h"
#include "sgs_wire_protocol.h"

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static void update_msg_len(sgs_message *pmsg);

/*
 * sgs_msg_add_arb_content()
 */
int sgs_msg_add_arb_content(sgs_message *pmsg, const uint8_t *content,
    size_t clen)
{
    size_t new_size = pmsg->size + clen;
  
    /** check that this won't make the message too long */
    if (new_size > SGS_MSG_MAX_LENGTH) {
        errno = EMSGSIZE;
        return -1;
    }
  
    /** check that this message has enough room allocated */
    if (new_size > pmsg->buflen) {
        errno = ENOBUFS;
        return -1;
    }
  
    /** copy the content over */
    memcpy(pmsg->buf + pmsg->size, content, clen);
  
    /** update the message size fields (both in the struct and in the data) */
    pmsg->size += clen;
    update_msg_len(pmsg);
  
    return 0;
}

/*
 * sgs_msg_add_fixed_content()
 */
int sgs_msg_add_fixed_content(sgs_message *pmsg, const uint8_t *content,
    size_t clen)
{
    size_t new_size = pmsg->size + clen + 2;  /** 2 bytes for length field */
    uint16_t _uint16_tmp;
  
    if (clen > UINT16_MAX) {
        errno = SGS_ERR_SIZE_ARG_TOO_LARGE;
        return -1;
    }
  
    /** check that this won't make the message too long */
    if (new_size > SGS_MSG_MAX_LENGTH) {
        errno = EMSGSIZE;
        return -1;
    }
  
    /** check that this message has enough room allocated */
    if (new_size > pmsg->buflen) {
        errno = ENOBUFS;
        return -1;
    }
  
    /** copy the content's length over */
    _uint16_tmp = htons(clen);
    memcpy(pmsg->buf + pmsg->size, &_uint16_tmp, 2);
  
    /** copy the content over */
    memcpy(pmsg->buf + pmsg->size + 2, content, clen);
  
    /** update the message size fields (both in the struct and in the data) */
    pmsg->size += clen + 2;
    update_msg_len(pmsg);
  
    return 0;
}

/*
 * sgs_msg_add_uint32()
 */
int sgs_msg_add_uint32(sgs_message *pmsg, uint32_t val) {
    uint32_t converted = htonl(val);
    return sgs_msg_add_arb_content(pmsg, (uint8_t*)&converted, 4);
}

/*
 * sgs_msg_deserialize()
 */
int sgs_msg_deserialize(sgs_message *pmsg, uint8_t *buffer, size_t buflen) {
    uint32_t *ptr;
  
    /** read message-length-field (first 4 bytes) */
    ptr = (uint32_t*)buffer;
    pmsg->size = ntohl(*ptr);
  
    /** account for the 4-bytes holding the length itself */
    pmsg->size += 4;
  
    /** check if buffer is long enough to contain this whole message. */
    if (buflen < pmsg->size) {
        errno = EINVAL;
        return -1;
    }
  
    pmsg->buf = buffer;
    pmsg->buflen = buflen;
  
    /** invalid message: too big */
    if (pmsg->size > SGS_MSG_MAX_LENGTH) {
        errno = EMSGSIZE;
        return -1;
    }
  
    return pmsg->size;
}

/*
 * sgs_msg_get_bytes()
 */
const uint8_t *sgs_msg_get_bytes(sgs_message *pmsg) {
    return pmsg->buf;
}

/*
 * sgs_msg_get_data()
 */
const uint8_t *sgs_msg_get_data(sgs_message *pmsg) {
    return pmsg->buf + 7;
}

/*
 * sgs_msg_get_datalen()
 */
size_t sgs_msg_get_datalen(sgs_message *pmsg) {
    return pmsg->size - 7;
}

/*
 * sgs_msg_get_opcode()
 */
uint8_t sgs_msg_get_opcode(sgs_message *pmsg) {
    return pmsg->buf[6];
}

/*
 * sgs_msg_get_service()
 */
uint8_t sgs_msg_get_service(sgs_message *pmsg) {
    return pmsg->buf[5];
}

/*
 * sgs_msg_get_size()
 */
size_t sgs_msg_get_size(sgs_message *pmsg) {
    return pmsg->size;
}

/*
 * sgs_msg_get_version()
 */
uint8_t sgs_msg_get_version(sgs_message *pmsg) {
    return pmsg->buf[4];
}

/*
 * sgs_msg_init()
 */
int sgs_msg_init(sgs_message *pmsg, uint8_t *buffer, size_t buflen,
    sgs_opcode opcode, sgs_service_id service_id)
{
    /** Buffer is too small to hold any messages (even w/o any payload). */
    if (buflen < 7) {
        errno = ENOBUFS;
        return -1;
    }
  
    pmsg->buf = buffer;
    pmsg->buflen = buflen;
    pmsg->size = 7;
  
    pmsg->buf[4] = SGS_MSG_VERSION;
    pmsg->buf[5] = service_id;
    pmsg->buf[6] = opcode;
    update_msg_len(pmsg);
  
    return 0;
}


/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */

/*
 * update_msg_len()
 */
static void update_msg_len(sgs_message *pmsg) {
    uint32_t _uint32_tmp = htonl(pmsg->size - 4);
    memcpy(pmsg->buf, &_uint32_tmp, 4);
}
