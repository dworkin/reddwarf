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
