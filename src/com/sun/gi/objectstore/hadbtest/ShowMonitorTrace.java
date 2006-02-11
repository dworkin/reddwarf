/*
 * Copyright 2006, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.TSOObjectStore;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.HadbDataSpace;
import com.sun.gi.objectstore.tso.dataspace.InMemoryDataSpace;
import com.sun.gi.objectstore.tso.dataspace.MonitoredDataSpace;
import com.sun.gi.objectstore.tso.dataspace.PersistantInMemoryDataSpace;
import com.sun.gi.objectstore.tso.dataspace.monitor.TraceRecord;
import com.sun.gi.objectstore.tso.dataspace.monitor.ReplayState;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Simple class to read back a sequence of {@link TraceRecord}s
 * and repeat the operations.
 */
public class ShowMonitorTrace {

    /**
     * Reads the tracerecord file (given as args[0]) and
     * lists the ops and how long they took to executed.
     */
    public static void main(String[] args) throws IOException {
	String traceFile = args[0];

	ObjectInputStream ois;

	try {
	    FileInputStream fis = new FileInputStream(traceFile);
	    ois = new ObjectInputStream(fis);
	} catch (IOException e) {
	    System.out.println(e);
	    return;
	}

	ReplayState replayState = new ReplayState();

	for (;;) {
	    TraceRecord o;
	    try {
		o = (TraceRecord) ois.readObject();
	    } catch (EOFException e) {
		System.out.println("DONE");
		break;
	    } catch (Exception e) {
		System.out.println(e);
		break;
	    }

	    System.out.println(o.getUnqualifiedClassName() + ": " +
	    		(o.getEndTime() - o.getStartTime()));
	}
    }
}

