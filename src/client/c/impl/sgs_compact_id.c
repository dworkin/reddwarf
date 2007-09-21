/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

/*
 * This file provides an implementation of compact-id data structures.
 * Implements functions declared in sgs_id.h.
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
#include <stdlib.h>
#include <string.h>
#include "sgs_compact_id.h"


/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static int8_t get_byte_count(uint8_t length_byte);
static size_t pack(uint8_t *dst, size_t dstlen, const uint8_t *src,
    size_t srclen);
static size_t unpack(uint8_t *dst, size_t dstlen, const uint8_t *src,
    size_t srclen);

/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_ID.H
 */

/*
 * sgs_id_compare()
 */
int sgs_id_compare(const sgs_compact_id *a, const sgs_compact_id *b) {
    if (a->uncompressed_len < b->uncompressed_len)
        return -1;
  
    if (a->uncompressed_len > b->uncompressed_len)
        return 1;
  
    /** else, a->uncompressed_len == b->uncompressed_len */
    return memcmp(a->uncompressed, b->uncompressed, a->uncompressed_len);
}

/*
 * sgs_id_equals_server()
 */
int sgs_id_equals_server(sgs_compact_id *id) {
    return ((id->uncompressed_len == 1) && (id->uncompressed[0] == 0));
}

/*
 * sgs_id_init()
 */
int sgs_id_init(sgs_compact_id *id, const uint8_t *data, size_t len) {
    if (len > sizeof(id->uncompressed)) {
        errno = EINVAL;
        return -1;
    }
    
    memcpy(id->uncompressed, data, len);
    id->uncompressed_len = len;
    return 0;
}

/*
 * sgs_id_get_bytes()
 */
const uint8_t *sgs_id_get_bytes(const sgs_compact_id *id) {
    return id->uncompressed;
}

/*
 * sgs_id_get_byte_len()
 */
size_t sgs_id_get_byte_len(const sgs_compact_id *id) {
    return id->uncompressed_len;
}


/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_COMPACT_ID.H
 */

/*
 * sgs_compact_id_init()
 */
size_t sgs_compact_id_init(sgs_compact_id *id, const uint8_t *data, size_t len) {
    size_t result = unpack(id->uncompressed, sizeof(id->uncompressed), data, len);
    if (result == -1) return -1;
    
    id->uncompressed_len = result;
    return get_byte_count(data[0]);
}

/*
 * sgs_compact_id_write()
 */
size_t sgs_compact_id_write(const sgs_compact_id *id, uint8_t *buf, size_t len) {
    return pack(buf, len, id->uncompressed, id->uncompressed_len);
}


/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 */

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
        /** "& 0x30" clears all but bits 5 and 6 */
        if ((length_byte & 0x30) == 0) {
            return 9 + (length_byte & 0x0F);
        } else {
            return -1;
        }
    }
}

/*
 * function: pack()
 *
 * Converts bytes from normal (unpacked) format to a compact format.
 */
static size_t pack(uint8_t *dst, size_t dstlen, const uint8_t *src,
    size_t srclen)
{
    uint8_t b = src[0];
    int zero_bits = 0;
    size_t bit_count, size;
    int mask;
    
    /** find bit count */
    for ( ; (zero_bits < 8) && ((b & 0x80) == 0); b <<= 1, zero_bits++)
        ;
    
    bit_count = ((srclen - 1) << 3) + (8 - zero_bits);
    
    /**
     * determine external form's byte count and the mask for most significant
     * two bits of first byte.
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
        size = srclen + 1;
        mask = 0xC0 + srclen - 8;
    }
    
    if (size > dstlen) {
        errno = ENOBUFS;
        return -1;
    }
    
    /** copy id into destination byte array and apply mask */
    memset(dst, '\0', size);
    memcpy(dst + size - srclen, src, srclen);
    
    dst[0] |= mask;
    
    return size;
}

/*
 * function: unpack()
 *
 * Converts a byte array from a compact format to a normal (unpacked) format.
 * Returns -1 on error or the number of bytes written on success.
 */
static size_t unpack(uint8_t *dst, size_t dstlen, const uint8_t *src,
    size_t srclen)
{
    size_t size, first, datalen;
    uint8_t first_byte;
    
    size = get_byte_count(src[0]);
    if (size == -1) return -1;  /* bad byte value */
    
    if (srclen < size) {
        errno = EINVAL;
        return -1;
    }
    
    if (size > dstlen) {
        errno = ENOBUFS;
        return -1;
    }
    
    if (size <= 8) {
        first_byte = src[0] & 0x3F;  /** clear out first 2 bits */
        first = 0;
        
        /** find first non-zero byte in external form */
        if (first_byte == 0) {
            for (first = 1; first < size && src[first] == 0; first++)
                ;
        }
        
        if (first == size) {
            /** all bytes are zero, so first byte is last byte */
            first = size - 1;
        }
        
        datalen = size - first;
        memcpy(dst, src + first, datalen);
    
        if (first == 0) {
            dst[0] = first_byte;
        }
    } else {
        datalen = size - 1;
        memcpy(dst, src + 1, datalen);
    }
    
    return datalen;
}
