/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides an implementation of compact-id data structures.
 * Implements function declared in sgs_id.h.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

/*
 * INCLUDES
 */
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "sgs_hex_utils.h"
#include "sgs_id.h"


/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static int8_t get_byte_count(uint8_t length_byte);
static int calc_bytes_from_compressed(sgs_id *id);
static int calc_bytes_from_hex(sgs_id *id);
static int calc_compressed_from_bytes(sgs_id *id);
static int calc_hex_from_bytes(sgs_id *id);

/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_ID.H
 */

/*
 * sgs_id_compare()
 */
int sgs_id_compare(const sgs_id *a, const sgs_id *b) {
  if (a->datalen < b->datalen)
    return -1;
  
  if (a->datalen > b->datalen)
    return 1;
  
  /** else, a->datalen == b->datalen */
  return memcmp(a->data, b->data, a->datalen);
}

/*
 * sgs_id_compressed_form()
 */
const uint8_t *sgs_id_compressed_form(const sgs_id *id) {
  return id->compressed;
}

/*
 * sgs_id_compressed_len()
 */
size_t sgs_id_compressed_len(const sgs_id *id) {
  return id->compressedlen;
}

/*
 * sgs_id_deserialize()
 */
int sgs_id_deserialize(const uint8_t *buf, size_t len, sgs_id *id) {
  if (sgs_id_init_from_compressed(buf, len, id) == -1) return -1;
  return 0;
}

/*
 * sgs_id_equals_server()
 */
int sgs_id_equals_server(const sgs_id *id) {
  if ((id->datalen == 1) && (id->data[0] == 0))
    return 1;
  
  return 0;
}

/*
 * sgs_id_printable()
 */
const char *sgs_id_printable(const sgs_id *id) {
  return id->hexstr;
}

/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_ID.H
 */

/*
 * sgs_id_init_from_bytes()
 */
int sgs_id_init_from_bytes(const uint8_t *data, size_t datalen, sgs_id *id)
{
  if (datalen > sizeof(id->data)) {
    errno = EINVAL;
    return -1;
  }
  
  memcpy(id->data, data, datalen);
  id->datalen = datalen;
  
  if (calc_compressed_from_bytes(id) == -1) return -1;
  if (calc_hex_from_bytes(id) == -1) return -1;
  
  return 0;
}

/*
 * sgs_id_init_from_compressed()
 */
int sgs_id_init_from_compressed(const uint8_t *data, size_t datalen, sgs_id *id)
{
  int size = get_byte_count(data[0]);
  if (size == -1) return -1;
  
  if (size > sizeof(id->compressed)) {
    errno = EINVAL;
    return -1;
  }
  
  memcpy(id->compressed, data, size);
  id->compressedlen = size;
  
  if (calc_bytes_from_compressed(id) == -1) return -1;
  if (calc_hex_from_bytes(id) == -1) return -1;
  
  return size;
}

/*
 * sgs_id_init_from_hex()
 */
int sgs_id_init_from_hex(const char* hexstr, sgs_id *id) {
  if (strlen(hexstr) > SGS_MAX_ID_SIZE*2) {
    errno = EINVAL;
    return -1;
  }
  
  strncpy(id->hexstr, hexstr, sizeof(id->hexstr) - 1);
  
  if (calc_bytes_from_hex(id) == -1) return -1;
  if (calc_compressed_from_bytes(id) == -1) return -1;
  
  return 0;
}


/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 */

/*
 * function: calc_bytes_from_compressed()
 *
 * Calculates and populates an ID's byte-array format by reading its compact
 * format.
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int calc_bytes_from_compressed(sgs_id *id) {
  int size, first;
  uint8_t first_byte;
  
  size = get_byte_count(id->compressed[0]);
  if (size == -1) return -1;
  
  if (id->compressedlen < size) {
    errno = EINVAL;
    return -1;
  }
  
  if (size <= 8) {
    first_byte = id->compressed[0] & 0x3F;  /** clear out first 2 bits */
    first = 0;
    
    /** find first non-zero byte in external form */
    if (first_byte == 0) {
      for (first = 1; first < size && id->compressed[first] == 0; first++)
        ;
    }
    
    if (first == size) {
      /** all bytes are zero, so first byte is last byte */
      first = size - 1;
    }
    
    id->datalen = size - first;
    memcpy(id->data, id->compressed + first, id->datalen);
    
    if (first == 0) {
      id->data[0] = first_byte;
    }
  }
  else {
    id->datalen = size - 1;
    memcpy(id->data, id->compressed + 1, id->datalen);
  }
  
  return 0;
}

/*
 * function: calc_bytes_from_hex()
 *
 * Calculates and populates an ID's byte-array format by reading its hex-string
 * format.
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int calc_bytes_from_hex(sgs_id *id) {
  if (hextobytes(id->hexstr, id->data) == -1) return -1;
  id->datalen = strlen(id->hexstr)/2;
  return 0;
}

/*
 * function: calc_compressed_from_bytes()
 *
 * Calculates and populates an ID's compact format by reading its byte-array
 * format.
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int calc_compressed_from_bytes(sgs_id *id) {
  int b = id->data[0];
  int zero_bits = 0;
  int bit_count;
  int mask, size;
  
  /** find bit count */
  for ( ; (zero_bits < 8) && ((b & 0x80) == 0); b <<= 1, zero_bits++)
    ;
  
  bit_count = ((id->datalen - 1) << 3) + (8 - zero_bits);
  
  /**
   * determine external form's byte count and the mask for most significant two
   * bits of first byte.
   */
  mask = 0x00;
  
  if (bit_count <= 14) {
    size = 2;
  } else if (bit_count <= 30) {
    size = 4;
    mask = 0x40;
  } else if (bit_count <= 62) {
    size = 8;
    mask = 0x80;
  } else {
    size = id->datalen + 1;
    mask = 0xC0 + id->datalen - 8;
  }
  
  /** copy id into destination byte array and apply mask */
  id->compressedlen = size;
  
  memset(id->compressed, '\0', id->compressedlen);
  memcpy(id->compressed + size - id->datalen, id->data, id->datalen);
  
  id->compressed[0] |= mask;
  
  return 0;
}

/*
 * function: calc_hex_from_bytes()
 *
 * Calculates and populates an ID's hex-string format by reading its byte-array
 * format.
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int calc_hex_from_bytes(sgs_id *id) {
  bytestohex(id->data, id->datalen, id->hexstr);
  return 0;
}

/*
 * function: get_byte_count()
 *
 * Given the first byte of the compact format of an ID, returns the number of
 * bytes used in that ID's compact format, *including* this first byte (which,
 * in some cases, contains only size information and no actual data).
 */
static int8_t get_byte_count(uint8_t length_byte) {
  switch (length_byte & 0xC0) {  /** clears all but the first 2 bits */
  case 0x00:  /** first two bits: 00 */
    return 2;
    
  case 0x40:  /** first two bits: 01 */
    return 4;
    
  case 0x80:  /** first two bits: 10 */
    return 8;
    
  default:    /** first two bits: 11 */
    if ((length_byte & 0x30) == 0) {  /** "& 0x30" clears all but bits 5 and 6 */
      return 9 + (length_byte & 0x0F);
    }
    else {
      errno = EINVAL;
      return -1;
    }
  }
}
