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
#include "HexUtils.h"
#include "SimpleSgsProtocol.h"

/*
 * DEFINES
 */
#define MAX_SGSID_SIZE  (8 + 0x0F)  // bytes

/*
 * TYPEDEFS
 */
typedef struct {
  uint8_t datalen;                        // len is 0 if the ID is empty (i.e. not yet specified)
  uint8_t data[MAX_SGSID_SIZE];           // normal (uncompressed) form
  uint8_t compressedlen;
  uint8_t compressed[MAX_SGSID_SIZE + 1]; // compressed form (exactly what would be sent in a message)
  char    hexstr[MAX_SGSID_SIZE*2 + 1];   // add 1 slot for null terminator ('\0')
} SGS_ID;


/*
 * FUNCTION DECLARATIONS
 * (implementations are in CompactId.c)
 */
int SGS_compareCompactIds(SGS_ID *id1, SGS_ID *id2);
int SGS_compressCompactId(const SGS_ID *id, uint8_t *buffer, const size_t buflen);
int SGS_decompressCompactId(const uint8_t *data, const size_t datalen, SGS_ID *id);
int SGS_equalsServerId(SGS_ID *id);
int SGS_initCompactIdFromBytes(const uint8_t *data, const size_t datalen, SGS_ID *id);
int SGS_initCompactIdFromCompressed(const uint8_t *data, const size_t datalen, SGS_ID *id);
int SGS_initCompactIdFromHex(const char* hexStr, SGS_ID *id);
char *SGS_printableCompactId(SGS_ID *id);

#endif
