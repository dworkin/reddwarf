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

package com.sun.sgs.impl.profile.listener;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Outputs task timing data to files for later use by Gnuplot.  The files are
 * stored in the {@code data} subdirectory of the application root directory,
 * as specified by the {@code com.sun.sgs.app.root} configuration property.  A
 * separate file is created for each base task type.
 */
public class TaskRuntimeGraphOutputListener implements ProfileListener {

    /** A map from basic task type to information collected for that type. */
    private final Map<String, TaskTypeDetail> taskTimes =
	new HashMap<String, TaskTypeDetail>();

    /** The directory for storing results. */
    private final String directory;

    /**
     * Creates an instance of {@code TaskRuntimeGraphOutputListener}.
     *
     * @param properties the {@code Properties} for this listener
     * @param owner the {@code Identity} to use for all tasks run by
     *        this listener
     * @param registry the {@code ComponentRegistry} containing the
     *        available system components
     */
    public TaskRuntimeGraphOutputListener(Properties properties,
                                          Identity owner,
                                          ComponentRegistry registry)
    {
	String rootDir = properties.getProperty("com.sun.sgs.app.root");
	if (rootDir == null) {
	    throw new IllegalArgumentException(
		"The com.sun.sgs.app.root property must be specified");
	}
	directory = rootDir + File.separator + "data" + File.separator;
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
		    File output = new File(directory + name + ".dat");
		    detail = new TaskTypeDetail(new PrintStream(output));
		    taskTimes.put(name, detail);
		} catch (Exception e) {
		    e.printStackTrace();
		    return;
		}
	    }
	    detail.count++;
	    detail.printStream.printf("%d %d%n", detail.count,
				      profileReport.getRunningTime());
	    if (detail.count % 100 == 0) {
		detail.printStream.flush();
	    }
	}
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
	for (TaskTypeDetail detail : taskTimes.values()) {
	    PrintStream ps = detail.printStream;
	    ps.flush();
	    ps.close();
	}
    }

    /** Records information for a particular task type. */
    private static class TaskTypeDetail {

	/** The number of tasks of this type. */
	int count = 0;

	/** A stream for printing output for this type. */
	final PrintStream printStream;

	/** Creates an instance using the specified stream. */
	TaskTypeDetail(PrintStream printStream) {
	    this.printStream = printStream;
	}
    }

}
