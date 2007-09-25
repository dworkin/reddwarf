/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

/*
 * Tests sgs/id.h
 */

#include "sgs/id.h"
#include "sgs/hex_utils.h"

void test1() {
    uint8_t bytes[] = { 1, 2, 3 };
    sgs_id* id;
    
    id = sgs_id_create(bytes, sizeof(bytes), NULL);
    if (id == NULL) {
        sgs_id_destroy(id);
        perror("init()");
    }
    
    printf("bytelen = %ld\n", sgs_id_get_byte_len(id));
    printf("memcmp = %d\n", memcmp(bytes, sgs_id_get_bytes(id),
               sgs_id_get_byte_len(id)));
    sgs_id_destroy(id);
}

void test2() {
    const char* hex = "0102";
    uint8_t bytebuf[1024];
    sgs_id* id;
    int result;
    size_t i;
    
    result = hextobytes(hex, bytebuf);
    
    if (result == -1) {
        printf("Error: invalid ID string (%s).\n", hex);
        return;
    }
    
    printf("hextobytes() returned %d\n", result);
    
    if ((id = sgs_id_create(bytebuf, strlen(hex)/2, NULL)) == NULL) {
        printf("Error: invalid ID string (%s).\n", hex);
        return;
    }
    
    printf("byte: { ");
    
    for (i=0; i < sgs_id_get_byte_len(id); i++) {
        if (i > 0) printf(", ");
        printf("%d", *(sgs_id_get_bytes(id) + i));
    }
    
    printf(" }\n");
    sgs_id_destroy(id);
}

/*
 * function: main()
 */
int main(int argc, char *argv[]) {
    printf("bytes test:\n");
    test1();
    printf("\nhex test:\n");
    test2();
}
