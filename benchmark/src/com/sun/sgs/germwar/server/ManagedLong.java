/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server;

import java.io.Serializable;

import com.sun.sgs.app.ManagedObject;

/**
 * A long integer that can be stored in the data store.
 */
public class ManagedLong implements ManagedObject, Serializable {
    private static final long serialVersionUID = 1L;

    /** The internal value. */
    private long value;

    /**
     * Creates a new {@code ManagedLong} with initial value 0.
     */
    public ManagedLong() {
        value = 0;
    }

    /**
     * Creates a new {@code ManagedLong} with a specified initial value.
     */
    public ManagedLong(long value) {
        this.value = value;
    }

    /**
     * @return the current value
     */
    public long get() {
        return value;
    }

    /**
     * Non-atomically sets the value to a new value and returns the old value.
     */
    public long getAndSet(long newValue) {
        long oldValue = value;
        value = newValue;
        return oldValue;
    }

    /**
     * Increments the value by 1 and returns the new value.
     */
    public long increment() {
        return ++value;
    }

    /**
     * Sets the current value.
     */
    public void set(long newValue) {
        value = newValue;
    }
}
