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

	ObjectInputStream ois;

	try {
	    FileInputStream fis = new FileInputStream(traceFile);
	    ois = new ObjectInputStream(fis);
	} catch (IOException e) {
	    System.out.println(e);
	    return;
	}

	// ObjectStore ostore = connect(true, dspaceType, null);

	for (;;) {
	    TraceRecord o;
	    try {
		o = (TraceRecord) ois.readObject();
	    } catch (EOFException e) {
		break;
	    } catch (Exception e) {
		System.out.println(e);
		break;
	    }

	    System.out.println(o);
	}
    }
}

