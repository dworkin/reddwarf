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
 * This file provides declarations for map data structures.
 */

#ifndef SGS_MAP_H
#define SGS_MAP_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"

typedef struct sgs_linked_list_map sgs_map;

/*
 * function: sgs_map_contains()
 *
 * Returns 1 if an element exists in the map associated with the specified key,
 * or 0 if not.
 */
int sgs_map_contains(const sgs_map* map, const void* key);

/*
 * function: sgs_map_clear()
 *
 * Removes all elements from the map.
 */
void sgs_map_clear(sgs_map* map);

/*
 * function: sgs_map_destroy()
 *
 * Safely deallocates a map (but not its contents).
 */
void sgs_map_destroy(sgs_map* map);

/*
 * function: sgs_map_get()
 *
 * If an element exists in the map associated with the specified key, a pointer
 * to the value field of the element is returned.  Otherwise, NULL is returned.
 * Note that it is legal for the value of an element to be the NULL pointer, in
 * which case this function will return the same value whether the element is
 * present in the map or not; sgs_map_contains() can be used to disambiguate
 * these two cases.
 */
void* sgs_map_get(const sgs_map* map, const void* key);

/*
 * function: sgs_map_create()
 *
 * Allocates and initializes a map.  The comparator argument is a pointer to a
 * function that can be used to compare two keys.  The function must return 0 if
 * the two keys are equal and any non-zero value if the keys are not equal.
 * NULL is returned if allocation fails.
 */
sgs_map* sgs_map_create(int (*comparator)(const void*, const void*));

/*
 * function: sgs_map_put()
 *
 * Inserts a new element into the map associated with the specified key/value
 * pair.  If an element already exists in the map with the specified key,
 * that element is replaced.
 * 
 * returns:
 *    1: success (element was inserted into the list; did not previously exist)
 *    0: success (element was inserted into the list; replaced existing element)
 *   -1: failure (errno is set to specific error code)
 */
int sgs_map_put(sgs_map* map, const void* key, void* value);

/*
 * function: sgs_map_remove()
 *
 * If an element exists in the map associated with the specified key, that
 * element is removed and 0 is returned.  If no such element exists, the map is
 * not altered and -1 is returned.
 */
int sgs_map_remove(sgs_map* map, const void* key);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_MAP_H */
