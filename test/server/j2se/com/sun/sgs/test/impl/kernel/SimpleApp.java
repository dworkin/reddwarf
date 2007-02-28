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
    private static class MyClientSessionListener
	implements ClientSessionListener, Serializable
    {
	private static final long serialVersionUID = 1;
	MyClientSessionListener() { }
	public void receivedMessage(byte[] message) { }
	public void disconnected(boolean graceful) { }
    }
    private static class MyTask implements ManagedObject, Serializable, Task {
	private static final long serialVersionUID = 1;
	private final String appName;
	MyTask(String appName) {
	    this.appName = appName;
	}
	public void run() {
	    System.out.println("SimpleApp.MyTask.run appName:" + appName);
	}
    }
    public ClientSessionListener loggedIn(ClientSession session) {
	System.out.println("SimpleApp.loggedIn(" + session + ")");
	return new MyClientSessionListener();
    }
    public void initialize(Properties props) {
	System.out.println("SimpleApp.initialize(" + props + ")");
	String appName = props.getProperty("com.sun.sgs.app.name");
	TaskManager taskManager = AppContext.getTaskManager();
	taskManager.schedulePeriodicTask(new MyTask(appName), 0, 2000);
    }
}
