/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * BASE64Encoder.java
 */
package com.sun.multicast.util;

/**
 * A BASE64 encoder/decoder. This class provides methods that encode and 
 * decode byte arrays to and from the BASE64 encoding (as defined in RFC 2045). 
 * The only aspect of BASE64 encoding that is not supported is line folding. 
 * No line terminators are created or expected. This feature could be added 
 * fairly easily.
 */
public class BASE64Encoder {

    /**
     * Perform BASE64 encoding (without line endings).
     * @param in input bytes
     * @return output bytes
     */
    public static byte[] encode(byte[] in) {
        int inLen = in.length;
        int chunks = (inLen + 2) / 3;
        int wholeChunks = inLen / 3;
        int outLen = chunks * 4;
        byte[] out = new byte[outLen];
        int outInd = 0;
        int inInd = 0;
        int wholeChunksLeft = wholeChunks;
        int b1;
        int b2;
        int b3;

        // Process whole chunks

        while (wholeChunksLeft-- != 0) {
            b1 = in[inInd++] & 255;
            b2 = in[inInd++] & 255;
            b3 = in[inInd++] & 255;
            out[outInd++] = BASE64EncodeTable[b1 >> 2];
            out[outInd++] = BASE64EncodeTable[((b1 & 3) << 4) | (b2 >> 4)];
            out[outInd++] = BASE64EncodeTable[((b2 & 15) << 2) | (b3 >> 6)];
            out[outInd++] = BASE64EncodeTable[b3 & 63];
        }

        // Process partial chunks

        switch (inLen - inInd) {

        case 2: 
            b1 = in[inInd++] & 255;
            b2 = in[inInd++] & 255;
            out[outInd++] = BASE64EncodeTable[b1 >> 2];
            out[outInd++] = BASE64EncodeTable[((b1 & 3) << 4) | (b2 >> 4)];
            out[outInd++] = BASE64EncodeTable[(b2 & 15) << 2];
            out[outInd++] = EQUALS_SIGN;

            break;

        case 1: 
            b1 = in[inInd++] & 255;
            out[outInd++] = BASE64EncodeTable[b1 >> 2];
            out[outInd++] = BASE64EncodeTable[(b1 & 3) << 4];
            out[outInd++] = EQUALS_SIGN;
            out[outInd++] = EQUALS_SIGN;

            break;

        case 0: 

        default: 
            break;
        }

        // An assert

        if (outInd != outLen) {
            throw new NullPointerException();
        } 

        return (out);
    }

    /**
     * Perform BASE64 decoding (without line endings).
     * @param in input bytes
     * @return output bytes
     * @exception com.sun.multicast.util.BadBASE64Exception if 
     *  the encoded data is corrupt
     */
    public static byte[] decode(byte[] in) throws BadBASE64Exception {
        int inLen = in.length;

        if (inLen == 0) {
            return (new byte[0]);
        } 
        if ((inLen % 4) != 0) {
            throw new BadBASE64Exception();
        } 

        int chunks = inLen / 4;
        int wholeChunks = chunks;
        int outLen = chunks * 3;

        if (in[inLen - 1] == EQUALS_SIGN) {
            wholeChunks--;
            outLen--;

            if (in[inLen - 2] == EQUALS_SIGN) {
                outLen--;
            } 
        }

        byte[] out = new byte[outLen];
        int outInd = 0;
        int inInd = 0;
        int wholeChunksLeft = wholeChunks;
        int b1;
        int b2;
        int b3;
        int b4;

        try {

            // Process whole chunks

            while (wholeChunksLeft-- != 0) {
                b1 = BASE64DecodeTable[in[inInd++]];
                b2 = BASE64DecodeTable[in[inInd++]];
                b3 = BASE64DecodeTable[in[inInd++]];
                b4 = BASE64DecodeTable[in[inInd++]];

                if ((b1 == -1) || (b2 == -1) || (b3 == -1) || (b4 == -1)) {
                    throw new BadBASE64Exception();
                } 

                out[outInd++] = (byte) ((b1 << 2) | (b2 >> 4));
                out[outInd++] = (byte) (((b2 & 15) << 4) | (b3 >> 2));
                out[outInd++] = (byte) (((b3 & 3) << 6) | b4);
            }

            // Process partial chunks

            switch (outLen - outInd) {

            case 2: 
                b1 = BASE64DecodeTable[in[inInd++]];
                b2 = BASE64DecodeTable[in[inInd++]];
                b3 = BASE64DecodeTable[in[inInd++]];

                if ((b1 == -1) || (b2 == -1) || (b3 == -1)) {
                    throw new BadBASE64Exception();
                } 

                out[outInd++] = (byte) ((b1 << 2) | (b2 >> 4));
                out[outInd++] = (byte) (((b2 & 15) << 4) | (b3 >> 2));

                break;

            case 1: 
                b1 = BASE64DecodeTable[in[inInd++]];
                b2 = BASE64DecodeTable[in[inInd++]];

                if ((b1 == -1) || (b2 == -1)) {
                    throw new BadBASE64Exception();
                } 

                out[outInd++] = (byte) ((b1 << 2) | (b2 >> 4));

                break;

            case 0: 

            default: 
                break;
            }
        } catch (IndexOutOfBoundsException e) {
            throw new BadBASE64Exception();     // Must be an input byte < 0
        }

        return (out);
    }

    private static final byte EQUALS_SIGN = 61;

    // This table maps values 0-63 to BASE64 encoded bytes (0-127)

    private static final byte BASE64EncodeTable[] = {
        65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 
        82, 83, 84, 85, 86, 87, 88, 89, 90, 97, 98, 99, 100, 101, 102, 103, 
        104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 
        118, 119, 120, 121, 122, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 43, 
        47
    };

    // This table maps BASE64 encoded bytes 0-127 to values 0-63 or 
    // -1 for invalid

    private static final byte BASE64DecodeTable[] = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 
        -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63, 52, 53, 54, 
        55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 
        4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 
        23, 24, 25, -1, -1, -1, -1, -1, -1, 26, 27, 28, 29, 30, 31, 32, 33, 
        34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 
        51, -1, -1, -1, -1, -1
    };
}
