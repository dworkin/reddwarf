/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

/*
 * Tests sgs_id.h
 */

#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "sgs_id.h"
#include "sgs_hex_utils.h"

void test1() {
    uint8_t bytes[] = { 1, 2, 3 };
    sgs_id id;
    int result;
    
    result = sgs_id_init(&id, bytes, sizeof(bytes));
    printf("init() == %d\n", result);
    if (result == -1) perror("init()");
    
    printf("bytelen = %ld\n", sgs_id_get_byte_len(&id));
    printf("memcmp = %d\n", memcmp(bytes, sgs_id_get_bytes(&id),
               sgs_id_get_byte_len(&id)));
}

void test2() {
    const char* hex = "02";
    uint8_t bytebuf[1024];
    sgs_id id;
    int result, i;
    
    result = hextobytes(hex, bytebuf);
    
    if (result == -1) {
        printf("Error: invalid ID string (%s).\n", hex);
        return;
    }
    
    printf("hextobytes() returned %d\n", result);
    
    if (sgs_id_init(&id, bytebuf, strlen(hex)/2) == -1) {
        printf("Error: invalid ID string (%s).\n", hex);
        return;
    }
    
    printf("byte: { ");
    
    for (i=0; i < sgs_id_get_byte_len(&id); i++) {
        printf("%d", *(sgs_id_get_bytes(&id) + i));
        if (i > 0) printf(", ");
    }
    
    printf(" }\n");
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
