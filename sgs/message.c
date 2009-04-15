/*
 * Copyright (c) 2007 - 2009, Sun Microsystems, Inc.
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
#include "sgs/buffer.h"


static void update_msg_len(sgs_message *pmsg);


/*
 * sgs_msg_init()
 */
int sgs_msg_init(sgs_message* pmsg, uint8_t* buffer, size_t buflen,
    sgs_opcode opcode)
{
    /** Buffer is too small to hold any messages (even w/o any payload). */
    if (buflen < SGS_MSG_INIT_LEN) {
        errno = ENOBUFS;
        return -1;
    }

    if (buflen > SGS_MSG_MAX_LENGTH)
        return -1;

    pmsg->buf = buffer;
    pmsg->capacity = buflen;
    pmsg->len = 1;

    pmsg->buf[SGS_OPCODE_OFFSET] = opcode;
    update_msg_len(pmsg);

    return 0;
}


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

    /** copy the content over, accounting for the first two bytes for the length*/
    memcpy(pmsg->buf + pmsg->len + SGS_MSG_LENGTH_OFFSET, content, clen);

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

    /** copy the content's length over, and update the length */
    _uint16_tmp = htons(clen);
    memcpy(pmsg->buf + pmsg->len + SGS_MSG_LENGTH_OFFSET, &_uint16_tmp, 2);
    pmsg->len += 2;
    /** copy the content over, and update the length */
    memcpy(pmsg->buf + pmsg->len + SGS_MSG_LENGTH_OFFSET, content, clen);
    pmsg->len += clen;

    /*update the overall length of the message*/
    update_msg_len(pmsg);

    return 0;
}

/*
 * sgs_msg_add_id()
 * This will copy the length of the id, and then the contents of the id,
 * to the message field
 */
int sgs_msg_add_id(sgs_message *msg, const sgs_id *id, int add_length) {
    if (add_length != 0) {
        return sgs_msg_add_fixed_content(msg, sgs_id_get_bytes(id),
                sgs_id_get_byte_len(id));
    } else {
        return sgs_msg_add_arb_content(msg, sgs_id_get_bytes(id),
                sgs_id_get_byte_len(id));
    }
}

/*
 * sgs_msg_add_uint16
 */
int sgs_msg_add_uint16(sgs_message *pmsg, uint16_t val){
	uint16_t converted = htons(val);
	return sgs_msg_add_arb_content(pmsg, (uint8_t*)&converted, 2);
}

/*
 * sgs_msg_add_uint32()
 */
int sgs_msg_add_uint32(sgs_message *pmsg, uint32_t val) {
    uint32_t converted = htonl(val);
    return sgs_msg_add_arb_content(pmsg, (uint8_t*)&converted, 4);
}

/*
 * sgs_msg_add_string()
 */
int sgs_msg_add_string(sgs_message *pmsg, const char *content){
    int16_t stringlen;

    stringlen = strlen(content);
    return(sgs_msg_add_fixed_content(pmsg, (uint8_t *)content, stringlen));
}

/*
 * sgs_msg_deserialize()
 */
int sgs_msg_deserialize(sgs_message* pmsg, uint8_t *buffer, size_t buflen) {
    uint32_t net_len;

    /** read message-length-field (first 2 bytes). This is the payload
     *  length, not the length of the entire buffer*/
    net_len = *((uint16_t*)buffer);
    pmsg->len = ntohs(net_len);



    /** check if buffer is long enough to contain this whole message. */
    if (buflen < pmsg->len) {
        errno = EINVAL;
        return -1;
    }

    pmsg->buf = buffer;
    pmsg->capacity = buflen;

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
    return pmsg->buf + SGS_MSG_INIT_LEN;
}

/*
 * sgs_msg_get_datalen()
 */
uint16_t sgs_msg_get_datalen(const sgs_message *pmsg) {
    return pmsg->len;
}

/*
 * sgs_msg_get_opcode()
 */
uint8_t sgs_msg_get_opcode(const sgs_message *pmsg) {
    return pmsg->buf[SGS_OPCODE_OFFSET];
}

/*
 * sgs_msg_get_size()
 *
 */
uint16_t sgs_msg_get_size(const sgs_message *pmsg) {
    return pmsg->len + SGS_MSG_INIT_LEN;
}

/*
 * sgs_msg_read_uint16()
 */
int sgs_msg_read_uint16(const sgs_message *pmsg, const uint16_t start, uint16_t *result){
    if (start > pmsg->len){
        return -1;
    }
    memcpy(result, pmsg->buf + start, 2);
    *result = ntohs(*result);
    return 2;
}

/*
 * sgs_msg_read_uint32()
 */
int sgs_msg_read_uint32(const sgs_message *pmsg, const uint16_t start, uint32_t *result){
    if (start + 2 > pmsg->len){
        return -1;
    }
    memcpy(result, pmsg->buf + start, 4);
    *result = ntohl(*result);
    return 4;
}

/*
 * sgs_msg_read_id()
 */
int sgs_msg_read_id(const sgs_message *pmsg, const uint16_t start,
        int read_length, sgs_id **result) {
    uint16_t length;
    uint8_t *buffer;
    int incr, incr1;

    if (read_length != 0) {
        incr = sgs_msg_read_uint16(pmsg, start, &length);
        if (incr < 1)
            return -1;
    } else {
        length = (pmsg->len + SGS_MSG_INIT_LEN) - start;
        incr = 0;
    }

    incr1 = sgs_msg_read_bytes(pmsg, start + incr, &buffer, length);
    if (incr1 != length) {
        return -1;
    }

    *result = sgs_id_create(buffer, length);
    return (incr + incr1);
}
/*
 * sgs_msg_read_string
 */
int sgs_msg_read_string(const sgs_message *pmsg, const uint16_t start, char **result) {
    uint16_t strsize;
    int incr;

    incr = sgs_msg_read_uint16(pmsg, start, &strsize);
    if (incr < 0)
        return incr;

    *result = malloc(sizeof (char) * (strsize + 1));
    if (*result == NULL)
        return -1;
    memcpy(*result, pmsg->buf + start + incr, strsize);
    *((*result) + strsize) = '\0';

    return (strsize + incr);
}

/*
 * sgs_msg_read_bytes
 */
int sgs_msg_read_bytes(const sgs_message *pmsg, const uint16_t start, uint8_t **result, uint16_t count){
    int retcount;


    *result = malloc(count);
    if (*result == NULL){
        return -1;
    }
    retcount = ((pmsg->len + SGS_MSG_INIT_LEN) < (start + count ))?
        (pmsg->len + SGS_MSG_INIT_LEN) - start : count;
    memcpy(*result, pmsg->buf + start, retcount);
    return retcount;
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
    uint16_t _uint16_tmp = htons(pmsg->len);
    memcpy(pmsg->buf, &_uint16_tmp, 2);
}
