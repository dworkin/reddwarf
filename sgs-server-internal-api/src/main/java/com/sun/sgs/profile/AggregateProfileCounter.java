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
 * A profile counter which is initially {@code 0}, and increments until it
 * is explicitly cleared.
 */
public interface AggregateProfileCounter extends ProfileCounter {

    /**
     * Gets the current counter value.
     * 
     * @return the current count value
     */
    long getCount();
    
    /**
     * Clear the counter, resetting it to {@code 0}.
     */
    void clearCount();
}
