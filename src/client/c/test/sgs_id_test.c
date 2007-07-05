/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Tests sgs_id.h
 */

#include <stdio.h>
#include "sgs_id.h"

/*
 * function: main()
 */
int main(int argc, char *argv[]) {
    char hex[] = "08";
    sgs_id id;
    int result;
  
    result = sgs_id_init_from_hex(hex, &id);
    printf("INIT-HEX(\"%s\") == %d\n", hex, result);
    if (result == -1) perror("init_from_hex()");
  
    printf("PRINT: %s\n", sgs_id_printable(&id));
}
