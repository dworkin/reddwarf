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
 */

package com.sun.sgs.test.util;

import com.sun.sgs.kernel.KernelRunnable;


/**
 * A utility class that implements the <code>getBaseTaskType</code> method
 * of <code>KernelRunnable</code> to return the name of this class, and
 * implements <code>run</code> to do nothing.
 */
public class DummyKernelRunnable implements KernelRunnable {

    // the type of this class
    private static final String TYPE = DummyKernelRunnable.class.getName();

    /**
     * Returns the name of the extending class.
     *
     * @return the name of the extending class
     */
    public String getBaseTaskType() {
        return TYPE;
    }

    /**
     * Does nothing.
     */
    public void run() throws Exception {}

}
