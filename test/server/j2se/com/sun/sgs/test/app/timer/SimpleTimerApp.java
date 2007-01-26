package com.sun.sgs.test.app.timer;

import java.io.Serializable;
import java.util.Properties;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Task;

/**
 * A simple example app that uses persistence and timed tasks.
 * It increments a counter on a preset {@value #TIMER_PERIOD_MILLIS}ms
 * interval and displays the current time and counter value.
 */
public class SimpleTimerApp
	implements AppListener, Task, ManagedObject, Serializable
{
    private static final long serialVersionUID = 1L;

    static final long TIMER_PERIOD_MILLIS = 100;
    private long tickCount = 0;

    /**
     * {@inheritDoc}
     */
    public void initialize(Properties props) {
	System.out.format("SimpleTimerApp starting up\n");

	AppContext.getTaskManager().schedulePeriodicTask(
		this, TIMER_PERIOD_MILLIS, TIMER_PERIOD_MILLIS);
    }

    /**
     * {@inheritDoc}
     */
    public ClientSessionListener loggedIn(ClientSession session) {
	// Don't let anybody login
	return null;
    }

    /**
     * {@inheritDoc}
     */
    public void run() throws Exception {
	// Inform the system that we're going to modify this object.
	AppContext.getDataManager().markForUpdate(this);
	
	System.out.format("[%d] tick %d\n",
		System.currentTimeMillis(), tickCount);

	tickCount++;
    }
}