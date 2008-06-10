/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
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
 * This file provides declarations relating to network messages.
 */

#ifndef SGS_MESSAGE_H
#define SGS_MESSAGE_H  1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"

typedef struct {
    /* The buffer for this message. NOTE! Not owned by this struct. */
    uint8_t* buf;

    /* Buffer capacity. */
    size_t capacity;

    /* Message length. */
    size_t len;

} sgs_message;

#include "sgs/id.h"
#include "sgs/protocol.h"

/*
 * function: sgs_msg_add_arb_content()
 *
 * Adds an "arbitrary" chunk of data to an existing message, which means that a
 * 2-byte size field is NOT prepended to the data before it is added.
 *
 * args:
 *     pmsg: the message to add data to
 *  content: byte array containing the content to add
 *     clen: length of the content array
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_msg_add_arb_content(sgs_message* pmsg, const uint8_t* content,
    size_t clen);

/*
 * function: sgs_msg_add_fixed_content()
 *
 * Adds a byte-array of data to an existing message after prepending a 2-byte
 * size field to the data containing the length of the array.
 *
 * args:
 *     pmsg: the message to add data to
 *  content: byte array containing the content to add
 *     clen: length of the content array
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_msg_add_fixed_content(sgs_message* pmsg, const uint8_t* content,
    size_t clen);


/*
 * function: sgs_msg_add_id()
 *
 * Writes an sgs_id to an existing message.
 *
 * args:
 *     pmsg: pointer to the message to add data to
 *       id: pointer to the sgs_id to add to the message
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_msg_add_id(sgs_message* pmsg, const sgs_id* id);

/*
 * function: sgs_msg_add_uint32()
 *
 * Writes a 32-bit int to an existing message (useful for sequence numbers).
 *
 * args:
 *     pmsg: pointer to the message to add data to
 *      val: the 32-bit integer to add to the message
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_msg_add_uint32(sgs_message* pmsg, uint32_t val);

/*
 * function: sgs_msg_deserialize()
 *
 * Initializes a message from a byte-array.
 */
int sgs_msg_deserialize(sgs_message* pmsg, uint8_t* buffer, size_t buflen);

/*
 * function: sgs_msg_get_bytes()
 *
 * Returns a pointer to this message's byte-array representation.
 */
const uint8_t* sgs_msg_get_bytes(const sgs_message* pmsg);

/*
 * function: sgs_msg_get_data()
 * 
 * Returns a pointer to the start of this message's data payload.
 */
const uint8_t* sgs_msg_get_data(const sgs_message* pmsg);

/*
 * function: sgs_msg_get_datalen()
 * 
 * Returns the length of this message's data payload.
 */
size_t sgs_msg_get_datalen(const sgs_message* pmsg);

/*
 * function: sgs_msg_get_opcode()
 * 
 * Returns the current op-code of this message.
 */
uint8_t sgs_msg_get_opcode(const sgs_message* pmsg);

/*
 * function: sgs_msg_get_service()
 * 
 * Returns the current service-id of this message.
 */
uint8_t sgs_msg_get_service(const sgs_message* pmsg);

/*
 * function: sgs_msg_get_size()
 *
 * Returns the total length of this message.
 */
size_t sgs_msg_get_size(const sgs_message* pmsg);

/*
 * function: sgs_msg_get_version()
 * 
 * Returns the current version-ID of this message.
 */
uint8_t sgs_msg_get_version(const sgs_message* pmsg);

/*
 * function: sgs_msg_init()
 *
 * Initializes the fields of a message without any optional content.
 *
 * args:
 *        pmsg: pointer to the message to initialize
 *      buffer: the backing buffer to write/read to/from
 *      buflen: size of backing buffer
 *      opcode: operation code for this message
 *  service_id: service id for this message
 *
 * returns:
 *  NULL: failure (errno is set to specific error code)
 */
int sgs_msg_init(sgs_message* pmsg, uint8_t* buffer, size_t buflen,
    sgs_opcode opcode, sgs_service_id service_id);

/*
 * function: sgs_msg_dump()
 *
 * Dumps an sgs_msg to stdout (noop if NDEBUG defined).
 */
void sgs_msg_dump(const sgs_message* pmsg);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_MESSAGE_H */
