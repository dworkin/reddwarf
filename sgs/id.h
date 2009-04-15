/*
 * Copyright (c) 2007 - 2009, Sun Microsystems, Inc.
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
 * This file provides declarations for unique identifiers.
 */

#ifndef SGS_ID_H
#define SGS_ID_H  1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"


typedef struct sgs_id {
    /** buf form */
    uint8_t *buf;
    uint8_t buf_len;
} sgs_id;

/*
 * function: sgs_id_create()
 *
 * Creates an sgs_id from the specified byte array.
 * Returns the bytes of data used in rc, if rc not NULL.
 */
sgs_id* sgs_id_create(const uint8_t* data, size_t len);

/*
 * function: sgs_id_duplicate()
 *
 * Creates an sgs_id from the specified sgs_id.
 */
sgs_id* sgs_id_duplicate(const sgs_id* id);

/*
 * function: sgs_id_destroy()
 *
 * Destroys an sgs_id, freeing any resources it was holding.
 */
void sgs_id_destroy(sgs_id* id);

/*
 * function: sgs_id_compare()
 *
 * Compares two IDs for equivalence.  Returns 0 if the IDs are equal, a value
 * less than 0 if a is less than b, or a value greater than 0 if a is
 * greater than b.
 */
int sgs_id_compare(const sgs_id* a, const sgs_id* b);

/*
 * function: sgs_id_get_bytes()
 *
 * Returns the byte-array representation of the specified sgs_id.
 */
const uint8_t* sgs_id_get_bytes(const sgs_id* id);

/*
 * function: sgs_id_get_byte_len()
 *
 * Returns the number of bytes in the byte-array representation of the
 * specified sgs_id. If the sgs_id has not been initialized, it is "empty" and
 * this method will return 0;
 */
size_t sgs_id_get_byte_len(const sgs_id* id);

/*
 * function: sgs_id_dump()
 *
 * Dumps an sgs_id to stdout (noop if NDEBUG defined).
 */
void sgs_id_dump(const sgs_id* id);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_ID_H */
