/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: June 5, 2007
 *
 * This file provides functions relating packing/unpacking CompactId data structures.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "CompactId.h"

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static int8_t getByteCount(uint8_t lengthByte);
static int calcBytesFromCompressed(SGS_ID *id);
static inline int calcBytesFromHex(SGS_ID *id);
static int calcCompressedFromBytes(SGS_ID *id);
static inline int calcHexFromBytes(SGS_ID *id);

/*
 * EXTERNAL FUNCTION IMPLEMENTATIONS
 * (these are functions that are exposed to the outside world).
 */

/*
 * function: SGS_compareCompactIds()
 *
 * Compares two IDs for equivalence.
 *
 * args:
 *   id1: first ID to compare
 *   id2: second ID to compare
 *
 * returns:
 *    0: if the two IDs are identical
 *   -1: if id1 is "less than" id2 (either in length or value)
 *    1: if id1 is "greater than" id2 (either in length or value)
 */
int SGS_compareCompactIds(SGS_ID *id1, SGS_ID *id2) {
  if (id1->datalen < id2->datalen)
    return -1;
  
  if (id1->datalen > id2->datalen)
    return 1;
  
  // else, id1->datalen == id2->datalen
  return memcmp(id1->data, id2->data, id1->datalen);
}

/*
 * functions: SGS_initCompactIdFromBytes()
 *
 * Initializes the fields of an ID, given its byte-array (i.e. non-compressed) representation.
 *
 * args:
 *      data: the byte-array (non-compressed) representation of an ID
 *   datalen: the length of the data array
 *        id: pointer to an ID whose fields will be populated
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int SGS_initCompactIdFromBytes(const uint8_t *data, const size_t datalen, SGS_ID *id) {
  if (datalen > sizeof(id->data)) {
    errno = EINVAL;
    return -1;
  }
  
  memcpy(id->data, data, datalen);
  id->datalen = datalen;
  
  if (calcCompressedFromBytes(id) == -1) return -1;
  if (calcHexFromBytes(id) == -1) return -1;
  
  return 0;
}

/*
 * function: SGS_initCompactIdFromCompressed()
 *
 * Initializes the fields of an ID, given its compact (i.e. compressed) representation.
 *
 * args:
 *      data: the compact (compressed) representation of an ID
 *   datalen: the length of the data array
 *        id: pointer to an ID whose fields will be populated
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int SGS_initCompactIdFromCompressed(const uint8_t *data, const size_t datalen, SGS_ID *id) {
  int size = getByteCount(data[0]);
  if (size == -1) return -1;
  
  if (size > sizeof(id->compressed)) {
    errno = EINVAL;
    return -1;
  }
  
  memcpy(id->compressed, data, size);
  id->compressedlen = size;
  
  if (calcBytesFromCompressed(id) == -1) return -1;
  if (calcHexFromBytes(id) == -1) return -1;
  
  return size;
}

/*
 * function: SGS_initCompactIdFromHex()
 *
 * Initializes the fields of an ID, given its hex-string representation.
 *
 * args:
 *    hexStr: the hex-string representation of an ID, null terminated
 *        id: pointer to an ID whose fields will be populated
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
int SGS_initCompactIdFromHex(const char* hexStr, SGS_ID *id) {
  if (strlen(hexStr) > MAX_SGSID_SIZE*2) {
    errno = EINVAL;
    return -1;
  }
  
  strncpy(id->hexstr, hexStr, sizeof(id->hexstr) - 1);
  
  if (calcBytesFromHex(id) == -1) return -1;
  if (calcCompressedFromBytes(id) == -1) return -1;
  
  return 0;
}

/*
 * function: SGS_equalsServerId()
 *
 * Returns whether the specified ID equals the server's ID (canonically zero).
 * 
 * args:
 *   id: the ID to check
 *
 * returns:
 *    1: if the ID equals the server's ID
 *    0: if the ID does not equal the server's ID
 */
inline int SGS_equalsServerId(SGS_ID *id) {
  if ((id->datalen = 1) && (id->data[0] == 0))
    return 1;
  
  return 0;
}

/*
 * function: SGS_printableCompactId()
 *
 * Returns a hex-string representation of an SGS_ID value.
 */
inline char *SGS_printableCompactId(SGS_ID *id) {
  return id->hexstr;
}

/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */

/*
 * function: calcBytesFromCompressed()
 *
 * Calculates and populates an ID's byte-array format by reading its compact format.
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int calcBytesFromCompressed(SGS_ID *id) {
  int8_t size, first;
  uint8_t firstByte;
  
  if (SGS_COMPACT_IDS) {
    size = getByteCount(id->compressed[0]);
    if (size == -1) return -1;  // error
    
    if (id->compressedlen < size) {
      errno = EINVAL;
      return -1;
    }
    
    if (size <= 8) {
      firstByte = id->compressed[0] & 0x3F;  // clear out first 2 bits
      first = 0;
      
      // find first non-zero byte in external form
      if (firstByte == 0) {
	for (first = 1; first < size && id->compressed[first] == 0; first++)
	  ;
      }
      
      if (first == size) {
	// all bytes are zero, so first byte is last byte
	first = size - 1;
      }
      
      id->datalen = size - first;
      memcpy(id->data, id->compressed + first, id->datalen);
      
      if (first == 0) {
	id->data[0] = firstByte;
      }
    }
    else {
      id->datalen = size - 1;
      memcpy(id->data, id->compressed + 1, id->datalen);
    }
  }
  else {
    // TODO
    perror("TODO - non CompactId decoding not yet implemented");
  }
  
  return 0;
}

/*
 * function: calcBytesFromHex()
 *
 * Calculates and populates an ID's byte-array format by reading its hex-string format.
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static inline int calcBytesFromHex(SGS_ID *id) {
  if (hextobytes(id->hexstr, id->data) == -1) return -1;
  id->datalen = strlen(id->hexstr)/2;
  return 0;
}

/*
 * function: calcCompressedFromBytes()
 *
 * Calculates and populates an ID's compact format by reading its byte-array format.
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static int calcCompressedFromBytes(SGS_ID *id) {
  int b = id->data[0];
  int zeroBits = 0;
  int bitCount;
  int mask, size;
  
  // find bit count
  for ( ; (zeroBits < 8) && ((b & 0x80) == 0); b <<= 1, zeroBits++)
    ;
  
  bitCount = ((id->datalen - 1) << 3) + (8 - zeroBits);
  
  // determine external form's byte count and the mask for most
  // significant two bits of first byte.
  mask = 0x00;
  
  if (bitCount <= 14) {
    size = 2;
  } else if (bitCount <= 30) {
    size = 4;
    mask = 0x40;
  } else if (bitCount <= 62) {
    size = 8;
    mask = 0x80;
  } else {
    size = id->datalen + 1;
    mask = 0xC0 + id->datalen - 8;
  }
  
  // copy id into destination byte array and apply mask
  id->compressedlen = size;
  
  bzero(id->compressed, id->compressedlen);
  memcpy(id->compressed + size - id->datalen, id->data, id->datalen);
  
  id->compressed[0] |= mask;
  
  return 0;
}

/*
 * function: calcHexFromBytes()
 *
 * Calculates and populates an ID's hex-string format by reading its byte-array format.
 *
 * returns:
 *    0: success
 *   -1: failure (errno is set to specific error code)
 */
static inline int calcHexFromBytes(SGS_ID *id) {
  bytestohex(id->data, id->datalen, id->hexstr);
  return 0;
}

/*
 * function: getByteCount()
 *
 * Given the first byte of the compact format of an ID, returns the number of bytes used in that
 *  ID's compact format, *including* this first byte (which, in some cases, contains only size
 *  information and no actual data).
 */
static int8_t getByteCount(uint8_t lengthByte) {
  switch (lengthByte & 0xC0) {  // clears all but the first 2 bits
  case 0x00:  // first two bits: 00
    return 2;
    
  case 0x40:  // first two bits: 01
    return 4;
    
  case 0x80:  // first two bits: 10
    return 8;
    
  default:  // first two bits: 11
    if ((lengthByte & 0x30) == 0) {  // "& 0x30" clears all but bits 5 and 6
      return 9 + (lengthByte & 0x0F);
    }
    else {
      errno = EINVAL;
      return -1;
    }
  }
}
