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
 * A profile operation which aggregates a count of how many times the operation
 * occurred until it is explicitly cleared.
 */
public interface AggregateProfileOperation extends ProfileOperation {

    /**
     * Gets aggregate number of times this operation has been reported.
     * 
     * @return the current count of operation reports
     */
    long getCount();
    
    /**
     * Clear the count of operation reports.
     */
    void clearCount();
}
