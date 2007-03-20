
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
