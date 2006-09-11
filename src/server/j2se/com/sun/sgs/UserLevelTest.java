
package com.sun.sgs;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ManagedRunnable;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.app.listen.BootListener;


/**
 * This is just a test driver for app-level code.
 * <p>
 * NOTE: This should be in the test hierarchy, but until we have a
 * polished boot class we can't set that up to accept configuration
 * and correctly load these boot listeners. In turn, that can't
 * be done until the bootstrapping and manager access code is
 * finished. So, for now, this class remains in the root package.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class UserLevelTest implements BootListener
{

    /**
     * Creates a new instance of <code>UserLevelTest</code>.
     */
    public UserLevelTest() {
        System.out.println("Test was constructed");
    }

    /**
     * Called when this application boots.
     */
    public void boot() {
        System.out.println("booting");
        
        ManagedReference<TestRunnable> runnableRef =
            DataManager.getInstance().manageObject(new TestRunnable());

        TaskManager.getInstance().queueTask(runnableRef);
    }

    /**
     *
     */
    public class TestRunnable implements ManagedRunnable {
        public void run() {
            System.out.println("my task ran");
        }
    }

}
