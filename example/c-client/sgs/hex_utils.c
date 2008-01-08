/*
 * Copyright (c) 2008, Sun Microsystems, Inc.
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
 *  and hex-strings.  Relevant declarations can be found in sgs_hex_utils.h.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */ 

#include "sgs/hex_utils.h"

/*
 * bytestohex()
 */
int bytestohex(const uint8_t *ba, const int len, char *hexstr) {
    int i;
  
    /** leading zeros are important! */
    for (i=0; i < len; i++)
        sprintf(hexstr + i*2, "%02X", ba[i]);
    
    hexstr[len*2] = '\0';
    
    return 0;
}

/*
 * hextobytes()
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
 * hextoi()
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
