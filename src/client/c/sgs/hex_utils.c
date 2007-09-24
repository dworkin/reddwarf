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
