package com.sun.sgs.analysis;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Properties;

/**
 * Bootstrapping application for scheduling testing tasks.
 */
public class TaskTimerApplication implements AppListener, Serializable {

    public static final String TASK_KEY = "com.sun.sgs.analysis.tasks";

    public TaskTimerApplication() { }

    public void initialize(Properties properties) {
	TaskManager tm = AppContext.getTaskManager();

	// Get the string of tasks that we are supposed to run
	String tasksStr = properties.getProperty(TASK_KEY);
	String[] taskClasses = tasksStr.split(":");

	for (String taskClass : taskClasses) {
	    try {
		Class<? extends Task> clazz =
		    Class.forName(taskClass).asSubclass(Task.class);
		Constructor<? extends Task> c = clazz.getConstructor();
		Task t = c.newInstance();
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
