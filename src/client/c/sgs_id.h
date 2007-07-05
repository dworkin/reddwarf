/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations for unique identifiers.
 */

#ifndef SGS_ID_H
#define SGS_ID_H  1

#include <stdint.h>
#include <stdlib.h>

/*
 * DEFINES
 */
#define SGS_MAX_ID_SIZE  (8 + 0x0F)

/*
 * STRUCTS
 */
struct sgs_id {
    /** normal (uncompressed) form: len is 0 if the ID is unspecified */
    uint8_t data[SGS_MAX_ID_SIZE];
    uint8_t datalen;
  
    /** compressed form (as would be sent over the network */
    uint8_t compressed[SGS_MAX_ID_SIZE + 1];
    uint8_t compressedlen;
  
    /** hex representation (+1 is for the null terminator ('\0') */
    char hexstr[SGS_MAX_ID_SIZE*2 + 1];
};

/*
 * TYPEDEFS
 */
typedef struct sgs_id sgs_id;

/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_id_compare()
 *
 * Compares two IDs for equivalence.
 *
 * returns:
 *    0: if the two IDs are identical
 *   -1: if a is "less than" b (either in length or value)
 *    1: if a is "greater than" b (either in length or value)
 */
int sgs_id_compare(const sgs_id *a, const sgs_id *b);

/*
 * function: sgs_id_compressed_form()
 * 
 * Returns the compressed form of this ID as a byte-array.
 */
const uint8_t *sgs_id_compressed_form(const sgs_id *id);

/*
 * function: sgs_id_compressed_len()
 *
 * Returns the length (in bytes) of the compressef form of this ID.
 */
size_t sgs_id_compressed_len(const sgs_id *id);

/*
 * function: sgs_id_deserialize()
 *
 * Deserializes a sgs_id from a byte array format.
 *
 * args:
 *  buf: the buffer to write into
 *  len: the size (capacity) of the buffer
 *   id: pointer to a sgs_id to deserialize into
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_id_deserialize(const uint8_t *buf, size_t len, sgs_id *id);

/*
 * function: sgs_id_equals_server()
 *
 * Returns whether the specified ID equals the server's ID (canonically zero).
 *
 * returns:
 *    1: if the ID equals the server's ID
 *    0: if the ID does not equal the server's ID
 */
int sgs_id_equals_server(const sgs_id *id);

/*
 * function: sgs_id_printable()
 *
 * Returns a hex-string representation of a sgs_id.
 */
const char *sgs_id_printable(const sgs_id *id);

/*
 * function: sgs_id_serialize()
 *
 * Serializes a sgs_id to a byte array format.
 *
 * args:
 *   id: pointer to the sgs_id to serialize
 *  buf: the buffer to write into
 *  len: the size (capacity) of the buffer
 *
 * returns:
 *  >0: success (value is the number of bytes written to buf)
 *  -1: failure (errno is set to specific error code)
 */
int sgs_id_serialize(const sgs_id *id, uint8_t *buf, size_t len);

/*
 * functions: sgs_id_init_from_bytes()
 *
 * Initializes a sgs_id, given its byte-array (i.e. non-compressed) format.
 *
 * args:
 *      data: the byte-array (non-compressed) representation of an ID
 *   datalen: the length of the data array
 *        id: pointer to the id to initialize
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_id_init_from_bytes(const uint8_t *data, size_t datalen, sgs_id *id);

/*
 * function: sgs_id_init_from_compressed()
 *
 * Initializes a sgs_id, given its compact (i.e. compressed) format.
 *
 * args:
 *      data: the compact (compressed) representation of an ID
 *   datalen: the length of the data array
 *        id: pointer to the id to initialize
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_id_init_from_compressed(const uint8_t *data, size_t datalen, sgs_id *id);

/*
 * function: sgs_id_init_from_hex()
 *
 * Initializes a sgs_id, given its hex-string format.
 *
 * args:
 *    hexStr: the hex-string representation of an ID, null terminated
 *        id: the id to initialize
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int sgs_id_init_from_hex(const char* hexstr, sgs_id *id);

#endif  /** #ifndef SGS_ID_H */
