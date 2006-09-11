
package com.sun.sgs.kernel;

import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ManagedRunnable;


/**
 * This is a <code>Runnable</code> that knows how to run
 * <code>ManagedRunnable</code>s from their references. It starts by fetching
 * the object (using <code>get</code>) and then invokes it. This must always
 * be run within the context of a transaction.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class ReferenceRunnable implements Runnable
{

    // the reference to the runnable
    private ManagedReference<? extends ManagedRunnable> runnableRef;

    /**
     * Creates an instance of <code>ReferenceRunnable</code>.
     *
     * @param runnableRef the <code>ManagedReference</code> referencing
     *                    the <code>Runnable</code> to run
     */
    public ReferenceRunnable(ManagedReference<? extends ManagedRunnable>
                             runnableRef) {
        this.runnableRef = runnableRef;
    }

    /**
     * Runs this task.
     */
    public void run() {
        // de-reference the runnable...
        ManagedRunnable runnable = runnableRef.get();

        // ...and run it
        runnable.run();
    }

}
