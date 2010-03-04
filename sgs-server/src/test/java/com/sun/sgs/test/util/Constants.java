/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.util;

/**
 * Contains constant values to be used with tests.
 */
public final class Constants {

    /**
     * This value is the maximum clock granularity that is expected for calls
     * to {@link java.lang.System#currentTimeMillis()
     * System.currentTimeMillis()} across all operating systems.  In other
     * words, this is the shortest amount of time (in milliseconds) between
     * calls to {@code System.currentTimeMillis()} where you can be
     * guaranteed to get different values.
     */
    public static final Long MAX_CLOCK_GRANULARITY =
            Long.getLong("test.clock.granularity", 20);

    /**
     * This class should not be instantiated,
     */
    private Constants() {

    }
}
