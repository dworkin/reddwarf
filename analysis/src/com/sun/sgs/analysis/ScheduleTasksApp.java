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
 * An application that schedules tasks specified by class names.  Gets the
 * names of the task classes from the value of the {@value TASK_KEY}
 * configuration property, which should contain the fully qualified names of
 * classes that implement {@link Task}, separated by colons.
 */
public class ScheduleTasksApp implements AppListener, Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * The name of the configuration property that specifies the names of task
     * classes.
     */
    public static final String TASK_KEY = "com.sun.sgs.analysis.tasks";

    /** Creates an instance of this class. */
    public ScheduleTasksApp() { }

    /** {@inheritDoc} */
    public void initialize(Properties properties) {
	TaskManager tm = AppContext.getTaskManager();

	/* Get the string of tasks that we are supposed to run */
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
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }

    /** {@inheritDoc} */
    public ClientSessionListener loggedIn(ClientSession session) {
	/* Unused */
	return null;
    }
}
