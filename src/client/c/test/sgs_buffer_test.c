/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include "sgs_buffer.h"

/*
 * function: printStats()
 */
void printStats(const sgs_buffer *buf) {
  printf("pos=%u, size=%u, cap=%u, remaining=%u\n", buf->position,
         sgs_buffer_size(buf), sgs_buffer_capacity(buf),
         sgs_buffer_remaining_capacity(buf));
}

/*
 * function: printArr()
 */
void printArr(const char *prefix, const uint8_t *buf, size_t len) {
  size_t i;
  if (prefix != NULL) printf("%s: ", prefix);
  
  for (i=0; i < len; i++)
    printf("%d ", buf[i]);
  
  printf("\n");
}

/*
 * function: main()
 */
int main(int argc, char *argv[]) {
  sgs_buffer *buf;
  uint8_t content[100];
  uint8_t content2[100];
  int result, i;
  
  for (i=0; i < sizeof(content); i++)
    content[i] = i;
  
  for (i=0; i < sizeof(content2); i++)
    content2[i] = 99;
  
  buf = sgs_buffer_new(10);
  if (buf == NULL) { perror("new() failed.\n"); exit(-1); }
  
  result = sgs_buffer_peek(buf, content2, 1);
  printf("PEEK(1) = %d  \t|  ", result);
  printStats(buf);
  
  result = sgs_buffer_write(buf, content, 5);
  printf("WRITE(5) = %d  \t|  ", result);
  printStats(buf);
  
  result = sgs_buffer_peek(buf, content2, 2);
  printf("PEEK(2) = %d  \t|  ", result);
  printStats(buf);
  printArr("results", content2, 10);
  
  result = sgs_buffer_read(buf, content2, 3);
  printf("READ(3) = %d  \t|  ", result);
  printStats(buf);
  printArr("results", content2, 10);
  
  result = sgs_buffer_read(buf, content2, 3);
  printf("READ(3) = %d  \t|  ", result);
  printStats(buf);
  
  printf("internal: ");
  for (i=0; i < 10; i++)
    printf("%d ", buf->buf[i]);
  printf("\n");
  
  result = sgs_buffer_write(buf, content, 9);
  printf("WRITE(9) = %d  \t|  ", result);
  printStats(buf);
  
  result = sgs_buffer_write(buf, content, 8);
  printf("WRITE(8) = %d  \t|  ", result);
  printStats(buf);
  
  printf("internal: ");
  for (i=0; i < 10; i++)
    printf("%d ", buf->buf[i]);
  printf("\n");
  
  result = sgs_buffer_read(buf, content2, 11);
  printf("READ(11) = %d  \t|  ", result);
  printStats(buf);
  
  result = sgs_buffer_read(buf, content2, 10);
  printf("READ(10) = %d  \t|  ", result);
  printStats(buf);
  printArr("results", content2, 10);
  
  sgs_buffer_free(buf);
  
  printf("Goodbye!\n");
  
  return 0;
}
