/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

/*
 * This file provides declarations for the compact-id implementation of unique
 * identifiers.
 */

#ifndef SGS_COMPACT_ID_H
#define SGS_COMPACT_ID_H  1


/*
 * INCLUDES
 */
#include <stdint.h>


/*
 * DEFINES
 */
#define SGS_MAX_COMPACT_ID_SIZE  (8 + 0x0F)


/*
 * STRUCTS
 */
typedef struct sgs_compact_id {
    /** uncompressed form */
    uint8_t uncompressed[SGS_MAX_COMPACT_ID_SIZE];
    uint8_t uncompressed_len;
} sgs_compact_id;

/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_compact_id_init()
 *
 * Initializes an sgs_compact_id from the specified byte array of compressed
 * data.  Returns -1 on failure or the number of bytes read on success.
 */
size_t sgs_compact_id_init(sgs_compact_id *id, const uint8_t *data, size_t len);

/*
 * function: sgs_compact_id_write()
 *
 * Writes the compressed form of the sgs_compact_id to the specified byte
 * array.  Returns -1 on failure or the number of bytes written on success.
 */
size_t sgs_compact_id_write(const sgs_compact_id *id, uint8_t *buf, size_t len);

#endif /* SGS_COMPACT_ID_H */
