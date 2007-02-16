package com.sun.sgs.tutorial.server.lesson4;

import java.io.Serializable;
import java.util.logging.Logger;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.Task;

public class TrivialTimedTask implements Task, ManagedObject, Serializable {
	static private Logger logger = 
		Logger.getLogger("sgs.tutorial.server");
	private long lastTime = System.currentTimeMillis();
	
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
