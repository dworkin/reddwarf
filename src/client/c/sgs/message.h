/*
 * This file provides declarations relating to network messages.
 */

#ifndef SGS_MESSAGE_H
#define SGS_MESSAGE_H  1

#include "sgs/config.h"

typedef struct sgs_message_impl sgs_message;

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
int sgs_msg_add_id(sgs_message* msg, const sgs_id* id);

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
sgs_message* sgs_msg_deserialize(uint8_t* buffer, size_t buflen);

/*
 * function: sgs_msg_get_bytes()
 *
 * Returns a pointer to this message's byte-array representation.
 */
const uint8_t* sgs_msg_get_bytes(sgs_message* pmsg);

/*
 * function: sgs_msg_get_data()
 * 
 * Returns a pointer to the start of this message's data payload.
 */
const uint8_t* sgs_msg_get_data(sgs_message* pmsg);

/*
 * function: sgs_msg_get_datalen()
 * 
 * Returns the length of this message's data payload.
 */
size_t sgs_msg_get_datalen(sgs_message* pmsg);

/*
 * function: sgs_msg_get_opcode()
 * 
 * Returns the current op-code of this message.
 */
uint8_t sgs_msg_get_opcode(sgs_message* pmsg);

/*
 * function: sgs_msg_get_service()
 * 
 * Returns the current service-id of this message.
 */
uint8_t sgs_msg_get_service(sgs_message* pmsg);

/*
 * function: sgs_msg_get_size()
 *
 * Returns the total length of this message.
 */
size_t sgs_msg_get_size(sgs_message* pmsg);

/*
 * function: sgs_msg_get_version()
 * 
 * Returns the current version-ID of this message.
 */
uint8_t sgs_msg_get_version(sgs_message* pmsg);

/*
 * function: sgs_msg_create()
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
sgs_message* sgs_msg_create(uint8_t* buffer, size_t buflen,
    sgs_opcode opcode, sgs_service_id service_id);

void sgs_msg_destroy(sgs_message* msg);

#ifndef NDEBUG
void sgs_msg_dump(const sgs_message* msg);
#endif /* !NDEBUG */

#endif /* !SGS_MESSAGE_H */
