/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations relating to network messages.
 */

#ifndef SGS_MESSAGE_H
#define SGS_MESSAGE_H  1

/*
 * INCLUDES
 */
#include <stdint.h>
#include "sgs_wire_protocol.h"

/*
 * TYPEDEFS
 */
typedef struct sgs_message {
    /** Pointer to the start of the memory reserved for this message. */
    uint8_t *buf;
  
    /**
     * Length of the memory reserved for this message.  This may be larger than
     * the number of bytes in the actual message.  If a method attempts to add
     * data to the message payload but there is no room (as indicated by this
     * variable), that method must fail and not write beyond the memory limit
     * imposed by this value.
     */
    size_t buflen;
  
    /**
     * The number of size of this message (so far).  Since the first 4 bytes of
     * any message contains its length (not including those 4 bytes), this
     * variable is redundant with those bytes but is more convenient to use.
     */
    size_t size;
} sgs_message;


/*
 * FUNCTION DECLARATIONS
 */

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
int sgs_msg_add_arb_content(sgs_message *pmsg, const uint8_t *content,
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
int sgs_msg_add_fixed_content(sgs_message *pmsg, const uint8_t *content,
    size_t clen);

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
int sgs_msg_add_uint32(sgs_message *pmsg, uint32_t val);

/*
 * function: sgs_msg_deserialize()
 *
 * Initializes a message from a byte-array.
 *
 * returns:
 *   >0: success (return value is the number of bytes of buffer that were read)
 *   -1: failure (errno is set to specific error code)
 */
int sgs_msg_deserialize(sgs_message *pmsg, uint8_t *buffer, size_t buflen);

/*
 * function: sgs_msg_get_bytes()
 *
 * Returns a pointer to this message's byte-array representation.
 */
const uint8_t *sgs_msg_get_bytes(sgs_message *pmsg);

/*
 * function: sgs_msg_get_data()
 * 
 * Returns a pointer to the start of this message's data payload.
 */
const uint8_t *sgs_msg_get_data(sgs_message *pmsg);

/*
 * function: sgs_msg_get_datalen()
 * 
 * Returns the length of this message's data payload.
 */
size_t sgs_msg_get_datalen(sgs_message *pmsg);

/*
 * function: sgs_msg_get_opcode()
 * 
 * Returns the current op-code of this message.
 */
uint8_t sgs_msg_get_opcode(sgs_message *pmsg);

/*
 * function: sgs_msg_get_service()
 * 
 * Returns the current service-id of this message.
 */
uint8_t sgs_msg_get_service(sgs_message *pmsg);

/*
 * function: sgs_msg_get_size()
 *
 * Returns the total length of this message.
 */
size_t sgs_msg_get_size(sgs_message *pmsg);

/*
 * function: sgs_msg_get_version()
 * 
 * Returns the current version-ID of this message.
 */
uint8_t sgs_msg_get_version(sgs_message *pmsg);

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
 *   0: success
 *  -1: failure (errno is set to specific error code)
 */
int sgs_msg_init(sgs_message *pmsg, uint8_t *buffer, size_t buflen,
    sgs_opcode opcode, sgs_service_id service_id);

#endif  /** #ifndef SGS_MESSAGE_H */
