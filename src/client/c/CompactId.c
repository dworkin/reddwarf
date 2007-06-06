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


/*
 * EXTERNAL FUNCTION IMPLEMENTATIONS
 * (these are functions that are exposed to the outside world).
 */

// TODO
#include <stdio.h>

/*
 *
 */
int SGS_compareCompactIds(SGS_ID *id1, SGS_ID *id2) {
  if (id1->len < id2->len)
    return -1;
  
  if (id1->len > id2->len)
    return 1;
  
  // else, id1->len == id2->len
  return memcmp(id1->data, id2->data, id1->len);
}

/*
 * TODO
 */
int SGS_decodeCompactId(uint8_t *data, uint32_t datalen, SGS_ID *id) {
  int8_t size, first, i;
  uint8_t firstByte;
  
  if (SGS_COMPACT_IDS) {
    size = getByteCount(data[0]);
    if (size == -1) return -1;  // error
    
    if (datalen < size) {
      errno = EINVAL;
      return -1;
    }
    
    if (size <= 8) {
      firstByte = data[0] & 0x3f;  // clear out first 2 bits
      first = 0;
      
      // find first non-zero byte in external form
      if (firstByte == 0) {
	for (first = 1; first < size && data[first] == 0; first++)
	  ;
      }
      
      if (first == size) {
	// all bytes are zero, so first byte is last byte
	first = size - 1;
      }
      
      id->len = size - first;
      memcpy(id->data, data + first, id->len);
      
      if (first == 0) {
	id->data[0] = firstByte;
      }
    }
    else {
      id->len = size - 1;
      memcpy(id->data, data + 1, id->len);
    }
  }
  else {
    
    // TODO
    perror("TODO - non CompactId decoding not yet implemented");
  }
  
  // leading zeros are important!
  for (i=0; i < id->len; i++)
    sprintf(id->hexstr + i*2, "%02x", id->data[i]);
  
  id->hexstr[id->len*2] = '\0';
  
  return size;
}

/*
 * todo
*/
// TODO - not sure if this works right
int SGS_equalsServerId(SGS_ID *id) {
  if ((id->len = 1) && (id->data[0] == 0))
    return 1;
  
  return 0;
}

/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */

/*
 * TODO
 */
static int8_t getByteCount(uint8_t lengthByte) {
  switch (lengthByte & 0xc0) {  // clears all but the first 2 bits
  case 0x00:  // first two bits: 00
    return 2;
    
  case 0x40:  // first two bits: 01
    return 4;
    
  case 0x80:  // first two bits: 10
    return 8;
    
  default:  // first two bits: 11
    if ((lengthByte & 0x30) == 0) {  // "& 0x30" clears all but bits 5 and 6
      return 9 + (lengthByte & 0x0f);
    }
    else {
      errno = EINVAL;
      return -1;
    }
  }
}
