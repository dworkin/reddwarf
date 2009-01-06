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

import java.io.Serializable;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.ManagedReference;

/**
 *
 */
public class ReportTask implements Task, Serializable {
    
    private static Logger logger = Logger.getLogger(
            ManagedInteger.class.getName());
    
    private final ManagedReference<ManagedInteger> integer;
    private final long startTimestamp;
    
    public ReportTask(ManagedReference<ManagedInteger> integer) {
        this.integer = integer;
        this.startTimestamp = System.currentTimeMillis();
    }

    @Override
    public void run() throws Exception {
        int updates = integer.get().getUpdates();
        
        long timestamp = System.currentTimeMillis();
        long timeLapse = timestamp - startTimestamp;
            
        double rate = (double) updates / ((double) timeLapse / 1000.0);
        logger.log(Level.INFO, rate + " Updates/Second");
    }

}
