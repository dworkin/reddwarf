/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 * --
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
