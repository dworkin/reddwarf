/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.profile;

/**
 * A counter used in profiling. All counters have a name associated with them,
 * and start at zero. Counters can only be incremented. 
 * <p>
 * Profile counters are created with calls to 
 * {@link ProfileConsumer#createCounter ProfileConsumer.createCounter}.  
 * A counter's name includes both the {@code name} supplied to 
 * {@code createCounter} and the value of {@link ProfileConsumer#getName}.
 */
public interface ProfileCounter {

    /**
     * Returns the name of this counter.
     *
     * @return the counter's name
     */
    String getName();

    /**
     * Increments the counter by <code>1</code>.
     */
    void incrementCount();

    /**
     * Increments the counter by the given non-negative value.
     *
     * @param value the amount to increment the counter
     * 
     * @throws IllegalArgumentException if {@code value} is negative
     */
    void incrementCount(long value);
}
