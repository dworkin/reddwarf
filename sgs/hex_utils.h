/*
 * Copyright (c) 2007, 2008, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
