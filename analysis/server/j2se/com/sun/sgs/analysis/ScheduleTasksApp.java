/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
 * An application that schedules tasks specified by class names in a
 * configuration property.  Gets the names of the task classes from the value
 * of the {@value #TASK_KEY} configuration property, which should contain the
 * fully qualified names of classes that implement {@link Task}, separated by
 * colons.  Each task class should provide a public constructor with a {@link
 * Properties} parameter, which will be used to supply configuration
 * properties.
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
		Constructor<? extends Task> c =
		    clazz.getConstructor(Properties.class);
		Task t = c.newInstance(properties);
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
