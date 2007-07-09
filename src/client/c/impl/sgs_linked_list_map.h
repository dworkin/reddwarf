/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations for an implementation of a Linked List-based
 * map data structure.
 */

#ifndef SGS_LINKED_LIST_MAP_H
#define SGS_LINKED_LIST_MAP_H  1

/*
 * STRUCTS & TYPEDEFS
 */
/** have to do some magic here to declare a self-referential struct */
typedef struct sgs_linked_list_map_elt sgs_linked_list_map_elt;

struct sgs_linked_list_map_elt {
    void *key;
    void *value;
    sgs_linked_list_map_elt *next;
};

struct sgs_linked_list_map {
    int (*compare_keys)(const void*, const void*);
    void (*free_map_key)(void*);
    void (*free_map_value)(void*);
    sgs_linked_list_map_elt *head;
};

typedef struct sgs_linked_list_map sgs_linked_list_map;

#endif  /** #ifndef SGS_LINKED_LIST_MAP_H */
