/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides an implementation of a linked-list-based map data
 * structure.  Implements functions declared in sgs_map.h.
 */

#include <stdlib.h>
#include <string.h>
#include "sgs_map.h"
#include "sgs_linked_list_map.h"

/*
 * FUNCTION IMPLEMENTATIONS
 */

/*
 * sgs_map_contains()
 */
int sgs_map_contains(const sgs_linked_list_map *map, const void *key) {
    sgs_linked_list_map_elt *ptr = map->head;
    
    while (ptr != NULL) {
        if (map->compare_keys(ptr->key, key) == 0) {
            return 1;
        }
        
        ptr = ptr->next;
    }
    
    return 0;
}

/*
 * sgs_map_empty()
 */
void sgs_map_empty(sgs_linked_list_map *map) {
    while (map->head != NULL) {
        sgs_map_remove(map, map->head->key);
    }
}

/*
 * sgs_map_free()
 */
void sgs_map_free(sgs_linked_list_map *map) {
    sgs_map_empty(map);
    free(map);
}

/*
 * sgs_map_get()
 */
void *sgs_map_get(const sgs_linked_list_map *map, const void *key) {
    sgs_linked_list_map_elt *ptr = map->head;
    
    while (ptr != NULL) {
        if (map->compare_keys(ptr->key, key) == 0) {
            return ptr->value;
        }
        
        ptr = ptr->next;
    }
    
    return NULL;
}

/*
 * sgs_map_new()
 */
sgs_linked_list_map *sgs_map_new(int (*comparator)(const void*, const void*),
    void (*free_map_key)(void*), void (*free_map_value)(void*))
{
    sgs_linked_list_map *this_map;
    
    this_map = (sgs_linked_list_map*)malloc(sizeof(sgs_linked_list_map));
    if (this_map == NULL) return NULL;
    
    this_map->compare_keys = comparator;
    this_map->free_map_key = free_map_key;
    this_map->free_map_value = free_map_value;
    this_map->head = NULL;
    return this_map;
}

/*
 * sgs_map_put()
 */
int sgs_map_put(sgs_linked_list_map *map, void *key, void *value) {
    sgs_linked_list_map_elt *ptr = map->head;
    sgs_linked_list_map_elt *prev = NULL;
    
    /** Iterate through list looking for specified key. */
    while (ptr != NULL) {
        if (map->compare_keys(ptr->key, key) == 0) {
            /** Element already exists with this key; replace it. */
            if (map->free_map_key != NULL) map->free_map_key(ptr->key);
            if (map->free_map_value != NULL) map->free_map_value(ptr->value);
            
            ptr->key = key;
            ptr->value = value;
            return 0;
        }
        
        prev = ptr;
        ptr = ptr->next;
    }
  
    /** No elements exist with this key; create a new one and add to the end. */
    if (prev == NULL) {
        map->head = (sgs_linked_list_map_elt *)
            malloc(sizeof(sgs_linked_list_map_elt));
    
        ptr = map->head;
    }
    else {
        prev->next = (sgs_linked_list_map_elt *)
            malloc(sizeof(sgs_linked_list_map_elt));
    
        ptr = prev->next;
    }
  
    if (ptr == NULL) return -1;  /** malloc() failed */
  
    ptr->key = key;
    ptr->value = value;
    ptr->next = NULL;
  
    return 1;
}

/*
 * sgs_map_remove()
 */
int sgs_map_remove(sgs_linked_list_map *map, const void *key) {
    sgs_linked_list_map_elt *ptr = map->head;
    sgs_linked_list_map_elt *prev = NULL;
  
    while (ptr != NULL) {
        if (map->compare_keys(ptr->key, key) == 0) {
            /**
             * Update the "next" pointer from the previous element to skip over
             * this element.
             */
            if (prev == NULL)
                map->head = ptr->next;
            else
                prev->next = ptr->next;
            
            /** delete this element */
            if (map->free_map_key != NULL) map->free_map_key(ptr->key);
            if (map->free_map_value != NULL) map->free_map_value(ptr->value);
            free(ptr);
            
            return 0;
        }
        
        prev = ptr;
        ptr = ptr->next;
    }
    
    return -1;
}
