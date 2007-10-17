package com.sun.sgs.benchmark.scripts;

import com.sun.sgs.benchmark.client.*;

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class DatastoreContention {
    
    static final int DEFAULT_READERS = 0;

    static final int DEFAULT_WRITERS = 5;

    static final long DEFAULT_DELAY = 100;

    static final int DEFAULT_BYTES = 1 << 12;
    

    private final int readers;

    private final int writers;

    private final long delay;

    private final int bytes;
    

    public DatastoreContention(int readers,
			       int writers,
			       long delay,
			       int bytes) {
	this.readers = readers;
	this.writers = writers;
	this.delay = delay;
	this.bytes = bytes;
    }

    public DatastoreContention() {
	this(DEFAULT_READERS, DEFAULT_WRITERS,
	     DEFAULT_DELAY, DEFAULT_BYTES);

    }    
    

    public void run() {
	try {
	    BenchmarkClient client = new BenchmarkClient();
	  
	    client.processInput("login datastore-stresser password");
	    try { Thread.sleep(1000); } catch (Throwable t) { }
	    client.processInput("ds_create sharedvar [B " + (bytes >>> 2));

	    List<Thread> threads = new LinkedList<Thread>();
	    for (int i = 0; i < readers; ++i) {
		final int j = i;
		threads.add(new Thread() {
			public void run() {
			    try {
				BenchmarkClient c = new BenchmarkClient();
				c.processInput("login reader-" + j + " r");
				while(true) {
				    sleep(delay);
				    c.processInput("ds_read sharedvar");
				}
			    }
			    catch (Throwable t) {
				t.printStackTrace();
			    }				   			    
			}
		    });
	    }
	    for (int i = 0; i < writers; ++i) {
		final int j = i;
		threads.add(new Thread() {
			public void run() {
			    try {
				BenchmarkClient c = new BenchmarkClient();
				c.processInput("login writer-" + j + " w");
				while(true) {
				    sleep(delay);
				    c.processInput("ds_write sharedvar");
				}
			    }
			    catch (Throwable t) {
				t.printStackTrace();
			    }				   			    
			}
		    });
	    }

	    Collections.shuffle(threads);
	    try { Thread.sleep(1000); } catch (Throwable t) { }

	    for (Thread t : threads) {
		t.start();
		try {
		    Thread.sleep(20 + (int)(100 * Math.random()));
		}
		catch (InterruptedException ie) { } // silent
	    }

	}
	catch (ParseException e) {
	    e.printStackTrace();
	}		    
    }
    


    private static void usage() {
	System.out.println("Usage: java DatastoreContention "
			   + "<#readers> <#writers> <delay (ms)> "
			   + "<object size (bytes)>");
	System.exit(1);
    }
    
    public static void main(String[] args) {
	if (args.length > 0) {
	    if (args.length != 4)
		usage();
	    int readers = 0, writers = 0, delay = 0, bytes = 0;
	    try {
		readers = Integer.parseInt(args[0]);		
		writers = Integer.parseInt(args[1]);		
		delay = Integer.parseInt(args[2]);		
		bytes = Integer.parseInt(args[3]);		
	    }
	    catch (Throwable t) {
		usage();
	    }
	    new DatastoreContention(readers,writers,delay,bytes).run();
	}
	else
	    new DatastoreContention().run();
    }
}