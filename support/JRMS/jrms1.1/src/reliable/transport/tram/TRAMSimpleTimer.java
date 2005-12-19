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
 * TRAMSimpleTimer.java
 * 
 * Module Description:
 * 
 * This class implements a simple timer algorithm. To use it, create
 * a new TRAMSimpleTimer object passing in the timeout value in milliseconds
 * along with the TRAMTimerEvent handler object. The timer object creates
 * a new thread and sleeps for the specified interval. When the timer
 * expires, the handleTimeout method in the TRAMTimerEvent object is called.
 * 
 * A new TRAMSimpleTimer object must be created for each new timer event.
 */
package com.sun.multicast.reliable.transport.tram;

class TRAMSimpleTimer extends Thread {
    private TRAMTimerEventHandler handler;
    private TRAMLogger logger;
    private long timeout;

    /*
     * Create a new timer object. Save the timeout value (milliseconds),
     * the event handler object, and the logger object.
     */

    public TRAMSimpleTimer(long timeout, TRAMTimerEventHandler handler, 
                          TRAMLogger logger) {
        this.timeout = timeout;
        this.handler = handler;
        this.logger = logger;

        setDaemon(true);
        start();

	if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
            logger.putPacketln(this,
		"Loading simple timer with timeout = " + timeout);
	}
    }

    /*
     * Sleep for the specified timeout period and call the handleTimeout
     * method in the TRAMTimerEvent object. This method is called if the
     * timer has expired.
     */

    public void run() {
        try {
            sleep(timeout);
	    if (handler != null)
                handler.handleTimeout();
        } catch (InterruptedException e) {}
    }

    /*
     * Abort timer.
     */
    public void abortTimer() {
	handler = null;
    }

}

