/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.profile.listener;

import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Outputs task timing data to a file for later use by Gnuplot.
 */
public class TaskRuntimeGraphOutputListener implements ProfileListener {
    
    private final Map<String,TaskTypeDetail> taskTimes;
    private final String directory;

    /**
     * Creates an instance of {@code TaskRuntimeGraphOutputListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code TaskOwner} to use for all tasks run by
     *        this listener
     * @param taskScheduler the {@code TaskScheduler} to use for
     *        running short-lived or recurring tasks
     * @param resourceCoord the {@code ResourceCoordinator} used to
     *        run any long-lived tasks
     */
    public TaskRuntimeGraphOutputListener(Properties properties, 
					  TaskOwner owner,
					  TaskScheduler taskScheduler,
					  ResourceCoordinator resourceCoord)
    {
	taskTimes = new HashMap<String,TaskTypeDetail>();
	directory = properties.getProperty("com.sun.sgs.app.root") + "/data/";
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent event) {
	// unused
    }

    /**
     * {@inheritDoc}
     */
    public void report(ProfileReport profileReport) {

        if (profileReport.wasTaskSuccessful()) {

            String name = profileReport.getTask().getBaseTaskType();
	    TaskTypeDetail detail = taskTimes.get(name);
	    if (detail == null) {
		try {		 
		    // NOTE: probably should have some configurable
		    // option to overwrite the existing file or not
		    File output = new File(directory + name + 
					   //"." + System.currentTimeMillis() + 
					   ".dat");
		    detail = new TaskTypeDetail(new PrintStream(output));
		    taskTimes.put(name, detail);
		} catch (Throwable t) {
		    t.printStackTrace();
		}
	    }	    
	    
	    detail.count++;
	    detail.printStream.printf("%d %d\n", detail.count, 
				      profileReport.getRunningTime());

	    if (detail.count % 100 == 0) 
		detail.printStream.flush();			    
	}       
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	for (String taskName : taskTimes.keySet()) {
	    PrintStream ps = taskTimes.get(taskName).printStream;
	    ps.flush();
	    ps.close();
	}
    }
    
    private static class TaskTypeDetail {
	
	int count;
	
	PrintStream printStream;

	TaskTypeDetail(PrintStream printStream) { 
	    count = 0;
	    this.printStream = printStream;
	}
    }

}
