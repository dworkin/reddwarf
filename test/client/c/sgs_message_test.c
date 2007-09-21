/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include "sgs_message.h"

/*
 * function: printMsg()
 */
static void printMsg(sgs_message *pmsg) {
    const uint8_t *data = sgs_msg_get_bytes(pmsg);
    int i;
  
    printf("{ ");
  
    for (i=0; i < sgs_msg_get_size(pmsg); i++) {
        printf("%d ", data[i]);
    }
  
    printf(" }\n");
}

/*
 * function: main()
 */
int main(int argc, char *argv[]) {
    sgs_message msg;
    uint8_t buf[1024];
    uint8_t content[100];
    int result;
  
    result = sgs_msg_init(&msg, buf, sizeof(buf), SGS_OPCODE_LOGIN_REQUEST,
        SGS_APPLICATION_SERVICE);
  
    printf("INIT() == %d\n", result);
  
    content[0] = 10;
    content[1] = 11;
    content[2] = 12;
    content[3] = 13;
    content[4] = 14;
  
    result = sgs_msg_add_arb_content(&msg, content, 5);
    printf("ADD-ARB() == %d\n", result);
    printMsg(&msg);
  
    result = sgs_msg_add_fixed_content(&msg, content, 5);
    printf("ADD-FIXED() == %d\n", result);
    printMsg(&msg);
  
    result = sgs_msg_deserialize(&msg, buf, sizeof(buf));
    printf("DESER == %d\n", result);
    printMsg(&msg);
  
    printf("Goodbye!\n");
  
    return 0;
}
