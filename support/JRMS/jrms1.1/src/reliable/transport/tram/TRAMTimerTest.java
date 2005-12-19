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
 * TRAMTimerTest.java
 */


package com.sun.multicast.reliable.transport.tram;

import java.lang.*;

class TRAMTimerTest implements TRAMTimerEventHandler {
    private TRAMTimer timer = null;

    public TRAMTimerTest() {
        timer = new TRAMTimer("TRAMTimerTestTimer", this);
    }

    public void loadTimer(long l) {
        timer.loadTimer(l);
    }

    public void stopTimer() {
        timer.stopTimer();
    }

    public static void main(String args[]) {
        TRAMTimerTest ttest = new TRAMTimerTest();

        System.out.println("Loading 10 secs timeout");
        ttest.loadTimer(1000);

        boolean loopFlag = true;

        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {}

        /*
         * If everything is working fine, the ttest should time out
         * and print the printf in the timeout handler before the
         * following is printed.
         */
        System.out.println("This line should be printed after the " + 
			   "Timeout Handler is invoked \n \n");
        System.out.println("Testing Interrupt. Interrupt should occur " +
			   "before the Timeout Handler is invoked");

        /*
         * if everything is working fine, the ttest timer should be
         * interrupted by the main module.
         */
        ttest.loadTimer(5000);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {}

        ttest.stopTimer();

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {}

        // try {
        // Thread.sleep(10000);
        // }catch (InterruptedException e){
        // }

        System.out.println("Exiting");
    }

    public synchronized void handleTimeout() {
        System.out.println("In handleTimeout routine");
    }

}

