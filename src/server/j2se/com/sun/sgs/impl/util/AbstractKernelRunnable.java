package com.sun.sgs.impl.util;

import com.sun.sgs.kernel.KernelRunnable;

/**
 * A utility class that implements the {@code getBaseTaskType} method
 * of {@code KernelRunnable} to return the class name of the concrete
 * instance.
 */
public abstract class AbstractKernelRunnable implements KernelRunnable {

    /**
     * Returns the class name of the concrete instance.
     *
     * @return the class name of the concrete instance
     */
    public String getBaseTaskType() {
        return getClass().getName();
    }
}
