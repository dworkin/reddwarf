/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
 * and there is a task associated with each {@code ManagedInteger} in the
 * list.  Each task attempts to sum up the values of all of the integers,
 * and record this sum in the state of the associated integer.  Operating
 * concurrently, this causes significant contention as every task is trying
 * to acquire an exclusive write lock one one of the integers, while also
 * acquiring a read lock on all of the other integers.
 */
public class Deadlock implements AppListener, Serializable {
    
    private static String SIZE = "com.sun.sgs.tests.deadlock.size";
    private static int DEFAULT_SIZE = 10;
    
    private ManagedReference<ManagedInteger>[] integers;

    public void initialize(Properties props) {
        int size;
        String sizeProp = props.getProperty(SIZE);
        try {
            size = Integer.valueOf(sizeProp);
        } catch (NumberFormatException ignore) {
            size = DEFAULT_SIZE;
        }
        
        integers = new ManagedReference[size];
        for(int i = 0; i < size; i++) {
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
