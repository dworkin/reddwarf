#include "sgs/map.h"

static int compare_ints(const void* a, const void* b);

static int print_get(int key, void* value) {
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
    void *pbuf;
    int *pkey;
    int result;
    sgs_map *map;
    
    map = sgs_map_create(compare_ints, free, NULL);
    
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
    
    pkey = malloc(sizeof(int));
    *pkey = 200;
    pbuf = sgs_map_get(map, pkey);
    print_get(*pkey, pbuf);
    
    result = sgs_map_put(map, pkey, str2);
    printf("Added element {%d, %s}.  result=%d\n", *pkey, str2, result);
    
    pbuf = sgs_map_get(map, pkey);
    print_get(*pkey, pbuf);
    
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
