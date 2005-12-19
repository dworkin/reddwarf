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
 * TRAMTimer.java
 */
package com.sun.multicast.reliable.transport.tram;

class TRAMTimer extends Thread {
    private String name;
    private TRAMTimerEventHandler handler = null;
    private boolean loaded;
    private TRAMLogger logger;
    private long time;      // in ms
    private boolean running = false;
    private boolean done = false;

    /**
     * Handle the timeout event.
     * 
     */
    public TRAMTimer(String name, TRAMTimerEventHandler handler) {
        super(name);

        initialize(name, handler);
        start();
    }

    public TRAMTimer(String name, TRAMTimerEventHandler handler, 
                    TRAMLogger logger) {
        super(name);

        initialize(name, handler);

        this.logger = logger;

        start();
    }

    /*
     * private method to initialize the timer module.
     */

    private void initialize(String name, TRAMTimerEventHandler handler) {
        this.name = name;
        this.handler = handler;
        loaded = false;
        logger = null;
        time = 0;
        running = false;

        setDaemon(true);
    }

    /*
     * Run method
     */

    public void run() {
        while (!done) {
            long ltime = 0;

            if (getLoaded() == true) {
                setRunning(true);

                ltime = getTime();

                try {
                    if (logger == null) {
                        System.out.println("Sleeping for " + ltime);
                    } else {
			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                            logger.putPacketln(this, "Sleeping for " + ltime);
			}
                    }

                    sleep(ltime);
                    setRunning(false);
                    setLoaded(false);
		    if (handler != null)
			handler.handleTimeout();
                } catch (InterruptedException e) {
                    if (logger == null) {
                        System.out.println("Interruped ...");
                    } else {
			if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                            logger.putPacketln(this, "Interruped ...");
			}
                    }

                    setRunning(false);
                }
            }
            if ((getLoaded() == false) && (done == false)) {
                if (logger == null) {
                    System.out.println("Timer not loaded... suspending");
                } else {
		    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                        logger.putPacketln(this,
                            "Timer not loaded... suspending");
		    }
                }

                stall();
            }
        }
    }

    /*
     * Stall method to synchronize access to wait() method
     */

    private synchronized void stall() {
        try {
            wait();
        } catch (InterruptedException ie) {
            if (logger == null) {
                System.out.println("Interruped during wait...");
            } else {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, 
			"Interruped during wait...");
		}
            }
        }
    }

    /*
     * Wake a stalled thread.
     */

    private synchronized void wake() {
        notifyAll();
    }

    /**
     * Loads the specified timer value.
     * @param interval The timer interval in ms that is to be loaded.
     * 
     */
    public void loadTimer(long interval) {
        if (logger == null) {
            System.out.println("Loading new timer " + interval);
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Loading new timer " + interval);
	    }
        }

        setTime(interval);
        setLoaded(true);
        wake();
    }

    /**
     * Loads the timer if not already running. If a timer is already loaded,
     * then the loaded timer is stopped and the new timer is loaded.
     * @param interval time in ms that is to used to reload the timer.
     */
    public void reloadTimer(long interval) {
        if (logger == null) {
            System.out.println("Reloading new timer " + interval);
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Reloading new timer " + interval);
	    }
        }

        stopTimer();
        setTime(interval);
        setLoaded(true);
        wake();
    }

    /**
     * Stops the timer if found active.
     */
    public void stopTimer() {
        if (logger == null) {
            System.out.println("In stopTimer");
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, "In stopTimer");
	    }
        }
        if (getRunning() == true) {
            if (logger == null) {
                System.out.println("Stopping an active timer");
            } else {
	        if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                    logger.putPacketln(this, 
			"Stopping an active timer");
		}
            }

            setLoaded(false);
            setTime(0);
            setRunning(false);
            interrupt();
        }
    }

    /*
     * Call this method to stop the timer thread. This timer is unusable
     * after this call.
     */

    public void killTimer() {
        done = true;
	handler = null;
	wake();
        interrupt();
    }

    /*
     * private method to access the loaded flag. The loaded flag
     * if true whet a timer is loaded. When the loaded timer
     * expires, the loaded flag is cleared.
     * This is made to be a method for mutual exclusion reasons.
     */

    private synchronized boolean getLoaded() {
        return loaded;
    }

    /*
     * private method to set the loaded field. This is made
     * to be a method for mutual exclusion reasons.
     * @param value true, if the loaded flag is to be set or
     * false to clear the loaded flag.
     */

    private synchronized void setLoaded(boolean value) {
        loaded = value;
    }

    /*
     * Private method to get the status of the timer.This is made
     * to be a method for mutual exclusion reasons.
     * @return true - if the timer is currently active.
     * false  if the timer is unloaded or inactive.
     */

    private synchronized boolean getRunning() {
        return running;
    }

    /*
     * private method to set the running flag to the specified value.
     * @param value  to be set
     */

    private synchronized void setRunning(boolean value) {
        running = value;
    }

    /*
     * private method to fetch the currently loaded time
     * @return loaded time in ms
     */

    private synchronized long getTime() {
        return time;
    }

    /*
     * private method to set the time to be loaded in the timer
     * @param time time to be loaded in ms.
     */

    private synchronized void setTime(long time) {
        this.time = time;
    }

}

