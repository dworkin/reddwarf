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
 * This file provides an implementation of a linked-list-based map data
 * structure.  Implements functions declared in sgs_map.h.
 */

#include "sgs/config.h"
#include "sgs/map.h"

/* have to do some magic here to declare a self-referential struct */
typedef struct sgs_linked_list_map_elt sgs_linked_list_map_elt;

struct sgs_linked_list_map_elt {
    const void *key;
    void *value;
    sgs_linked_list_map_elt *next;
};

struct sgs_linked_list_map {
    int (*compare_keys)(const void*, const void*);
    sgs_linked_list_map_elt *head;
};

typedef struct sgs_linked_list_map sgs_linked_list_map;

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
 * sgs_map_clear()
 */
void sgs_map_clear(sgs_linked_list_map *map) {
    while (map->head != NULL) {
        sgs_map_remove(map, map->head->key);
    }
}

/*
 * sgs_map_destroy()
 */
void sgs_map_destroy(sgs_linked_list_map *map) {
    sgs_map_clear(map);
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
 * sgs_map_create()
 */
sgs_linked_list_map *
sgs_map_create(int (*comparator)(const void*, const void*))
{
    sgs_linked_list_map *this_map;
    
    this_map = (sgs_linked_list_map*)malloc(sizeof(sgs_linked_list_map));
    if (this_map == NULL) return NULL;
    
    this_map->compare_keys = comparator;
    this_map->head = NULL;
    return this_map;
}

/*
 * sgs_map_put()
 */
int sgs_map_put(sgs_linked_list_map *map, const void *key, void *value) {
    sgs_linked_list_map_elt *ptr = map->head;
    sgs_linked_list_map_elt *prev = NULL;
    
    /** Iterate through list looking for specified key. */
    while (ptr != NULL) {
        if (map->compare_keys(ptr->key, key) == 0) {
            /** Element already exists with this key; replace it. */
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
            ptr->key = NULL;
            ptr->value = NULL;
            free(ptr);
            
            return 0;
        }
        
        prev = ptr;
        ptr = ptr->next;
    }
    
    return -1;
}
