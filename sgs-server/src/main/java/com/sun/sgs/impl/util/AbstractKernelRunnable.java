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

package com.sun.sgs.impl.util;

import com.sun.sgs.kernel.KernelRunnable;

/**
 * A utility class that implements the {@code getBaseTaskType} method
 * of {@code KernelRunnable} to return the class name of the concrete
 * instance.
 */
public abstract class AbstractKernelRunnable implements KernelRunnable {

    private final String name;

    /**
     * Constructs an instance with the specified {@code name}.  If the
     * {@code name} is non-{@code null}, then it is included in the
     * {@code toString} method to identify the instance.
     *
     * @param	name a name for the instance
     */
    public AbstractKernelRunnable(String name) {
	this.name = name;
    }

    /**
     * Returns the class name of the concrete instance.
     *
     * @return the class name of the concrete instance
     */
    public String getBaseTaskType() {
        return toString();
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
	String nameString = name != null ? "[" + name + "]" : "";
	return getClass().getName() + nameString;
    }
}
