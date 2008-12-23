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

import java.util.Random;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.Serializable;
import com.sun.sgs.app.ManagedObject;

/**
 *
 */
public class ManagedInteger implements Serializable, ManagedObject {
    
    private static Logger logger = Logger.getLogger(
            ManagedInteger.class.getName());

    /** 
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1;
    
    private static int MAX = 100;
    private static int CHECK = 100;

    private Random random;
    private int value;
    private int sum;
    
    private int updates;
    private long timestamp;
    
    public ManagedInteger() {
        random = new Random();
        value = random.nextInt(MAX);
        sum = 0;
        updates = 0;
        timestamp = System.currentTimeMillis();
    }
    
    public int getValue() {
        return value;
    }
    
    public void setValue() {
        value = random.nextInt(MAX);
        updates++;
        
        if(updates % CHECK == 0) {
            long lastTime = timestamp;
            timestamp = System.currentTimeMillis();
            long timeLapse = timestamp - lastTime;
            
            double rate = (double)CHECK / ((double)timeLapse / 1000.0);
            logger.log(Level.INFO, rate + " Updates/Second");
        }
    }
    
    public void setSum(int sum) {
        this.sum = sum;
    }
    
}
