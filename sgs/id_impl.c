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


/*
 * sgs_id_compare()
 */
int sgs_id_compare(const sgs_id* a, const sgs_id* b) {
    if (a->buf_len < b->buf_len)
        return -1;

    if (a->buf_len > b->buf_len)
        return 1;

    /** else, a->buf_len == b->buf_len */
    return memcmp(a->buf, b->buf, a->buf_len);
}

/*
 * sgs_id_get_bytes()
 */
const uint8_t *sgs_id_get_bytes(const sgs_id *id) {
    return id->buf;
}

/*
 * sgs_id_get_byte_len()
 */
size_t sgs_id_get_byte_len(const sgs_id *id) {
    return id->buf_len;
}

/*
 * sgs_id_create()
 * creates a sgs_id from the data stream. The argument data points
 * to the data stream that begins with the information that is needed to
 * create the id; the len parameter is the total length of the buffer pointed
 * to by data
 */
sgs_id* sgs_id_create(const uint8_t* data, size_t len) {
    sgs_id* id;

    id = malloc(sizeof (sgs_id));
    if (id == NULL) return NULL;

    id->buf_len = len;

    id->buf = malloc(len);
    if (id->buf == NULL) {
        free(id);
        return NULL;
    }

    memcpy(id->buf, data, len);

    return id;
}

#ifndef NDEBUG
#include <stdio.h>

void sgs_id_dump(const sgs_id* id) {
    size_t i;
    for (i = 0; i < id->buf_len; ++i) {
        printf("%2.2x", id->buf[i]);
    }
    printf("\n");
}
#else /* NDEBUG */

void sgs_id_dump(const sgs_id* id) {
}
#endif /* NDEBUG */

sgs_id* sgs_id_duplicate(const sgs_id* id) {
    sgs_id* result;

    result = malloc(sizeof (sgs_id));
    if (result == NULL) return NULL;

    result->buf_len = id->buf_len;
    result->buf = malloc(id->buf_len);
    if (result->buf == NULL){
        free(result);
        return NULL;
    }
    memcpy(result->buf, id->buf, id->buf_len);

    return result;
}

void sgs_id_destroy(sgs_id* id) {
    if (id != NULL){
        free(id->buf);
        free(id);
    }
}

