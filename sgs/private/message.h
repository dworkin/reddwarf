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
 * This file provides declarations relating to network messages. The functions
 * declared in this file are used to build messages sent to the server and 
 * decode messages sent from the server. The protocol used to define these
 * messages can be found in protocol.h, which also contains a number of
 * constants that are used to build and parse these messages.
 *
 * Messages are built by calling sgs_msg_init, passing in a pointer to the
 * message buffer, a pointer to the backing buffer, the size of the backing
 * buffer, and the opcode of the message to be constructed. The rest of the
 * message is constructed by calls to the sgs_msg_add* functions. These in 
 * turn are grounded in either sgs_msg_add_arb_content(), which will add the bytes
 * passed into the buffer, or sgs_msg_add_fixed_content, which will add the bytes
 * passed in prefixed with a two byte (unsigned short) length of the content.
 * Adding content will also update the size of the payload.
 *
 * Received messages are parsed by first calling sgs_msg_deserialize, which
 * determines the overall length of the incoming message and sets up the
 * sgs_message object that can be used to read the message. The buffer that
 * contains only the payload (that is, the opcode and any other content) can
 * be obtained by a call to sgs_msg_get_data, while the opcode can be obtained
 * by a call to sgs_msg_get_opcode. In all of the functions for getting 
 * information out of the buffer, the start point for reading the buffer
 * is passed to the reading function. 
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

    /* Message length. This includes the 2-byte payload length*/
    uint16_t len;

} sgs_message;

#include "sgs/id.h"
#include "sgs/protocol.h"

/*
 * function: sgs_msg_init()
 *
 * Initializes the fields of a message and sets the opcode of the message 
 * without adding any other optional content.
 * 
 * args:
 *        pmsg: pointer to the message to initialize
 *      buffer: the backing buffer to write/read to/from
 *      buflen: size of backing buffer
 *      opcode: operation code for this message
 *
 * returns:
 *  NULL: failure (errno is set to specific error code)
 */
int sgs_msg_init(sgs_message* pmsg, uint8_t* buffer, size_t buflen,
    sgs_opcode opcode);

/*
 * function: sgs_msg_add_arb_content()
 *
 * Adds an "arbitrary" chunk of data to an existing message, which means that a
 * 2-byte size field is NOT prepended to the data before it is added. The length
 * of the overall message is updated to reflect the addition of the content, 
 * and converted to a form that can be sent over the network.
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
 * size field to the data containing the length of the array. Updates the
 * length of the overall message to reflect the addition of the content and
 * the two bytes of the length field. The length is added in a form suitable
 * for sending over the network.
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
 * Writes an sgs_id to an existing message. The id is not interpreted in any
 * way, and can be of any size. If add_length is not 0, the result is that a 
 * two-byte size (in a form appropriate for sending on the network) is added to 
 * the data buffer, followed by the bytes of the id. If add_length is 0, only the 
 * bytes of the id are written to the stream. In either case, the overall length 
 * of the message is updated to reflect the addition of the content that was added.
 *
 * args:
 *     pmsg: pointer to the message to add data to
 *       id: pointer to the sgs_id to add to the message
 *     add_length: if 0, only the bytes of the id are added to the stream,
 *              otherwise, both the length and the bytes of the id are added
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_msg_add_id(sgs_message* pmsg, const sgs_id* id, int add_length);
	
/*
 * function: sgs_msg_add_uint16()
 * 
 * Writes a 16-bit int to an existing message (useful for lengths). The int
 * is first converted to a form appropriate for sending on the network, and
 * is then added to the message buffer. The overall length of the buffer is
 * increased by two.
 *
 * returns 0 is successful, -1 on failure
 */
int sgs_msg_add_uint16(sgs_message *msg, uint16_t val);

/*
 * function: sgs_msg_add_uint32()
 *
 * Writes a 32-bit int to an existing message (useful for sequence numbers). The
 * int is first converted to a form appropriate for sending on the network, and
 * is then appended to the buffer. The overall length of the buffer is then 
 * increased by four.
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
 * function: sgs_msg_add_string()
 *
 * Writes a string to an existing message. It will first write the length of 
 * the string, then the contents of the character array, stripping off the
 * null terminating byte. It will then update the overall length of the message
 * payload by the length of the string less the null termination plus two bytes 
 * (for the length). The length will be written in a form that is appropriate
 * for transmission over the network.
 *
 *  args:
 *      pmsg: pointer to the message to which the string is added
 *      content: pointer to the string to be added
 *
 *  returns:
 *      0: success
 *     -1: failure
 *
 */
int sgs_msg_add_string(sgs_message* pmsg, const char* content);

/*
 * function: sgs_msg_deserialize()
 *
 * Initializes a message from a byte-array. The function first determines the
 * overall length of the message by reading the first two bytes of the array
 * and converting those bytes into an unsigned 16-bit integer. Using this length,
 * the function then determines if the buffer suppled has sufficient size to
 * contain the rest of the message/
 */
int sgs_msg_deserialize(sgs_message* pmsg, uint8_t* buffer, size_t buflen);

/*
 * function: sgs_msg_get_bytes()
 *
 * Returns a pointer to this message's byte-array representation. This pointer
 * will include the parts of the buffer used to indicate the overall size
 * of the buffer.
 */
const uint8_t* sgs_msg_get_bytes(const sgs_message* pmsg);

/*
 * function: sgs_msg_get_data()
 * 
 * Returns a pointer to the start of this message's data payload. This pointer
 * will not include the parts of the buffer used to indicate the overall size
 * of the buffer.
 */
const uint8_t* sgs_msg_get_data(const sgs_message* pmsg);

/*
 * function: sgs_msg_get_datalen()
 * 
 * Returns the length of this message's data payload. This is determined from 
 * the first two bytes of the message buffer, and contains all of the payload
 * of the message (including the opcode)
 */
uint16_t sgs_msg_get_datalen(const sgs_message* pmsg);

/*
 * function: sgs_msg_get_opcode()
 * 
 * Returns the current op-code of this message. This requires that the whole 
 * of the network buffer, including those parts that indicate the length of the
 * buffer, be passed in.
 */
uint8_t sgs_msg_get_opcode(const sgs_message* pmsg);

/*
 * function: sgs_msg_get_size()
 *
 * Returns the total length of this message, including the two bytes used to
 * indicate the overall size of the payload of the message.
 */
uint16_t sgs_msg_get_size(const sgs_message* pmsg);
		
/* 
 * function: sgs_msg_read_uint16()
 *
 * reads the first two bytes of the message passed in, beginning at position 
 * start, and interprets them as an unsigned 16 bit integer (converting from 
 * network format to local format) in the caller-supplied location pointed to
 * by result.
 * 
 * Returns the number of bytes read in the conversion (which should be 2) or
 * -1 if there is a failure
 */
int sgs_msg_read_uint16(const sgs_message *pmsg, const uint16_t start, uint16_t *result);

/* 
 * function: sgs_msg_read_uint32()
 *
 * reads the first four bytes of the message passed in, beginning with the 
 * position start, and interprets them as an unsigned 32 bit integer (converting 
 * from network format to local format) in the caller-supplied location pointed
 * to by result.
 * 
 * Returns the number of bytes read in the conversion (which should be 4) or
 * -1 if there is a failure
 */
int sgs_msg_read_uint32(const sgs_message *pmsg, const uint16_t start, uint32_t *result);

/*
 * function sgs_msg_read_id()
 *
 * Reads and returns an sgs_id from the message passed in, beginning the
 * interpretation with the position start. The result is passed back in the 
 * caller-supplied pointer to an sgs_id; the memory for this structure 
 * will be allocated by this call, but freeing this memory will be the 
 * responsibility of the caller (or subsequent code).
 *
 * Note that if *result points to allocated memory, that memory will be lost. 
 * This function cannot tell if a non-null value of *result is a pointer to 
 * allocated memory or an uninitialized value pointing to nothing.
 *
 * If the value of the parameter read_length is not zero, the length of the 
 * id will be read from the data stream pointed to by pmsg beginning with the
 * position indicated by the parameter start. If the value of read_length is
 * 0, then the size of the id will be taken to be the rest of the buffer pointed
 * to by pmsg from position start to the end of the buffer.
 *
 * Returns the total number of bytes read from the starting point on success,
 * or -1 on failure.
 */

int sgs_msg_read_id(const sgs_message *pmsg, const uint16_t start, 
            int read_length, sgs_id **result);

/*
 * function sgs_msg_read_string()
 *
 * Reads and returns a string from the message passed in, beginning its 
 * interpretation with the position start. The function assumes
 * that the first two bytes of the buffer will be interpreted as the length
 * of the string, in a format appropriate for transmission on the network. The
 * string itself will be contained in the next n bytes (where n is the length
 * contained in the first two bytes). The string returned will be in memory
 * allocated by this function; it is the responsibility of the caller to 
 * free this memory. 
 *
 * Note that if *result points to allocated memory, that memory will be lost. 
 * This function cannot tell if a non-null value of *result is a pointer to 
 * allocated memory or an uninitialized value pointing to nothing.
 *
 * Returns the total number of bytes in the buffer that contained the combination
 * of the string and the length if successful. On failure, returns -1.
 */
int sgs_msg_read_string(const sgs_message *pmsg, const uint16_t start, char **result);

/*
 * function sgs_msg_read_bytes()
 * 
 * Reads the indicated set of bytes from the message, starting with the position 
 * start, and copies them into the buffer pointed to by result. The space for
 * the buffer will be allocated by this function, but will need to be freed by the
 * calling function. 
 * 
 * Note that if *result points to allocated memory, that memory will be lost. 
 * This function cannot tell if a non-null value of *result is a pointer to 
 * allocated memory or an uninitialized value pointing to nothing.
 *
 * Returns the number of bytes transferred, or -1 on failure
 */
int sgs_msg_read_bytes(const sgs_message *pmsg, const uint16_t start, uint8_t **result, uint16_t count);

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
