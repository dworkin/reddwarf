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

package com.sun.sgs.tests.deadlock;

import java.util.Properties;
import java.io.Serializable;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.AppContext;


/**
 * A simple PDS application that initiates a set of self-rescheduling
 * tasks which intentionally causes pathological contention between
 * them.
 * <p>
 * The application contains a list of {@link ManagedInteger} references
 * and there is a {@link SumTask} associated with each {@code ManagedInteger}
 * in the list.  Each task attempts to sum up the values of all of the integers,
 * and record this sum in the state of the associated integer.  Operating
 * concurrently, this causes significant contention as every task is trying
 * to acquire an exclusive write lock one one of the integers, while also
 * acquiring a read lock on all of the other integers.
 * <p>
 * A single {@link ReportTask} is also scheduled to run once per 
 * second which tracks
 * and reports on the successful update rate of a single {@code ManagedInteger}
 * in the list of {@code ManagedInteger}s.
 */
public class Deadlock implements AppListener, Serializable {
    
    private static final String SIZE = "com.sun.sgs.tests.deadlock.size";
    private static final int DEFAULT_SIZE = 10;
    
    public void initialize(Properties props) {
        int size;
        String sizeProp = props.getProperty(SIZE);
        try {
            size = Integer.valueOf(sizeProp);
        } catch (NumberFormatException ignore) {
            size = DEFAULT_SIZE;
        }
        
        ManagedReference<ManagedInteger>[] integers = 
                new ManagedReference[size];
        for (int i = 0; i < size; i++) {
            integers[i] = AppContext.getDataManager().createReference(
                    new ManagedInteger());
            AppContext.getTaskManager().scheduleTask(
                    new SumTask(integers, integers[i]));
        }
        
        AppContext.getTaskManager().schedulePeriodicTask(
                new ReportTask(integers[0]), 1000, 1000);
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
