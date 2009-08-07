/*
 * Copyright (c) 2007-2009, Sun Microsystems, Inc.
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

package com.sun.sgs.tests.overhead;

import java.util.Properties;
import java.io.Serializable;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.AppContext;


/**
 * A simple PDS application that initiates a single self-rescheduling
 * task that implements an incrementing counter in a tight loop.
 * <p>
 * The application is designed purely as a micro-benchmark to determine
 * the amount of overhead involved in scheduling tasks.  A 
 * {@code ProfileSummaryListener} is enabled which periodically reports
 * task performance information out to the console.
 */
public class Overhead implements AppListener, Serializable {
    
    private static final String SUM = "com.sun.sgs.tests.overhead.sum";
    private static final long DEFAULT_SUM = 1000000;
    
    public void initialize(Properties props) {
        long sum;
        String sumProp = props.getProperty(SUM);
        try {
            sum = Long.valueOf(sumProp);
        } catch (NumberFormatException ignore) {
            sum = DEFAULT_SUM;
        }
        
	for(int i = 0; i < 4; i++) {
	    AppContext.getTaskManager().scheduleTask(new SumTask(sum));
	}
    }

    /**
     * This app does not support client logins.
     * 
     * @param session
     * @return always return {@code null}
     */
    public ClientSessionListener loggedIn(ClientSession session) {
        return null;
    }

}
