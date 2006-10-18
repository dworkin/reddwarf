package com.sun.sgs.test.app.timer;

import java.io.Serializable;
import java.util.Properties;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ShutdownListener;
import com.sun.sgs.app.Task;

/**
 * @author James Megquier
 * @version 1.0
 */
public class SimpleTimerApp
	implements AppListener, Task, ManagedObject, Serializable
{
    private static final long serialVersionUID = 1L;

    private static final long TIMER_PERIOD = 100; // milliseconds
    private long tickCount = 0;

    public void startingUp(Properties props) {
	System.out.format("SimpleTimerApp starting up\n");

	AppContext.getTaskManager().schedulePeriodicTask(this, TIMER_PERIOD, TIMER_PERIOD);
    }

    public void shuttingDown(ShutdownListener listener, boolean force) {
	System.out.format("SimpleTimerApp shutting down, force = %b\n", force);

	listener.shutdownComplete();
    }

    public void loggedIn(ClientSession session) {
	// no-op
    }

    public void run() throws Exception {
	// Inform the system that we're going to modify this object.
	AppContext.getDataManager().markForUpdate(this);
	
	System.out.format("[%d] tick %d\n",
		System.currentTimeMillis(), tickCount);

	tickCount++;
    }
}