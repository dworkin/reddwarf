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

static int testUint16(sgs_message *pmsg, int numTests, int silent);
static int testUint32(sgs_message *pmsg, int numTests, int silent);
static int testStrings(sgs_message *pmsg, int numTests, int silent);


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
    uint8_t buf[4096];
    uint8_t content[100];
    int result;
  
    result = sgs_msg_init(&msg, buf, sizeof(buf),
        SGS_OPCODE_LOGIN_REQUEST);
  
    printf("INIT() == %d\n", result);
  
    if ((result = testUint16(&msg, 200, 1)) == 0)
        printf("testUint16 passed \n");
    else
        printf("%d failures in testUint16\n", result);
    
    result = sgs_msg_init(&msg, buf, sizeof(buf),
        SGS_OPCODE_LOGIN_REQUEST);
    
    if ((result = testUint32(&msg, 200, 1))== 0)
        printf("test Uint32 passed\n");
    else
        printf("%d failures in testUint32\n", result);
    
    result = sgs_msg_init(&msg, buf, sizeof(buf), SGS_OPCODE_LOGIN_REQUEST);
    if ((result = testStrings(&msg, 20, 0)) == 0){
        printf("test Strings passed\n");
    } else 
        printf("%d failures in testStrings\n", result);
  
    printf("Goodbye!\n");
  
    return 0;
}

/*
 *Check the writing and reading of uint16_t types. The test will write a 
 *number of uint16_t values to the message buffer that is handed in, and will
 *then read the same number of values. The values read will be compared to 
 *the values written. If they are the same, the function will return 0; if they
 *are different, the function will return the number of values which are different.
 *
 *If the value of silent is 1, the test will run without printing out 
 *anything. If the value of silent is 0, then the test will print the values
 *that are written to the message buffer, and the values that are read from
 *the buffer.
 */

int testUint16(sgs_message *pmsg, int numTests, int silent){
    uint16_t *writenums, *readnums;
    int check, offset, i;
    
    writenums = malloc(numTests * sizeof(uint16_t));
    readnums = malloc(numTests * sizeof(uint16_t));
    if ((readnums == NULL) || (writenums == NULL)){
        printf ("unable to allocate test buffers\n");
        return -1;
    }
    for (i = 0; i < numTests; i++){
        writenums[i] = random();
        check =sgs_msg_add_uint16(pmsg, writenums[i]);
        if (check == -1)
            printf("unable to write the %dth number\n", i);
    }
    
    offset = 3;
    for (i= 0; i < numTests; i++){
        check = sgs_msg_read_uint16(pmsg, offset, readnums+i);
        if (check < 0){
            printf("unable to read the %dth number\n", i);
            offset += 2;
        } else 
            offset += check;
    }
    
    check = 0;
    for (i = 0; i < numTests; i++){
        if (writenums[i] != readnums[i]){
            check++;
        }
        if (silent == 0){
            printf("the value written is %d, the value read is %d\n", 
                    writenums[i], readnums[i]);
        }
        
    }
    free(writenums);
    free(readnums);
    return check;
}


/*
 *Check the writing and reading of uint132_t types. The test will write a 
 *number of uint32_t values to the message buffer that is handed in, and will
 *then read the same number of values. The values read will be compared to 
 *the values written. If they are the same, the function will return 0; if they
 *are different, the function will return the number of values which are different.
 *
 *If the value of silent is 1, the test will run without printing out 
 *anything. If the value of silent is 0, then the test will print the values
 *that are written to the message buffer, and the values that are read from
 *the buffer.
 */

int testUint32(sgs_message *pmsg, int numTests, int silent){
    uint32_t *writenums, *readnums;
    int check, offset, i;
    
    writenums = malloc(numTests * sizeof(uint32_t));
    readnums = malloc(numTests * sizeof(uint32_t));
    if ((readnums == NULL) || (writenums == NULL)) {
        printf("unable to allocate test buffers\n");
        return -1;
    }
    for (i = 0; i < numTests; i++){
        writenums[i] = random();
        check =sgs_msg_add_uint32(pmsg, writenums[i]);
        if (check == -1)
            printf("unable to write the %dth number\n", i);
    }
    
    offset = 3;
    for (i= 0; i < numTests; i++){
        check = sgs_msg_read_uint32(pmsg, offset, readnums+i);
        if (check < 0){
            printf("unable to read the %dth number\n", i);
            offset += 4;
        } else 
            offset += check;
    }
    
    check = 0;
    for (i = 0; i < numTests; i++){
        if (writenums[i] != readnums[i]){
            check++;
        }
        if (silent == 0){
            printf("the value written is %d, the value read is %d\n", 
                    writenums[i], readnums[i]);
        }
        
    }
    free(readnums);
    free(writenums);
    return check;
}


int testStrings(sgs_message *pmsg, int numTests, int silent){
    int i, check, offset;
    
    char *inBuffer[] = {
        "A day for firm decisions!!!!!  Or is it?",
        "A few hours grace before the madness begins again.",
        "Better hope the life-inspector doesn't come around while you have your life in such a mess.",
        "Don't let your mind wander -- it's too little to be let out alone.",
        "Everything that you know is wrong, but you can be straightened out.",
        "Generosity and perfection are your everlasting goals.",
        "It's a very *__UN*lucky week in which to be took dead.-- Churchy La Femme",
        "Things will be bright in P.M.  A cop will shine a light in your face.",
        "Today's weirdness is tomorrow's reason why.-- Hunter S. Thompson",
        "You are confused; but this is your normal state."
    };
    
    printf("made it to allocation of outbuffer\n");
    char **outbuffer = malloc (sizeof(char *) * numTests);
    printf("allocated outbuffer\n");
    for (i = 0; i < numTests; i++){
        check = sgs_msg_add_string(pmsg, inBuffer[i%10]);
        if (check < 0)
            printf ("Unable to add %dth string %s\n", i, inBuffer[i%10]);
    }
    printf("loaded the output buffer\n");
    offset = 3;
    for (i = 0; i < numTests; i++){
        check = sgs_msg_read_string(pmsg, offset, outbuffer[i]);
        if (check < 0){
            printf ("unable to read the %dth string\n", i);
        }
        offset += check;    
    }
    printf("read the message buffers\n");
    
    check = offset = 0;
    for (i = 0; i < numTests; i++){
        offset = strcmp(inBuffer+[i%10], outbuffer[i]);
        if (silent == 0){
            printf("input string = %s\noutput string = %s\n", inBuffer[i%10], outbuffer[i]);
        }
        if (offset != 0) check++;
    }
    return check;
}