/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
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
