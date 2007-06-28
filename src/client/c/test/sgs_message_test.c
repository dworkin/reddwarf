/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include "sgs_message.h"

static void printBuf(uint8_t buf[], size_t len) {
  int i;
  
  printf("{ ");
  
  for (i=0; i < len; i++) {
    printf("%d ", buf[i]);
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
  
  msg = sgs_msg_create(1024);
  
  if (msg == NULL) {
    fprintf(stderr, "Could not create sgs_message.\n");
    exit(-1);
  }
  
  result = sgs_msg_init(msg, SGS_OPCODE_LOGIN_REQUEST, SGS_APPLICATION_SERVICE);
  printf("INIT() == %d\n", result);
  
  content[0] = 10;
  content[1] = 11;
  content[2] = 12;
  content[3] = 13;
  content[4] = 14;
  
  result = sgs_msg_serialize(msg, buf, sizeof(buf));
  printf("SER == %d\n", result);
  printBuf(buf, result);
  
  result = sgs_msg_add_arb_content(msg, content, 5);
  printf("ADD-ARB() == %d\n", result);
  
  result = sgs_msg_serialize(msg, buf, sizeof(buf));
  printf("SER == %d\n", result);
  printBuf(buf, result);
  
  result = sgs_msg_add_fixed_content(msg, content, 5);
  printf("ADD-FIXED() == %d\n", result);
  
  result = sgs_msg_serialize(msg, buf, sizeof(buf));
  printf("SER == %d\n", result);
  printBuf(buf, result);
  
  result = sgs_msg_deserialize(msg, buf, sizeof(buf));
  printf("DESER == %d\n", result);
  
  result = sgs_msg_serialize(msg, buf, sizeof(buf));
  printf("SER == %d\n", result);
  printBuf(buf, result);
  
  sgs_msg_destroy(msg);
  
  printf("Goodbye!\n");
  
  return 0;
}
