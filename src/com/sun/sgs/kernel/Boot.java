
package com.sun.sgs.kernel;


import com.sun.sgs.kernel.impl.SimpleAppContext;
import com.sun.sgs.kernel.impl.SimpleResourceCoordinator;
import com.sun.sgs.kernel.impl.SimpleTransactionCoordinator;

import com.sun.sgs.kernel.scheduling.TaskScheduler;
import com.sun.sgs.kernel.scheduling.FairPriorityTaskScheduler;
import com.sun.sgs.kernel.scheduling.TaskScheduler;

import com.sun.sgs.manager.impl.SimpleChannelManager;
import com.sun.sgs.manager.impl.SimpleDataManager;
import com.sun.sgs.manager.impl.SimpleTaskManager;
import com.sun.sgs.manager.impl.SimpleTimerManager;

import com.sun.sgs.service.impl.SimpleContentionService;
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

    // a reference to the task queue
    private TaskScheduler taskScheduler;

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
        taskScheduler = new FairPriorityTaskScheduler(appContext);

        // tell the services about the transaction proxy, the app context,
        // and each other (as defined by the configuration)
        dataService.configure(appContext, transactionProxy, contentionService);
        taskService.configure(appContext, transactionProxy);
        contentionService.configure(appContext, transactionProxy);

        // FIXME: we haven't figured out how this is configured yet, but
        // a more general solution is needed for frameworks, etc.
        taskService.setTaskScheduler(taskScheduler);

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
        Runnable tr = new TransactionRunnable(new TaskImpl(br, testApp, null, Kernel.instance().getSystemPriority(), Kernel.instance().getSystemUser()));
        Task bootTask = new TaskImpl(tr, testApp, null, Kernel.instance().getSystemPriority(), Kernel.instance().getSystemUser());

        // finally, queue the task to run
        taskScheduler.queueTask(bootTask);
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
