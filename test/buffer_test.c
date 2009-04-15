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

#include "sgs/buffer.h"

/*
 * function: printStats()
 */
void printStats(const sgs_buffer *buf) {
    printf("pos=%lu, size=%lu, cap=%lu, remaining=%lu\n",
        sgs_buffer_position(buf),
        sgs_buffer_size(buf), sgs_buffer_capacity(buf),
        sgs_buffer_remaining(buf));
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
    int result;
    size_t i;
  
    for (i=0; i < sizeof(content); i++)
        content[i] = i;
  
    for (i=0; i < sizeof(content2); i++)
        content2[i] = 99;
  
    buf = sgs_buffer_create(10);
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
    sgs_buffer_dump(buf);
    printf("\n");
  
    result = sgs_buffer_write(buf, content, 9);
    printf("WRITE(9) = %d  \t|  ", result);
    printStats(buf);
  
    result = sgs_buffer_write(buf, content, 8);
    printf("WRITE(8) = %d  \t|  ", result);
    printStats(buf);
  
    printf("internal: ");
    sgs_buffer_dump(buf);
    printf("\n");
  
    result = sgs_buffer_read(buf, content2, 11);
    printf("READ(11) = %d  \t|  ", result);
    printStats(buf);
  
    result = sgs_buffer_read(buf, content2, 10);
    printf("READ(10) = %d  \t|  ", result);
    printStats(buf);
    printArr("results", content2, 10);
  
    sgs_buffer_destroy(buf);
  
    printf("Goodbye!\n");
  
    return 0;
}
