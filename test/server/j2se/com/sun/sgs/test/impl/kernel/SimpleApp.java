/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import java.io.Serializable;
import java.util.Properties;

/**
 * Defines a simple SGS application that schedules a single, repeating task.
 */
public class SimpleApp implements AppListener, Serializable {
    private static final long serialVersionUID = 1;
    private static class MyTask implements ManagedObject, Serializable, Task {
	private static final long serialVersionUID = 1;
	private int count = 0;
	MyTask() { }
	public void run() {
	    count++;
	    System.out.println("count=" + count);
	}
    }
    public ClientSessionListener loggedIn(ClientSession session) {
	return null;
    }
    public void initialize(Properties props) {
	TaskManager taskManager = AppContext.getTaskManager();
	taskManager.schedulePeriodicTask(new MyTask(), 0, 2000);
    }
}
