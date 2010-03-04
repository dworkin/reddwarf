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
 * An operation which has occurred.
 * <p>
 * Profile operations are created with calls to {@link 
 * ProfileConsumer#createOperation ProfileConsumer.createOperation}.  An 
 * operations's name includes both the {@code name} supplied to 
 * {@code createOperation} and the value of {@link ProfileConsumer#getName}.
 */
public interface ProfileOperation {

    /**
     * Returns the name of this operation.
     *
     * @return the name
     */
    String getName();

    /**
     * Tells this operation to report that it is happening. 
     */
    void report();
}
