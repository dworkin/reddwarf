package com.sun.sgs.analysis;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionException;

import com.sun.sgs.app.util.DistributedHashMap;
import com.sun.sgs.app.util.ManagedSerializable;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import java.lang.reflect.Constructor;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.Properties;

/**
 * Bootstrapping application for scheduling testing-based tasks.
 *
 */
public class TaskTimerApplication implements AppListener, Serializable {


    public static final String TASK_KEY = 
	"com.sun.sgs.analysis.tasks";

    public TaskTimerApplication() {

    }

    public void initialize(Properties properties) {

	TaskManager tm = AppContext.getTaskManager();
	
	// Get the string of tasks that we are suppoed to run
	String tasksStr = 
	    new PropertiesWrapper(properties).getProperty(TASK_KEY);
	String[] taskClasses = tasksStr.split(":");

	for (String taskClass : taskClasses) {
	    try {
		@SuppressWarnings("unchecked")
		Class<Task> clazz = (Class<Task>)(Class.forName(taskClass));
		Constructor<Task> c = clazz.getConstructor(new Class<?>[]{});
		Task t = c.newInstance(new Object[]{});
		System.out.println("Starting task: " + taskClass);
		tm.scheduleTask(t);
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    public ClientSessionListener loggedIn(ClientSession session) {
	// unused
	return null;
    }

}
