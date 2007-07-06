/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations for map data structures.
 */

#ifndef SGS_MAP_H
#define SGS_MAP_H  1

/*
 * Opaque pointer (declare before any #includes)
 *  - Use linked list implementation
 */
typedef struct sgs_linked_list_map *sgs_map;

/*
 * TYPEDEFS
 */
typedef void sgs_map_key;
typedef void sgs_map_value;

/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_map_new()
 *
 * Allocates and initializes a map.  The comparator argument is a pointer to a
 * function that can be used to compare two keys.  The function must return 0 if
 * the two keys are equal and any non-zero value if the keys are not equal.
 * NULL is returned if allocation fails.
 */
sgs_map sgs_map_new(int (*comparator)(const sgs_map_key*, const sgs_map_value*),
    void (*free_map_key)(sgs_map_key*), void (*free_map_value)(sgs_map_value*));

/*
 * function: sgs_map_free()
 *
 * Safely deallocates a map (and any contents).
 */
void sgs_map_free(sgs_map map);

/*
 * function: sgs_map_empty()
 *
 * Removes all elements from the map.
 */
void sgs_map_empty(sgs_map map);

/*
 * function: sgs_map_get()
 *
 * If an element exists in the map associated with the specified key, value is
 * set to point to the value field of the element and 1 is returned.  If no such
 * element exists, 0 is returned.
 */
int sgs_map_get(const sgs_map map, const sgs_map_key *key, sgs_map_value **value);

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
int sgs_map_put(sgs_map map, sgs_map_key *key, sgs_map_value *value);

/*
 * function: sgs_map_remove()
 *
 * If an element exists in the map associated with the specified key, that
 * element is removed and 0 is returned.  If no such element exists, the map is
 * not altered and -1 is returned.
 */
int sgs_map_remove(sgs_map map, const sgs_map_key *key);

#endif  /** #ifndef SGS_MAP_H */
