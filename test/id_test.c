/*
 * Copyright (c) 2007 - 2009, Sun Microsystems, Inc.
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

/* Taken from message_test.c (should really be put into a shared utility 
 * module); generates a set of random bytes of random lengths between 1 and 20
 * that can be used to create a set of ids
 */
void generateIds(sgs_id **testIds){
    int i, j, k, len;
    uint8_t buffer[10];
    long sourcebuf[3];
    
    for (i = 0; i < 10; i++){
        len = random()%10;
        len++;
        for (k = 0; k < 3; k++){
            sourcebuf[k] = random();
        }
        memcpy(buffer, sourcebuf, len);
        *(testIds + i) = sgs_id_create(buffer, len);
    }
}


int main(int argc, char *argv[]) {
    sgs_id *testIds[10];
    sgs_id *cloneIds[10];
    int i, j, numBad, numBad1;
    
    numBad = 0;
    numBad1 = 0;
    
    for (i = 0; i < 20; i++){
        generateIds(testIds);
        for (j = 0; j < 10; j++){
            cloneIds[j] = sgs_id_duplicate(testIds[j]);
        }
        /* test to make sure the clones are identical to the original*/
        for (j = 0; j< 10; j++){
            if (0 != sgs_id_compare(testIds[j], cloneIds[j])){
                numBad++;
            }
        }
        if (numBad != 0){
            printf("%d failures of equality on clones\n", numBad);
       } 
            
        /*Test to make sure that compare doesn't always say they are 
         *the same
         */
        for (j= 0; j < 10; j++){
            if (0 == sgs_id_compare(testIds[j], cloneIds[(j+1)%10])){
                numBad1++;
            }
        }
        if (numBad1 != 0){
            printf("%d false positive equality in ids\n", numBad);
        }
            
        for (j = 0; j < 10; j++){
            sgs_id_destroy(testIds[j]);
            sgs_id_destroy(cloneIds[j]);
        }
    }
    if ((numBad == 0)&&(numBad1 == 0)){
        printf("Id tests passed\n");
    } else {
        printf("Id tests failed with %d failures\n", numBad + numBad1);
    }
    
    return (numBad + numBad1);
    
}
