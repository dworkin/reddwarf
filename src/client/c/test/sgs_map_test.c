/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

#include <stdlib.h>  /** included for exit() */
#include <stdio.h>
#include <sys/time.h>
#include <unistd.h>
#include "sgs_map.h"

static int compare_ints(const void* a, const void* b);

/*
 * function: main()
 */
int main(int argc, char *argv[]) {
    int lookup_key;
    char str1[] = "foobar";
    char str2[] = "chicken soup";
    void *pbuf;
    void *pkey;
    int result;
    sgs_map *map;
    
    map = sgs_map_new(compare_ints, free, NULL);
    
    if (map == NULL) {
        fprintf(stderr, "Could not create sgs_map.\n");
        exit(-1);
    }
    
    pkey = malloc(sizeof(int));
    *(int*)pkey = 100;
    pbuf = sgs_map_get(map, pkey);
    printf("GET(%d) == %d.  value=%s\n", *(int*)pkey, (pbuf != NULL), (char*)pbuf);
    
    result = sgs_map_put(map, pkey, str1);
    printf("Added element {%d, %s}.  result=%d\n", *(int*)pkey, str1, result);
    
    pbuf = sgs_map_get(map, pkey);
    printf("GET(%d) == %d.  value=%s\n", *(int*)pkey, (pbuf != NULL), (char*)pbuf);
    
    pkey = malloc(sizeof(int));
    *(int*)pkey = 200;
    pbuf = sgs_map_get(map, pkey);
    printf("GET(%d) == %d.  value=%s\n", *(int*)pkey, (pbuf != NULL), (char*)pbuf);
    
    result = sgs_map_put(map, pkey, str2);
    printf("Added element {%d, %s}.  result=%d\n", *(int*)pkey, str2, result);
    
    pbuf = sgs_map_get(map, pkey);
    printf("GET(%d) == %d.  value=%s\n", *(int*)pkey, (pbuf != NULL), (char*)pbuf);
    
    lookup_key = 100;
    pbuf = sgs_map_get(map, &lookup_key);
    printf("GET(%d) == %d.  value=%s\n", lookup_key, (pbuf != NULL), (char*)pbuf);
    
    lookup_key = 300;
    result = sgs_map_remove(map, &lookup_key);
    printf("REMOVE(%d) == %d\n", lookup_key, result);
    
    lookup_key = 100;
    result = sgs_map_remove(map, &lookup_key);
    printf("REMOVE(%d) == %d\n", lookup_key, result);
    
    lookup_key = 100;
    pbuf = sgs_map_get(map, &lookup_key);
    printf("GET(%d) == %d.  value=%s\n", lookup_key, (pbuf != NULL), (char*)pbuf);
    
    lookup_key = 200;
    pbuf = sgs_map_get(map, &lookup_key);
    printf("GET(%d) == %d.  value=%s\n", lookup_key, (pbuf != NULL), (char*)pbuf);
  
    sgs_map_empty(map);
    printf("EMPTY()\n");
  
    lookup_key = 100;
    pbuf = sgs_map_get(map, &lookup_key);
    printf("GET(%d) == %d.  value=%s\n", lookup_key, (pbuf != NULL), (char*)pbuf);
  
    lookup_key = 200;
    pbuf = sgs_map_get(map, &lookup_key);
    printf("GET(%d) == %d.  value=%s\n", lookup_key, (pbuf != NULL), (char*)pbuf);
  
    sgs_map_free(map);
  
    printf("Goodbye!\n");
  
    return 0;
}


/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 * (these are functions that can only be called within this file)
 */

static int compare_ints(const void* a, const void* b)
{
    int int_a, int_b;
    int_a = *(int*)a;
    int_b = *(int*)b;
  
    if (int_a == int_b) return 0;
    return -1;
}
