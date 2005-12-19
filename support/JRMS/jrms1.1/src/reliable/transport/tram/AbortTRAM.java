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
 * AbortTRAM.java
 * 
 * Module Description: 
 *
 * This thread is designed to be invoked by ANY
 * TRAM module upon detecting an irrecoverable error condition.
 * This thread basically invokes the doTRAMAbort() method in TRAMControlBlock.
 * Since the doTRAMAbort() method essentially starts stopping all the
 * active TRAM threads in the system, proper TRAM termination may not be
 * guaranteed if a thread that is in the list of threads shutdown by
 * doTRAMAbort() invokes the doTRAMAbort() method.
 * This thread aids in performing close operation successfully. Essentially
 * an active thread in TRAM will spawn off this thread and waits for
 * the shutdown to occur.
 */
package com.sun.multicast.reliable.transport.tram;

class AbortTRAM extends Thread {
    private String name;
    private TRAMControlBlock tramblk;

    /**
     * Abort TRAM.
     * 
     */
    public AbortTRAM(String name, TRAMControlBlock tramblk) {
        super(name);

        this.name = name;
        this.tramblk = tramblk;

        setDaemon(true);
        setPriority(10);
        start();
    }

    /*
     * Run method
     */

    public void run() {
        TRAMLogger logger = tramblk.getLogger();

        if (logger == null) {
            System.out.println("SHUTTING DOWN TRAM.!!!!!!!!!!!!!!!!!!!!!!");
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_ANY)) {
                logger.putPacketln(this, 
                    "SHUTTING DOWN TRAM.!!!!!!!!!!!!!!!!!!!!!!");
	    }
        }

        tramblk.doTRAMAbort();
    }

}
