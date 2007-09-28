/*
 * Copyright 2007 Sun Microsystems, Inc.
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

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.EmptyKernelAppContext;
import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.TaskOwner;
import java.lang.Math;

/** Provides a simple implementation of TaskOwner, for testing. */
public class DummyTaskOwner implements TaskOwner {

    /** The identity. */
    private final Identity identity = new DummyIdentity();

    /** The kernel application context. */
    private KernelAppContext kernelAppContext =
        new EmptyKernelAppContext("dummyApp-" + Math.random());

    /** Creates an instance of this class. */
    public DummyTaskOwner() { }

    /* -- Implement TaskOwner -- */

    public KernelAppContext getContext() {
	return kernelAppContext;
    }

    public Identity getIdentity() {
	return identity;
    }
    
    public void setContext(KernelAppContext ctx) {
        kernelAppContext = ctx;
    }
}
