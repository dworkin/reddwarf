/*
 * Copyright (c) 2007 - 2009 Sun Microsystems, Inc.
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

#include "sgs/map.h"

static int compare_ints(const void* a, const void* b);

static int print_get(int key, const void* value) {
    return printf("GET(%d) == %s\n", key,
        (value == NULL) ? "<empty>" : (char*)value);
}

/*
 * function: main()
 */
int main(int argc, char *argv[]) {
    int lookup_key;
    char str1[] = "foobar";
    char str2[] = "chicken soup";
    const void *pbuf;
    int *pkey;
    int *pkey2;
    int result;
    sgs_map *map;
    
    map = sgs_map_create(compare_ints);
    
    if (map == NULL) {
        fprintf(stderr, "Could not create sgs_map.\n");
        exit(-1);
    }
    
    pkey = malloc(sizeof(int));
    *pkey = 100;
    pbuf = sgs_map_get(map, pkey);
    print_get(*pkey, pbuf);
    
    result = sgs_map_put(map, pkey, str1);
    printf("Added element {%d, %s}.  result=%d\n", *pkey, str1, result);
    
    pbuf = sgs_map_get(map, pkey);
    print_get(*pkey, pbuf);
    
    pkey2 = malloc(sizeof(int));
    *pkey2 = 200;
    pbuf = sgs_map_get(map, pkey2);
    print_get(*pkey2, pbuf);
    
    result = sgs_map_put(map, pkey2, str2);
    printf("Added element {%d, %s}.  result=%d\n", *pkey2, str2, result);
    
    pbuf = sgs_map_get(map, pkey2);
    print_get(*pkey2, pbuf);
    
    lookup_key = 100;
    pbuf = sgs_map_get(map, &lookup_key);
    print_get(lookup_key, pbuf);
    
    lookup_key = 300;
    result = sgs_map_remove(map, &lookup_key);
    printf("REMOVE(%d) == %d\n", lookup_key, result);
    
    lookup_key = 100;
    result = sgs_map_remove(map, &lookup_key);
    printf("REMOVE(%d) == %d\n", lookup_key, result);
    
    lookup_key = 100;
    pbuf = sgs_map_get(map, &lookup_key);
    print_get(lookup_key, pbuf);
    
    lookup_key = 200;
    pbuf = sgs_map_get(map, &lookup_key);
    print_get(lookup_key, pbuf);
  
    sgs_map_clear(map);
    printf("EMPTY()\n");
  
    lookup_key = 100;
    pbuf = sgs_map_get(map, &lookup_key);
    print_get(lookup_key, pbuf);
  
    lookup_key = 200;
    pbuf = sgs_map_get(map, &lookup_key);
    print_get(lookup_key, pbuf);
  
    sgs_map_destroy(map);
    free(pkey2);
    free(pkey);
  
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
