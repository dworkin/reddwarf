/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: June 5, 2007
 *
 * This file provides functions relating packing/unpacking CompactId data structures.
 */

#ifndef _COMPACTID_H
#define _COMPACTID_H  1

/*
 * INCLUDES
 */
#include <errno.h>
#include <stdint.h>
#include <stdio.h>     // included for FILE typedef
#include <string.h>    // included for memcpy
#include "SimpleSgsProtocol.h"

/*
 * DEFINES
 */
#define MAX_SGSID_SIZE  (8 + 0x0f)  // bytes

/*
 * TYPEDEFS
 */
typedef struct {
  // len is 0 if the ID is empty (i.e. not yet specified)
  uint8_t len;
  
  uint8_t data[MAX_SGSID_SIZE];
  
  // add 1 slot for null terminator ('\0')
  char    hexstr[MAX_SGSID_SIZE*2 + 1];
} SGS_ID;


/*
 * FUNCTION DECLARATIONS
 * (implementations are in CompactId.c)
 */
int SGS_compareCompactIds(SGS_ID *id1, SGS_ID *id2);
int SGS_decodeCompactId(uint8_t *data, uint32_t datalen, SGS_ID *id);
int SGS_equalsServerId(SGS_ID *id);

#endif
