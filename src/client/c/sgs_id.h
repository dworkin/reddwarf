/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

/*
 * This file provides declarations for unique identifiers.
 */

#ifndef SGS_ID_H
#define SGS_ID_H  1


/*
 * INCLUDES
 */
#include <stdint.h>

/*
 * Including the following header allows files that include sgs_id.h to declare
 * instances of sgs_id - otherwise compiler gives:
 *    error: storage size of 'foo' isn't known
 */
#include "sgs_compact_id.h"

/*
 * sgs_compact_id provides the implementation for the sgs_id interface
 */
typedef struct sgs_compact_id sgs_id;


/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_id_compare()
 *
 * Compares two IDs for equivalence.  Returns 0 if the IDs are equal, a value
 * less than 0 if id1 is less than id2, or a value greater than 0 if id1 is
 * greater than id2.
 */
int sgs_id_compare(const sgs_id *id1, const sgs_id *id2);

/*
 * function: sgs_id_equals_server()
 *
 * Returns a non-zero value if and only if the specified sgs_id matches the ID
 * of the server (canonically 0).
 */
int sgs_id_equals_server(sgs_id *id);

/*
 * function: sgs_id_init()
 *
 * Initializes an sgs_id from the specified byte array.
 */
int sgs_id_init(sgs_id *id, const uint8_t *data, size_t len);

/*
 * function: sgs_id_get_bytes()
 *
 * Returns the byte-array representation of the specified sgs_id.
 */
const uint8_t *sgs_id_get_bytes(const sgs_id *id);

/*
 * function: sgs_id_get_byte_len()
 *
 * Returns the number of bytes in the byte-array representation of the specified
 * sgs_id. If the sgs_id has not been initialized, it is "empty" and this method
 * will return 0;
 */
size_t sgs_id_get_byte_len(const sgs_id *id);

#endif  /** #ifndef SGS_ID_H */
