/*
 * This file provides utility functions for converting between byte-arrays
 *  and hex-strings.
 */

#ifndef SGS_HEX_UTILS_H
#define SGS_HEX_UTILS_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"

/*
 * function: bytestohex()
 *
 * Writes hex characters to hexstr that represent the byte values in ba,
 *  followed by a null ('\0') character.  Writes to exactly len*2+1 entries
 *  of hexstr.
 */
int bytestohex(const uint8_t* ba, const int len, char* hexstr);

/*
 * function: hextobytes()
 *
 * Interprets each pair of characters in hexstr as a byte-encoding; fills
 *  ba with the bytes encoded.  The length of hexstr (not including the null
 *  terminator) must be even or an error is returned.  Exactly (length of
 *  hexstr)/2 entries of ba will be written to.
 */
int hextobytes(const char* hexstr, uint8_t* ba);

/*
 * function: hextoi()
 *
 * Returns the integer value represented by the specified ascii hex character.
 */
int hextoi(char c);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_HEX_UTILS_H */
