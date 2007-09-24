/*
 * This file provides an implementation of compact-id data structures.
 * Implements functions declared in sgs_id.h.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "sgs/config.h"
#include "sgs/id.h"
#include "sgs/private/id_impl.h"

/* The maximum length of an sgs_compact_id, in bytes. */
#define SGS_COMPACT_ID_MAX_SIZE (8 + 0x0F)

typedef struct sgs_compact_id {
    /** uncompressed form */
    uint8_t uncompressed[SGS_COMPACT_ID_MAX_SIZE];
    uint8_t uncompressed_len;
} sgs_compact_id;


static int8_t get_byte_count(uint8_t length_byte);

static ssize_t pack(uint8_t *dst, size_t dstlen, const uint8_t *src,
    size_t srclen);

static ssize_t unpack(sgs_compact_id* id, const uint8_t *src,
    size_t srclen);

/*
 * sgs_id_compare()
 */
int sgs_id_compare(const sgs_compact_id* a, const sgs_compact_id* b) {
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
int sgs_id_is_server(sgs_compact_id* id) {
    return ((id->uncompressed_len == 1) && (id->uncompressed[0] == 0));
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
 * sgs_id_create()
 */
sgs_compact_id* sgs_id_create(const uint8_t* data, size_t len, ssize_t* rc) {
    sgs_compact_id* id = malloc(sizeof(sgs_compact_id));

    if (id == NULL) return NULL;

    id->uncompressed_len = sizeof(id->uncompressed);

    ssize_t result =
        unpack(id, data, len);

    if (rc != NULL)
        *rc = result;

    if (result == -1) {
        free(id);
        return NULL;
    }
    
    return id;
}

#ifndef NDEBUG
# include <stdio.h>
void sgs_id_dump(const sgs_compact_id* id) {
    for (size_t i = 0; i < id->uncompressed_len; ++i) {
        printf("%2.2x", id->uncompressed[i]);
    }
    printf("\n");
}
#endif /* !NDEBUG */

sgs_compact_id* sgs_id_duplicate(const sgs_compact_id* id) {
    return id;
}

void sgs_id_destroy(sgs_compact_id* id) {
    // no-op
}

/*
 * PRIVATE IMPL FUNCTIONS
 */

/*
 * sgs_id_impl_write()
 */
size_t sgs_id_impl_write(const sgs_compact_id* id, uint8_t* buf, size_t len) {
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
static ssize_t pack(uint8_t *dst, size_t dstlen, const uint8_t *src,
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
 * Returns -1 on error or the number of bytes read on success.
 */
static ssize_t unpack(sgs_compact_id* id, const uint8_t *src,
    size_t srclen)
{
    ssize_t byte_count;
    size_t size, first, datalen;
    uint8_t first_byte;

    if (srclen == 0) {
        memset(id->uncompressed, 0, 1);
        id->uncompressed_len = 1;
        return 0;
    }

    byte_count = get_byte_count(src[0]);
    if (byte_count < 0) return -1;  /* bad byte value */

    size = byte_count;
    
    if (srclen < size) {
        errno = EINVAL;
        return -1;
    }
    
    if (size > id->uncompressed_len) {
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
        memcpy(id->uncompressed, src + first, datalen);
    
        if (first == 0) {
            id->uncompressed[0] = first_byte;
        }
    } else {
        datalen = size - 1;
        memcpy(id->uncompressed, src + 1, datalen);
    }

    id->uncompressed_len = datalen;
    return size;
}
