package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.Task;

import com.sun.sgs.impl.util.LoggerWrapper;
import static com.sun.sgs.impl.kernel.AppStartupRunner.APP_IS_RUNNING_BINDING;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.TaskOwner;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;


class AppShutdownTrampoline implements Task, Serializable
{
    private static final long serialVersionUID = 1L;

    // logger for this class
    static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(AppShutdownTrampoline.class.getName()));

    AppShutdownTrampoline() { /* empty */ }

    // TODO
    // This would be the last stage in shutdown.  First, the services
    // should be told not to generate new events from external inputs
    // (either once shutdownComplete is called, or if "force" is set).
    // Then this task should run and indicate the app is clean, and
    // tell the services to shut themselves down after this txn commits.
    public void run() throws Exception {
	TaskOwner owner = TaskThread.currentThread().getCurrentOwner();
	KernelAppContext ctx = owner.getContext();
	AppKernelAppContext appContext = (AppKernelAppContext) ctx;
	
        if (logger.isLoggable(Level.FINER))
            logger.log(Level.FINER, "{0}: stopping application", appContext);

        // FIXME
        DataService dataService = appContext.getService(DataService.class);
        TaskService taskService = appContext.getService(TaskService.class);
        
        // if the app has shut down cleanly, or was never run, we
        // need to send it the startingUp callback
        try {
            // Is the app already shutdown?
            ManagedObject appRunningIndicator =
            dataService.getServiceBinding(
        	    APP_IS_RUNNING_BINDING, ManagedObject.class);
	    // ...no, so we should shut it down
            // delete the appRunningIndicator
            try {
            	dataService.removeObject(appRunningIndicator);
            } catch (ObjectNotFoundException e) { /* ignore */ }
            // let NameNotBoundException be caught by the outer try
            dataService.removeServiceBinding(APP_IS_RUNNING_BINDING);
            
	} catch (NameNotBoundException nnbe) {
            if (logger.isLoggable(Level.WARNING))
                logger.logThrow(Level.WARNING,
            	       "{0}: application was already shut down",
                           nnbe, appContext);
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE))
                logger.logThrow(Level.SEVERE,
            	       "{0}: couldn't instantiate application:",
                           e, appContext);
            throw e;
	}
        
        taskService.shutdownTransactional();
        
        logger.log(Level.FINE, "preparting to exit");
        new ShutdownThread(dataService).start();
    }

    static class ShutdownThread extends Thread {
	final DataService dataService;
	ShutdownThread(DataService dataService) {
	    this.dataService = dataService;
	}
	public void run() {
	    try {
		Thread.sleep(1000);
		logger.log(Level.FINE, "shutdown services");
		dataService.shutdown();
		Thread.sleep(1000);
	    } catch (InterruptedException e) {
		// ignore
	    }
            logger.log(Level.INFO, "calling System.exit");
	    System.exit(0);
        }
    }
}