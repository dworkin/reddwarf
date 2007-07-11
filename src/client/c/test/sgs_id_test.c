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
#include "sgs_id.h"

/*
 * function: main()
 */
int main(int argc, char *argv[]) {
    char hex[] = "08";
    uint8_t bytes[] = { 1, 2, 3 };
    sgs_id id;
    int result;
    
    result = sgs_id_init(&id, bytes, sizeof(bytes));
    printf("init() == %d\n", result);
    if (result == -1) perror("init()");
    
    printf("bytelen = %d\n", sgs_id_get_byte_len(&id));
    printf("memcmp = %d\n", memcmp(bytes, sgs_id_get_bytes(&id),
               sgs_id_get_byte_len(&id)));
}
