package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ShutdownListener;
import com.sun.sgs.app.Task;

import static com.sun.sgs.impl.kernel.AppStartupRunner.LISTENER_BINDING;
import com.sun.sgs.impl.util.LoggerWrapper;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AppShutdownTask implements Task, Serializable
{
    private static final long serialVersionUID = 1L;

    // logger for this class
    static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(AppShutdownTask.class.getName()));

    public static void requestShutdown() {
	AppContext.getTaskManager().scheduleTask(new AppShutdownTask());
    }

    private AppShutdownTask() { /* empty */ }

    public void run() throws Exception {
	// TODO
	logger.log(Level.FINER, "scheduling shutdown");

	AppContext.getDataManager().
	    getBinding(LISTENER_BINDING, AppListener.class).
		shuttingDown(new ShutdownListenerImpl(), false);
    }

    static class ShutdownListenerImpl
    	    implements ShutdownListener, Serializable
    {
        private static final long serialVersionUID = 1L;
	public void shutdownComplete() {
	    AppContext.getTaskManager().
	        scheduleTask(new AppShutdownTrampoline());
        }
    }
    
}