/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

/*
 * This file provides declarations for map data structures.
 */

#ifndef SGS_MAP_H
#define SGS_MAP_H  1


/*
 * sgs_linked_list_map provides the implementation for the sgs_map interface
 */
typedef struct sgs_linked_list_map sgs_map;


/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_map_contains()
 *
 * Returns 1 if an element exists in the map associated with the specified key,
 * or 0 if not.
 */
int sgs_map_contains(const sgs_map *map, const void *key);

/*
 * function: sgs_map_empty()
 *
 * Removes all elements from the map.
 */
void sgs_map_empty(sgs_map *map);

/*
 * function: sgs_map_free()
 *
 * Safely deallocates a map (and any contents).
 */
void sgs_map_free(sgs_map *map);

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
void *sgs_map_get(const sgs_map *map, const void *key);

/*
 * function: sgs_map_new()
 *
 * Allocates and initializes a map.  The comparator argument is a pointer to a
 * function that can be used to compare two keys.  The function must return 0 if
 * the two keys are equal and any non-zero value if the keys are not equal.
 * NULL is returned if allocation fails.
 */
sgs_map *sgs_map_new(int (*comparator)(const void*, const void*),
    void (*free_map_key)(void*), void (*free_map_value)(void*));

/*
 * function: sgs_map_put()
 *
 * Inserts a new element into the map associated with the specified key/value
 * pair, both of which are _copied_ from the specified data arrays.  If an
 * element already exists in the map with the specified key, that element is
 * replaced.
 * 
 * returns:
 *    1: success (element was inserted into the list; did not previously exist)
 *    0: success (element was inserted into the list; replaced existing element)
 *   -1: failure (errno is set to specific error code)
 */
int sgs_map_put(sgs_map *map, void *key, void *value);

/*
 * function: sgs_map_remove()
 *
 * If an element exists in the map associated with the specified key, that
 * element is removed and 0 is returned.  If no such element exists, the map is
 * not altered and -1 is returned.
 */
int sgs_map_remove(sgs_map *map, const void *key);

#endif  /** #ifndef SGS_MAP_H */
