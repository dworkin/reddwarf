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
 * TRAMSeqNumber.java
 * 
 * Module Description: Defines the TRAM Sequence number class.
 */
package com.sun.multicast.reliable.transport.tram;

/*
 * TRAM Sequence Number Class
 */

class TRAMSeqNumber {
    private int seqNumber = 1;
    private static long MASK = 0xffffffffL;
    private static final long ROLLOVERMAX = 0x7fffffffL;

    public TRAMSeqNumber() {

    // nothing.

    }

    /**
     * Constructor.
     * Sets the initial sequence number to be the passed number.
     */
    public TRAMSeqNumber(int startSeq) {
        seqNumber = startSeq;
    }

    /*
     * Test method.
     */

    public static void main(String[] args) {
        TRAMSeqNumber tramSeq = null;
        int numberToComp = 0;
        int cmp, seq;

        if (args.length != 2) {
            System.out.println(
		"Usage: java TRAMSeqNumber baseNumber compareNumber");
        }

        seq = Integer.parseInt(args[0]);
        cmp = Integer.parseInt(args[1]);
        tramSeq = new TRAMSeqNumber(Integer.parseInt(args[0]));

        if (tramSeq.isEqualTo(cmp)) {
            System.out.println(seq + " = " + cmp);
        } 
        if (tramSeq.isLessThan(cmp)) {
            System.out.println(seq + " < " + cmp);
        } 
        if (tramSeq.isGreaterThan(cmp)) {
            System.out.println(seq + " > " + cmp);
        } 
        if (tramSeq.isLessThanOrEqual(cmp)) {
            System.out.println(seq + " <= " + cmp);
        } 
        if (tramSeq.isGreaterThanOrEqual(cmp)) {
            System.out.println(seq + " >= " + cmp);
        } 

        System.out.println("TRAMCompareSeqNumber");

        int x = tramSeq.compareSeqNumber(cmp);

        if (x == 0) {
            System.out.println(seq + " = " + cmp);
        } else if (x == 1) {
            System.out.println(seq + " > " + cmp);
        } else {
            System.out.println(seq + " < " + cmp);
        }

        System.out.println("Performing the 20 Increments. Start # " 
                           + tramSeq.getSeqNumber());

        for (int k = 1; k <= 20; k++) {
            tramSeq.incrSeqNumber();
            System.out.println("          " + tramSeq.getSeqNumber());
        }

        System.out.println("Performing the 20 Decrements. Start# " 
                           + tramSeq.getSeqNumber());

        for (int k = 1; k <= 20; k++) {
            tramSeq.decrSeqNumber();
            System.out.println("          " + tramSeq.getSeqNumber());
        }

        System.out.println("Current Seq Number " + tramSeq.getSeqNumber());
        tramSeq.add(10);
        System.out.println("Sequence Number + 10 = " + tramSeq.getSeqNumber());
        System.out.println("Current Seq Number " + tramSeq.getSeqNumber());
        tramSeq.subtract(10);
        System.out.println("Sequence Number - 10 = " + tramSeq.getSeqNumber());
    }

    /**
     * gets the current sequence number value stored in the object.
     * 
     * @return int - the current sequence number value.
     */
    public final int getSeqNumber() {
        return seqNumber;
    }

    /*
     * Explicitly set the sequence number.
     */

    public void setSeqNumber(int value) {
        seqNumber = value;
    }

    /**
     * Decrements the current sequence number to a valid previous sequence
     * number value.
     */
    public final void incrSeqNumber() {
        seqNumber++;
    }

    /**
     * Decrements the current sequence number to a valid previous sequence
     * number value.
     */
    public final void decrSeqNumber() {
        seqNumber--;
    }

    /**
     * Returns the immediate previous valid sequence number with reference to
     * the current sequence number. The current sequence number value remains
     * unchanged.
     * 
     * @return int - The required immediate previous valid sequence number.
     */
    public final int getPreviousSeqNumber() {
        int tmp = --seqNumber;

        return tmp;
    }

    /**
     * Compares the internal sequence number with the passed in sequence
     * number. The result returned is very much like a - b (a is the
     * internal value and b is the comparing/passed value.
     * the returned value is 0 if they are equal, -1 if the internal
     * value is less than the comparing value and 1 if the internal
     * value is greater than the comparing value.
     * 
     * @param - int comparing sequence number.
     * @return - int : 0  if internal and comparing values are equal.
     * 1  if internal value is greater than comparing value.
     * -1 if comparing value is greater than internal value.
     * 
     */
    public int compareSeqNumber(int seqNumberToCompare) {
        if (seqNumber == seqNumberToCompare) {
            return 0;
        } 

        /*
         * First determine if both the numbers are on the same side of the
         * sign scale - i.e., check if both the numbers are +ve numbers or
         * -ve numbers. In this case don't need to worry about roll over
         * checks hence a simple 'less than or greater than' test works
         * fine.
         */
        long localSeqNumberInLong = ((long) seqNumber) & MASK;
        long seqNumberToCompareInLong = ((long) seqNumberToCompare) & MASK;

        /*
         * System.out.println("Seq# in int " + seqNumber + "In long "
         * + localSeqNumberInLong);
         * System.out.println("Comp Seq# in int " + seqNumberToCompare +
         * "In long " + seqNumberToCompareInLong);
         */

        /*
         * The Algorithm in use as follows -
         * 
         * Module  Compare(int a,int b)
         * 
         * 1. Convert both the numbers to long data types ( a to La, and
         * b to Lb).
         * 2. determine the ABSOLUTE difference between the two numbers.
         * 3. Test if the ABSOLUTE difference is greater than or equal to the
         * maximum rollover value - (0x7fffffff).
         * If true:
         * Then the numbers are on the opposite sides of the sign
         * spectrum. The following will determine which number is
         * LESS(or comes before) which number.
         * Max(La, Lb) is LESS than Min(La, Lb).
         * 
         * If false:
         * Then the numbers La & Lb are on same side of the
         * sign spectrum... hence a simple test of which
         * number is greater than the other yields the
         * result.
         * 
         */
        long diff = Math.abs(localSeqNumberInLong - seqNumberToCompareInLong);

        if (diff >= ROLLOVERMAX) {
            if (localSeqNumberInLong > seqNumberToCompareInLong) {
                return -1;
            } else {
                return 1;
            }
        }

        /*
         * Since the difference is less than the rollover
         * max the numbers are in the same sign region.
         */
        if (localSeqNumberInLong > seqNumberToCompareInLong) {
            return 1;
        } 

        return -1;
    }

    /*
     * Compare this sequence number to the sequence number parameter
     * and return true if they are equal.
     */

    public boolean isEqualTo(int sequenceNumber) {
        if (sequenceNumber == seqNumber) {
            return true;
        } 

        return false;
    }

    /*
     * If this sequence number is less than the supplied sequence
     * number, return true.
     */

    public boolean isLessThan(int sequenceNumber) {
        long longSeqNumber = ((long) seqNumber) & MASK;
        long longSequenceNumber = ((long) sequenceNumber) & MASK;
        long diff = longSeqNumber - longSequenceNumber;

        if (Math.abs(diff) >= ROLLOVERMAX) {
            if (diff < 0) {
                return false;
            } else {
                return true;
            }
        } else if (diff < 0) {
            return true;
        } else {
            return false;
        }
    }

    /*
     * If this sequence number is greater than the supplied sequence
     * number, return true.
     */

    public boolean isGreaterThan(int sequenceNumber) {
        long longSeqNumber = ((long) seqNumber) & MASK;
        long longSequenceNumber = ((long) sequenceNumber) & MASK;
        long diff = longSeqNumber - longSequenceNumber;

        if (Math.abs(diff) >= ROLLOVERMAX) {
            if (diff < 0) {
                return true;
            } else {
                return false;
            }
        } else if (diff > 0) {
            return true;
        } else {
            return false;
        }
    }

    /*
     * If this sequence number is less than or equal to the supplied sequence
     * number, return true.
     */

    public boolean isLessThanOrEqual(int sequenceNumber) {
        if (isLessThan(sequenceNumber)) {
            return true;
        } else {
            return (isEqualTo(sequenceNumber));
        }
    }

    /*
     * If this sequence number is greater than or equal to the
     * supplied sequence number, return true.
     */

    public boolean isGreaterThanOrEqual(int sequenceNumber) {
        if (isGreaterThan(sequenceNumber)) {
            return true;
        } else {
            return (isEqualTo(sequenceNumber));
        }
    }

    /**
     * Add value to the sequence number.
     */
    public void add(int value) {
        seqNumber += value;
    }

    /**
     * Subtract value from the sequence number.
     */
    public void subtract(int value) {
        seqNumber -= value;
    }

}

