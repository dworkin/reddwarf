package com.sun.sgs.test.util;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.TaskOwner;

public class DummyTaskOwner implements TaskOwner {
    public DummyTaskOwner() { }
    public KernelAppContext getContext() {
	return new KernelAppContext() { };
    }
    public String getIdentity() { return "Me!"; }
}
