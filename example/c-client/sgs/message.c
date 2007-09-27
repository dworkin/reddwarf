/*
 * Copyright (c) 2007, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This file provides an implementation of functions relating to network
 * messages.  Implements functions declared in private/message.h.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return 0 upon
 *  success and -1 upon failure, while also setting errno to the specific error
 *  code.
 */

#include "sgs/config.h"
#include "sgs/id.h"
#include "sgs/error_codes.h"
#include "sgs/private/message.h"
#include "sgs/protocol.h"

/* for sgs_id_impl_write() */
#include "sgs/private/id_impl.h"

static void update_msg_len(sgs_message *pmsg);

/*
 * sgs_msg_add_arb_content()
 */
int sgs_msg_add_arb_content(sgs_message *pmsg, const uint8_t *content,
    size_t clen)
{
    size_t new_size = pmsg->len + clen;
  
    /** check that this won't make the message too long */
    if (new_size > SGS_MSG_MAX_LENGTH) {
        errno = EMSGSIZE;
        return -1;
    }
  
    /** check that this message has enough room allocated */
    if (new_size > pmsg->capacity) {
        errno = ENOBUFS;
        return -1;
    }
  
    /** copy the content over */
    memcpy(pmsg->buf + pmsg->len, content, clen);
  
    /** update the message size fields (both in the struct and in the data) */
    pmsg->len += clen;
    update_msg_len(pmsg);
  
    return 0;
}

/*
 * sgs_msg_add_fixed_content()
 */
int sgs_msg_add_fixed_content(sgs_message *pmsg, const uint8_t *content,
    size_t clen)
{
    size_t new_size = pmsg->len + clen + 2;  /** 2 bytes for length field */
    uint16_t _uint16_tmp;
  
    if (clen > SGS_MSG_MAX_LENGTH) {
        errno = SGS_ERR_SIZE_ARG_TOO_LARGE;
        return -1;
    }
  
    /** check that this won't make the message too long */
    if (new_size > SGS_MSG_MAX_LENGTH) {
        errno = EMSGSIZE;
        return -1;
    }
  
    /** check that this message has enough room allocated */
    if (new_size > pmsg->capacity) {
        errno = ENOBUFS;
        return -1;
    }
  
    /** copy the content's length over */
    _uint16_tmp = htons(clen);
    memcpy(pmsg->buf + pmsg->len, &_uint16_tmp, 2);
  
    /** copy the content over */
    memcpy(pmsg->buf + pmsg->len + 2, content, clen);
  
    /** update the message size fields (both in the struct and in the data) */
    pmsg->len += clen + 2;
    update_msg_len(pmsg);
  
    return 0;
}

/*
 * sgs_msg_add_id()
 */
int sgs_msg_add_id(sgs_message *msg, const sgs_id *id) {
    int result = sgs_id_impl_write(id, msg->buf + msg->len,
        msg->capacity - msg->len);
    
    if (result == -1) return -1;
    
    msg->len += result;
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
int sgs_msg_deserialize(sgs_message* pmsg, uint8_t *buffer, size_t buflen) {
    uint32_t net_len;
    
    /** read message-length-field (first 4 bytes) */
    net_len = *((uint32_t*)buffer);
    pmsg->len = ntohl(net_len);
  
    /** account for the 4-bytes holding the length itself */
    pmsg->len += 4;
  
    /** check if buffer is long enough to contain this whole message. */
    if (buflen < pmsg->len) {
        errno = EINVAL;
        return -1;
    }
  
    pmsg->buf = buffer;
    pmsg->capacity = buflen;
  
    /** invalid message: too big */
    if (pmsg->len > SGS_MSG_MAX_LENGTH) {
        errno = EMSGSIZE;
        return -1;
    }
  
    return pmsg->len;
}

/*
 * sgs_msg_get_bytes()
 */
const uint8_t *sgs_msg_get_bytes(const sgs_message *pmsg) {
    return pmsg->buf;
}

/*
 * sgs_msg_get_data()
 */
const uint8_t *sgs_msg_get_data(const sgs_message *pmsg) {
    return pmsg->buf + 7;
}

/*
 * sgs_msg_get_datalen()
 */
size_t sgs_msg_get_datalen(const sgs_message *pmsg) {
    return pmsg->len - 7;
}

/*
 * sgs_msg_get_opcode()
 */
uint8_t sgs_msg_get_opcode(const sgs_message *pmsg) {
    return pmsg->buf[6];
}

/*
 * sgs_msg_get_service()
 */
uint8_t sgs_msg_get_service(const sgs_message *pmsg) {
    return pmsg->buf[5];
}

/*
 * sgs_msg_get_size()
 */
size_t sgs_msg_get_size(const sgs_message *pmsg) {
    return pmsg->len;
}

/*
 * sgs_msg_get_version()
 */
uint8_t sgs_msg_get_version(const sgs_message *pmsg) {
    return pmsg->buf[4];
}

/*
 * sgs_msg_create()
 */
int sgs_msg_init(sgs_message* pmsg, uint8_t* buffer, size_t buflen,
    sgs_opcode opcode, sgs_service_id service_id)
{
    /** Buffer is too small to hold any messages (even w/o any payload). */
    if (buflen < 7) {
        errno = ENOBUFS;
        return -1;
    }

    pmsg->buf = buffer;
    pmsg->capacity = buflen;
    pmsg->len = 7;
  
    pmsg->buf[4] = SGS_MSG_VERSION;
    pmsg->buf[5] = service_id;
    pmsg->buf[6] = opcode;
    update_msg_len(pmsg);
  
    return 0;
}

#ifndef NDEBUG
void sgs_msg_dump(const sgs_message* msg) {
    size_t i;

    for (i = 0; i < msg->len; ++i) {
        printf("  %2.2x", msg->buf[i]);
        if ((i % 16) == 0)
            printf("\n");
    }
    if ((i % 16) != 1)
        printf("\n");
}
#else /* NDEBUG */
void sgs_msg_dump(const sgs_message* msg) { }
#endif /* NDEBUG */

/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */

/*
 * update_msg_len()
 */
static void update_msg_len(sgs_message *pmsg) {
    uint32_t _uint32_tmp = htonl(pmsg->len - 4);
    memcpy(pmsg->buf, &_uint32_tmp, 4);
}
