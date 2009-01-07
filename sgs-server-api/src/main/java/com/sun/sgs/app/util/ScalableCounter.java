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
import com.sun.sgs.app.Task;

/**
 * A scalable implementation of a simple integer counter.  This class
 * supports multiple concurrent write operations with minimal contention.
 * This includes calls to {@link #add(int)}, {@link #increment()}, and
 * {@link #decrement()}.  However, any read operation, such as a call to
 * {@link #get()}, made simultaneously with a write operation will always
 * cause contention.  Therefore, this class is best suited for scenarios
 * where frequent writes but infrequent reads are required.
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
    
    /**
     * Array of {@code InternalCounter} objects that when combined, 
     * represent the actual value of this {@code ScalableCounter}.
     */
    private final ManagedReference<InternalCounter>[] counters;
    
    /**
     * Creates a new {@code ScalableCounter} with an initial value of
     * {@code 0} and the number of {@code InternalCounter}s set to
     * {@value ScalableCounter#DEFAULT_NUM_COUNTERS}.
     */
    public ScalableCounter() {
        this(0, DEFAULT_NUM_COUNTERS);
    }

    /**
     * Creates a new {@code ScalableCounter} with a configurable initial value
     * and the number of {@code InternalCounter}s set to
     * {@value ScalableCounter#DEFAULT_NUM_COUNTERS}.
     * 
     * @param initialValue the initial value of the {@code ScalableCounter}
     */
    public ScalableCounter(int initialValue) {
        this(initialValue, DEFAULT_NUM_COUNTERS);
    }
    
    /**
     * Creates a new {@code ScalableCounter} with a configurable initial value
     * and a configurable number of {@code InternalCounter}s.  Typically,
     * a larger number of {@code InternalCounter} objects is desired when
     * the ratio of writes to reads of the {@code ScalableCounter} is extremely
     * high.
     * 
     * @param initialValue the initial value of the {@code ScalableCounter}
     * @param numCounters the number of {@code InternalCounter} objects to use
     *        to track the value of the counter
     */
    public ScalableCounter(int initialValue, int numCounters) {
        if(numCounters < 1) {
            throw new IllegalArgumentException(
                    "numCounters cannot be less than 1");
        }
        
        counters = new ManagedReference[numCounters];
        counters[0] = AppContext.getDataManager().createReference(
                new InternalCounter(initialValue));
        for(int i = 1; i < numCounters; i++) {
            counters[i] = AppContext.getDataManager().createReference(
                    new InternalCounter(0));
        }
    }
    
    /**
     * Adds the given value to this {@code ScalableCounter}.
     * 
     * @param value the value to add to the counter
     */
    public void add(int value) {
        int counterIndex = random.nextInt(counters.length);
        counters[counterIndex].getForUpdate().add(value);
    }
    
    /**
     * Increments the value of this {@code ScalableCounter} by {@code 1}.
     */
    public void increment() {
        add(1);
    }
    
    /**
     * Decrements the value of this {@code ScalableCounter} by {@code 1}.
     */
    public void decrement() {
        add(-1);
    }
    
    /**
     * Returns the value of this {@code ScalableCounter}.  This operation
     * (and other read operations like it)
     * should be used sparingly in the presence of multiple concurrent
     * modification operations including calls to {@link #add(int)},
     * {@link #increment()}, and {@link #decrement()}.
     * 
     * @return the value of this {@code ScalableCounter}
     */
    public int get() {
        int value = 0;
        for(ManagedReference<InternalCounter> c : counters) {
            value += c.get().getValue();
        }
        
        return value;
    }

    /**
     * Returns the value of this {@code ScalableCounter} as a {@code double}.
     * 
     * @return the value of this {@code ScalableCounter} as a {@code double}.
     */
    @Override
    public double doubleValue() {
        return (double)get();
    }

    /**
     * Returns the value of this {@code ScalableCounter} as a {@code float}.
     * 
     * @return the value of this {@code ScalableCounter} as a {@code float}.
     */
    @Override
    public float floatValue() {
        return (float)get();
    }

    /**
     * Returns the value of this {@code ScalableCounter} as a {@code int}.
     * 
     * @return the value of this {@code ScalableCounter} as a {@code int}.
     */
    @Override
    public int intValue() {
        return get();
    }

    /**
     * Returns the value of this {@code ScalableCounter} as a {@code long}.
     * 
     * @return the value of this {@code ScalableCounter} as a {@code long}.
     */
    @Override
    public long longValue() {
        return (long)get();
    }

    /**
     * Schedules this {@code ScalableCounter}'s array of {@code InternalCounter}
     * objects for asynchronous removal.
     */
    @Override
    public void removingObject() {
        AppContext.getTaskManager().scheduleTask(
                new RemoveInternalCountersTask());
    }
    
    /**
     * Helper {@code Task} to asyncronously remove the {@code InternalCounter}
     * objects associated with this {@code ScalableCounter}.
     */
    private class RemoveInternalCountersTask
            implements Task, Serializable {
        
        @Override
        public void run() throws Exception {
            for (int i = 0; i < ScalableCounter.this.counters.length; i++) {
                AppContext.getDataManager().removeObject(counters[i].get());
            }
        }
        
    }

    /**
     * Represents a simple integer counter.
     */
    private static class InternalCounter 
            implements Serializable, ManagedObject {
        
        /**
         * The value of the counter
         */
        private int counter;
        
        /**
         * Creates an {@code InternalCounter} with an initial 
         * value of {@code 0}.
         */
        public InternalCounter() {
            this(0);
        }
        
        /**
         * Creates an {@code InternalCounter} with a configurable initial
         * value.
         * 
         * @param initialValue the initial value of the {@code InternalCounter}
         */
        public InternalCounter(int initialValue) {
            this.counter = initialValue;
        }
        
        /**
         * Adds an integer value to this {@code InternalCounter}.
         * 
         * @param value the value to add to the counter
         */
        public void add(int value) {
            this.counter += value;
        }
        
        /**
         * Retrieves the value of this counter.
         * 
         * @return the value of the counter
         */
        public int getValue() {
            return counter;
        }
    }

}
