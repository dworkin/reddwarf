/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
