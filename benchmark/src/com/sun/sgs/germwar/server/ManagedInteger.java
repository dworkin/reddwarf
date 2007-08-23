/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server;

import java.io.Serializable;

import com.sun.sgs.app.ManagedObject;

/**
 * An integer that can be stored in the data store.
 */
public class ManagedInteger implements ManagedObject, Serializable {
    private static final long serialVersionUID = 1L;

    /** The internal value. */
    private int value;

    /**
     * Creates a new {@code ManagedInteger} with initial value 0.
     */
    public ManagedInteger() {
        value = 0;
    }

    /**
     * Creates a new {@code ManagedInteger} with a specified initial value.
     */
    public ManagedInteger(int value) {
        this.value = value;
    }

    /**
     * @return the current value
     */
    public int get() {
        return value;
    }

    /**
     * Non-atomically sets the value to a new value and returns the old value.
     */
    public int getAndSet(int newValue) {
        int oldValue = value;
        value = newValue;
        return oldValue;
    }

    /**
     * Increments the value by 1 and returns the new value.
     */
    public int increment() {
        return ++value;
    }

    /**
     * Sets the current value.
     */
    public void set(int newValue) {
        value = newValue;
    }
}
