/*
 * Copyright (c) 2007, Sun Microsystems, Inc.
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

#include "sgs/private/message.h"

/*
 * function: printMsg()
 */
static void printMsg(const sgs_message *pmsg) {
    const uint8_t *data = sgs_msg_get_bytes(pmsg);
    size_t i;
  
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
  
    result = sgs_msg_init(&msg, buf, sizeof(buf),
        SGS_OPCODE_LOGIN_REQUEST, SGS_APPLICATION_SERVICE);
  
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
