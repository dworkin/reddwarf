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

import com.sun.sgs.impl.util.AbstractKernelRunnable;

/**
 * A subclass of {@code AbstractKernelRunnable} used for test purposes.
 * For ease of use, this class supplies a public no-arg constructor (which
 * {@code AbstractKernelRunnable} lacks).
 */
public abstract class TestAbstractKernelRunnable extends AbstractKernelRunnable {

    /** Constructs an instance. */
    public TestAbstractKernelRunnable() {
	super(null);
    }
}
