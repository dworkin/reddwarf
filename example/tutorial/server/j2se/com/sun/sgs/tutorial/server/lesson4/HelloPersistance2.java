package com.sun.sgs.tutorial.server.lesson4;

import java.util.Properties;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.TaskManager;

public class HelloPersistance2 implements AppListener {
	/**
	 * Managed Objects are referred to through Managed References
	 * This is very important.  If you use a normal Java refernce then,
	 * just like any other field, the object referenced will become part of 
	 * the state of the referencing object and be stored independantly from
	 * any other reference to the same object.
	 * 
	 * This can be the source of many subtle errors.  (Typically your not seeing
	 * changes to the object made by reference from one object reflected in the object
	 * rerefenced by another one since each gets its own copy as part of its state.)
	 *
	 */
	private ManagedReference taskManagedObjectReference = null;
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
		/**
		 * We need a DataManager to create a Managed Object
		 */
		DataManager dataManager =AppContext.getDataManager();
		TrivialTimedTask task = new TrivialTimedTask();
		/**
		 * We make the object into a Managed Object by asking for a 
		 * ManagedReference.
		 */
		taskManagedObjectReference = dataManager.createReference(task);
		/* register a periodic task to start about 5 seconds from now
		 * and 'tick' once per second.
		 */
		
		taskManager.schedulePeriodicTask(task,5000, 500);
	}

	public ClientSessionListener loggedIn(ClientSession session) {
		// TODO Auto-generated method stub
		return null;
	}

	


}
