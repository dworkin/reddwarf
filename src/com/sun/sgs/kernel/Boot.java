
package com.sun.sgs.kernel;

import com.sun.sgs.kernel.impl.SimpleResourceCoordinator;
import com.sun.sgs.kernel.impl.SimpleTransactionCoordinator;

import com.sun.sgs.manager.impl.SimpleChannelManager;
import com.sun.sgs.manager.impl.SimpleDataManager;
import com.sun.sgs.manager.impl.SimpleTaskManager;
import com.sun.sgs.manager.impl.SimpleTimerManager;

import com.sun.sgs.service.impl.SimpleDataService;
import com.sun.sgs.service.impl.SimpleTaskService;


/**
 * This is the starting point for the system.
 * <p>
 * NOTE: For the present, this is really a shim to help test how all the
 * pieces fit together.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public final class Boot
{

    // a reference to the event queue
    private EventQueue eventQueue;

    // a reference to the resource manager
    private ResourceCoordinator resourceCoordinator;

    /**
     * Creates an instance of <code>Boot</code>.
     *
     * @param numThreads the number of threads to create and use
     */
    protected Boot(int numThreads) {
        // create a proxy instance to share
        TransactionProxy transactionProxy = new TransactionProxy();

        // install the managers
        new SimpleChannelManager(transactionProxy);
        new SimpleDataManager(transactionProxy);
        new SimpleTaskManager(transactionProxy);
        new SimpleTimerManager(transactionProxy);

        // create some test services
        SimpleDataService dataService = new SimpleDataService();
        dataService.setTransactionProxy(transactionProxy);
        SimpleTaskService taskService = new SimpleTaskService();
        taskService.setTransactionProxy(transactionProxy);

        // create a test transaction coordinator with default services
        SimpleTransactionCoordinator transactionCoordinator =
            new SimpleTransactionCoordinator(dataService, taskService);
        TransactionRunnable.setTransactionCoordinator(transactionCoordinator);

        // create a test resource coordinator...
        resourceCoordinator = new SimpleResourceCoordinator();

        // ...and give it the designated number of threads
        for (int i = 0; i < numThreads; i++) {
            TaskThread taskThread = new TransactionalTaskThread();
            taskThread.start();
            resourceCoordinator.giveThread(taskThread);
        }

        // create the event queue, at which point we're ready to start
        // processing events
        // FIXME: for testing, we're giving all the threads right
        // to the event queue, since no one else needs them yet
        eventQueue = new EventQueue(resourceCoordinator, numThreads);
        taskService.setEventQueue(eventQueue);
    }

    /**
     * TEST: This is a simple testing method that will be removed once the
     * functionality of the system is verified.
     */
    public void test() {
        // create the bootstrap runnable that actually calls the user code
        Runnable br = new BootstrapRunnable(new com.sun.sgs.UserLevelTest());

        // now create the transaction task that will run the bootstap task
        Runnable tr = new TransactionRunnable(new Task(br, null));
        Task bootTask = new Task(tr, null);

        // finally, queue the task to run
        eventQueue.queueEvent(bootTask);
    }

    /**
     * Command-line entry for starting the system. You may optionally
     * provide a number on the command-line which is the number of threads
     * to start using.
     */
    public static void main(String [] args) {
        Boot boot;

        if (args.length < 1)
            boot = new Boot(1);
        else
            boot = new Boot(Integer.parseInt(args[0]));

        boot.test();
    }

}
