
package com.sun.sgs.kernel;

import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.app.DataManager;

import com.sun.sgs.app.listen.BootListener;


/**
 * This is a package-private helper class that is used to bootstrap the
 * start of an application. This involves managing the boot listener,
 * locking the boot listener, and then calling <code>boot</code> on the
 * boot listener. This runnable must be invoked in a transactional
 * context.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
class BootstrapRunnable implements Runnable
{

    // the original listener instance
    private BootListener listener;

    /**
     * Creates an instance of <code>BootstrapRunnable</code>.
     *
     * @param listener the listener to call when this instance is run
     */
    public BootstrapRunnable(BootListener listener) {
        this.listener = listener;
    }

    /**
     * Runs the task.
     */
    public void run() {
        // first, the listener needs to be managed
        DataManager dataManager = DataManager.getInstance();
        ManagedReference<BootListener> listenerRef =
            dataManager.manageObject(listener, "bootListener");

        // next, we call get on the reference so we can update it
        BootListener listenerObj = listenerRef.get();

        // finally, invoke the listener
        listenerObj.boot();
    }

}
