package com.sun.sgs.test.util;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.TaskOwner;

/** Provides a simple implementation of TaskOwner, for testing. */
public class DummyTaskOwner implements TaskOwner {

    /** The kernel application context. */
    private final KernelAppContext kernelAppContext =
	new KernelAppContext() { };

    /** Creates an instance of this class. */
    public DummyTaskOwner() { }

    /* -- Implement TaskOwner -- */

    public KernelAppContext getContext() {
	return kernelAppContext;
    }

    public Identity getIdentity() {
	return new com.sun.sgs.impl.auth.IdentityImpl("Me!");
    }
}
