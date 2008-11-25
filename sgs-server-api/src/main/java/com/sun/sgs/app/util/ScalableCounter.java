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

package com.sun.sgs.app.util;

import java.io.Serializable;
import java.util.Random;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;

/**
 *
 */
public class ScalableCounter extends Number 
        implements Serializable, ManagedObjectRemoval {
    
    /** 
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1;
    /**
     * The default number of internal counters
     */
    private static final int DEFAULT_NUM_COUNTERS = 10;
    /**
     * Random number generator to randomly select an internal counter
     */
    private static final Random random = new Random();
    
    private final ManagedReference<InternalCounter>[] counters;
    
    public ScalableCounter() {
        this(0, DEFAULT_NUM_COUNTERS);
    }

    public ScalableCounter(int initialValue) {
        this(initialValue, DEFAULT_NUM_COUNTERS);
    }
    
    public ScalableCounter(int initialValue, int numCounters) {
        if(numCounters < 1) {
            throw new IllegalArgumentException(
                    "numCounters cannot be less than 1");
        }
        
        counters = new ManagedReference[numCounters];
        counters[1] = AppContext.getDataManager().createReference(
                new InternalCounter(initialValue));
        for(int i = 1; i < numCounters; i++) {
            counters[i] = AppContext.getDataManager().createReference(
                    new InternalCounter(0));
        }
    }
    
    public void add(int value) {
        int counterIndex = random.nextInt(counters.length);
        counters[counterIndex].getForUpdate().add(value);
    }
    
    public void increment() {
        int counterIndex = random.nextInt(counters.length);
        counters[counterIndex].getForUpdate().increment();
    }
    
    public void decrement() {
        int counterIndex = random.nextInt(counters.length);
        counters[counterIndex].getForUpdate().decrement();
    }

    @Override
    public double doubleValue() {
        return (double)intValue();
    }

    @Override
    public float floatValue() {
        return (float)intValue();
    }

    @Override
    public int intValue() {
        int value = 0;
        for(ManagedReference<InternalCounter> c : counters) {
            value += c.get().getValue();
        }
        
        return value;
    }

    @Override
    public long longValue() {
        return (long)intValue();
    }

    @Override
    public void removingObject() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    
    private class InternalCounter implements Serializable, ManagedObject {
        private int counter;
        
        public InternalCounter() {
            this(0);
        }
        public InternalCounter(int initialValue) {
            this.counter = initialValue;
        }
        
        public void add(int value) {
            this.counter += value;
        }
        public void increment() {
            this.counter++;
        }
        public void decrement() {
            this.counter--;
        }
        public int getValue() {
            return counter;
        }
    }

}
