/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: June 13, 2007
 *
 * This file provides utility functions for converting between byte-arrays
 *  and hex-strings.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "HexUtils.h"

/*
 * EXTERNAL FUNCTION IMPLEMENTATIONS
 * (these are functions that are exposed to the outside world).
 */

/*
 * function: bytestohex()
 *
 * Writes hex characters to hexstr that represent the byte values in ba,
 *  followed by a null ('\0') character.  Writes to exactly len*2+1 entries
 *  of hexstr.
 */
int bytestohex(const uint8_t *ba, const int len, char *hexstr) {
  int i;
  
  // leading zeros are important!
  for (i=0; i < len; i++)
    sprintf(hexstr + i*2, "%02X", ba[i]);
  
  hexstr[len*2] = '\0';
  
  return 0;
}

/*
 * function: hextobytes()
 *
 * Interprets each pair of characters in hexstr as a byte-encoding; fills
 *  ba with the bytes encoded.  The length of hexstr (not including the null
 *  terminator) must be even or an error is returned.  Exactly (length of
 *  hexstr)/2 entries of ba will be written to.
 */
int hextobytes(const char *hexstr, uint8_t *ba) {
  int i, hi, lo, hexlen;
  
  hexlen = strlen(hexstr);
  
  if ((hexlen & 1) == 1) {
    errno = EINVAL;
    return -1;
  }
  
  for (i=0; i < hexlen/2; i++) {
    hi = hextoi(hexstr[2*i]);
    lo = hextoi(hexstr[2*i+1]);
    
    if (hi == -1 || lo == -1) {
      errno = EINVAL;
      return -1;
    }
    
    ba[i] = hi*16 + lo;
  }
  
  return 0;
}

/*
 * function: hextoi()
 *
 * Returns the integer value represented by the specified ascii hex character.
 */
int hextoi(char c) {
  if (c >= '0' && c <= '9')
    return c - '0';
  
  if (c >= 'A' && c <= 'F')
    return c - 'A' + 10;
  
  if (c >= 'a' && c <= 'f')
    return c - 'a' + 10;
  
  return -1;
}
