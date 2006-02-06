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
public class ReplayMonitorTrace {

    public static void main(String[] args) throws IOException {
	String traceFile = args[0];
	String dspaceType = args[1];
	DataSpace dataSpace = null;

	ObjectInputStream ois;

	try {
	    FileInputStream fis = new FileInputStream(traceFile);
	    ois = new ObjectInputStream(fis);
	} catch (IOException e) {
	    System.out.println(e);
	    return;
	}

	ReplayState replayState = new ReplayState();

	dataSpace = TestUtil.openDataSpace(1, dspaceType);
	dataSpace.clear();

	long traceElapsed;
	long prevEndTime = -1;

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

	    if (dataSpace != null) {
		if (prevEndTime < 0) {
		    prevEndTime = o.getStartTime();
		}

		traceElapsed = o.getStartTime() - prevEndTime;
		if (traceElapsed > 1) {
		    System.out.println("snoozing for " + traceElapsed);
		    try {
			Thread.sleep(traceElapsed);
		    } catch (InterruptedException e) {
			System.out.println("interrupted");
			return ;
		    }
		} 
		o.replay(dataSpace, replayState);
		prevEndTime = o.getEndTime();
	    }
	}

	dataSpace.close();
    }
}

