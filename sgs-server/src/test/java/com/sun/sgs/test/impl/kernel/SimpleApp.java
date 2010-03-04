/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
        try {
            Thread.sleep(500L);
        } catch (InterruptedException e) {
            //ignore
        }
	TaskManager taskManager = AppContext.getTaskManager();
	taskManager.schedulePeriodicTask(new MyTask(), 0, 2000);
    }
}
