package com.sun.sgs.tutorial.server.lesson4;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

public class HelloPersistance implements AppListener, Task, Serializable {
	private long lastTime;
	static private Logger logger = 
		Logger.getLogger("sgs.tutorial.server");
	
	/**
	 * This is the initialize method run when the app is first installed
	 * it sets up the timer task.  Because timer tasks are persistant, this
	 * only needs to be done once.  if the system crashes and then comes
	 * back up, the timer will resume ticking without further
	 * registration.
	 */
	public void initialize(Properties props) {
		TaskManager taskManager = AppContext.getTaskManager();
		/* register a periodic task to start about 5 seconds from now
		 * and 'tick' once per second.
		 */
		lastTime=System.currentTimeMillis();
		taskManager.schedulePeriodicTask(this,5000, 500);
	}

	public ClientSessionListener loggedIn(ClientSession session) {
		// TODO Auto-generated method stub
		return null;
	}

	/** This is the task execution method.  It gets called the first 
	 * time after a minimal 5 second delay from when initialize runs,
	 * and then approximately once per second after that.
	 * It is important to note that the period is a minimal delay, not
	 * an absolute gaurantee or an average.  If, for whateve reason, the
	 * event doesnt happen again until 3 seconds after the last one,
	 * it still only gets called once.  For this reason it is vital that
	 * an app track real elpsed time and adjust its actions accordingly.
	 */
	public void run() throws Exception {
		long time =System.currentTimeMillis();
		logger.info("HelloTimer task ran at "+System.currentTimeMillis()+"ms, "+
				(time-lastTime)+" ms after the last time it was run.");
		lastTime=time;
		
	}

}
