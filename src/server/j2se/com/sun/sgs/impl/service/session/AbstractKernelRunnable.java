
package com.sun.sgs.impl.service.session;

import com.sun.sgs.kernel.KernelRunnable;


/**
 * A utility class that implements the <code>getBaseTaskType</code> method
 * of <code>KernelRunnable</code> to return the name of the class that
 * extends this class.
 */
abstract class AbstractKernelRunnable implements KernelRunnable {

    /**
     * Returns the name of the extending class.
     *
     * @return the name of the extending class
     */
    public String getBaseTaskType() {
        return getClass().getName();
    }

}
