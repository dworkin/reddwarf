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
import com.sun.gi.objectstore.tso.dataspace.InMemoryDataSpace;
import com.sun.gi.objectstore.tso.dataspace.PersistantInMemoryDataSpace;
import com.sun.gi.objectstore.tso.dataspace.HadbDataSpace;
import com.sun.gi.objectstore.tso.dataspace.MonitoredDataSpace;
import com.sun.gi.objectstore.tso.dataspace.monitor.LogEntry;
import java.io.Serializable;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

/**
 * @author Daniel Ellard
 */

public class ReplayMonitorTrace {

    public static void main(String[] args) throws IOException {
	String traceFile = args[0];
	String dspaceType = args[1];

	FileInputStream fis;
	ObjectInputStream ois;

	try {
	    fis = new FileInputStream(traceFile);
	    ois = new ObjectInputStream(fis);
	} catch (IOException e) {
	    System.out.println(e);
	    return;
	}

	// ObjectStore ostore = connect(true, dspaceType, null);

	while (fis.available() > 0) {
	    System.out.println("avail: " + fis.available());
	    LogEntry o;
	    try {
		o = (LogEntry) ois.readObject();
	    } catch (Exception e) {
		System.out.println(e);
		break;
	    }

	    System.out.println(o.getClass().getName() + ": " +
		    o.getStartTime());
	}
    }
}

