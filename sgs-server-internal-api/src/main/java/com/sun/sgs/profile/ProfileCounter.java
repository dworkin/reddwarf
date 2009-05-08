/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
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
