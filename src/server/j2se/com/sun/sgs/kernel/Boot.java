
package com.sun.sgs.kernel;

import com.sun.sgs.impl.kernel.SimpleAppContext;
import com.sun.sgs.impl.kernel.SimpleResourceCoordinator;
import com.sun.sgs.impl.kernel.SimpleTransactionCoordinator;

import com.sun.sgs.impl.manager.SimpleChannelManager;
import com.sun.sgs.impl.manager.SimpleDataManager;
import com.sun.sgs.impl.manager.SimpleTaskManager;
import com.sun.sgs.impl.manager.SimpleTimerManager;

import com.sun.sgs.impl.service.SimpleContentionService;
import com.sun.sgs.impl.service.SimpleDataService;
import com.sun.sgs.impl.service.SimpleTaskService;


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

    // a reference to the task queue
    private TaskQueue taskQueue;

    // TEST: the single app context we're using
    private AppContext testApp;

    /**
     * Private helper that sets up a single application's context
     * FIXME: this should only be called once until the task queue handling
     * (i.e., setting the app context on each thread) is fixed
     * TEST: The numThreds and rc parameters are just here temporarily
     */
    private AppContext setupApp(TransactionProxy transactionProxy,
                                int numThreads, ResourceCoordinator rc) {
        // FIXME: fetch the application config data, which will include
        // the id, services, etc.

        // create some test services
        // FIXME: this actually gets created based on the config, but
        // for now we'll just create them directly
        SimpleDataService dataService = new SimpleDataService();
        SimpleTaskService taskService = new SimpleTaskService();
        SimpleContentionService contentionService =
            new SimpleContentionService();

        // create the managers for this application
        // FIXME: again, this is based on the config
        SimpleChannelManager channelManager =
            new SimpleChannelManager(null);
        SimpleDataManager dataManager = new SimpleDataManager(dataService);
        SimpleTaskManager taskManager = new SimpleTaskManager(taskService);
        SimpleTimerManager timerManager =
            new SimpleTimerManager(null);

        // create a new context with the manager details
        AppContext appContext =
            new SimpleAppContext(channelManager, dataManager, taskManager,
                                 timerManager);

        // create the event queue
        // TEST: This is only here for now because it needs to know about
        // this context, and then be used to initialize a service
        taskQueue = new TaskQueue(rc, numThreads, appContext);

        // tell the services about the transaction proxy, the app context,
        // and each other (as defined by the configuration)
        dataService.configure(appContext, transactionProxy, contentionService);
        taskService.configure(appContext, transactionProxy);
        contentionService.configure(appContext, transactionProxy);

        // FIXME: we haven't figured out how this is configured yet, but
        // a more general solution is needed for frameworks, etc.
        taskService.setTaskQueue(taskQueue);

        // finally, return the app context
        return appContext;
    }

    /**
     * Creates an instance of <code>Boot</code>.
     *
     * @param numThreads the number of threads to create and use
     */
    protected Boot(int numThreads) {
        // to start create a test resource coordinator...
        ResourceCoordinator resourceCoordinator =
            new SimpleResourceCoordinator();

        // ...and give it the designated number of threads
        for (int i = 0; i < numThreads; i++) {
            TaskThread taskThread = new TransactionalTaskThread();
            taskThread.start();
            resourceCoordinator.giveThread(taskThread);
        }

        // create a proxy instance to share the transaction state
        TransactionProxy transactionProxy = new TransactionProxy();

        // setup a single test application
        testApp = setupApp(transactionProxy, numThreads, resourceCoordinator);

        // create a test transaction coordinator
        SimpleTransactionCoordinator transactionCoordinator =
            new SimpleTransactionCoordinator();
        TransactionRunnable.setTransactionCoordinator(transactionCoordinator);
    }

    /**
     * TEST: This is a simple testing method that will be removed once the
     * functionality of the system is verified.
     */
    public void test() {
        // create the bootstrap runnable that actually calls the user code
        Runnable br = new BootstrapRunnable(new com.sun.sgs.UserLevelTest());

        // now create the transaction task that will run the bootstap task
        Runnable tr = new TransactionRunnable(new Task(br, testApp, null));
        Task bootTask = new Task(tr, testApp, null);

        // finally, queue the task to run
        taskQueue.queueTask(bootTask);
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
