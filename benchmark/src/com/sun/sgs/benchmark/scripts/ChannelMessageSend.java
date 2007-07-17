package com.sun.sgs.benchmark.scripts;

import com.sun.sgs.benchmark.client.*;

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ChannelMessageSend {
    
    static final int DEFAULT_CLIENTS = 3;

    static final long DEFAULT_DELAY = 100;

    static final int DEFAULT_BYTES = 1 << 12;
    

    private final int clients;    

    private final long delay;

    private final int bytes;
    

    public ChannelMessageSend(int clients, long delay, int bytes) {
	this.clients = clients;
	this.delay = delay;
	this.bytes = bytes;
    }

    public ChannelMessageSend() {
	this(DEFAULT_CLIENTS, DEFAULT_DELAY, DEFAULT_BYTES);

    }    
    

    public void run() {
	try {
	    BenchmarkClient client = new BenchmarkClient();
	  
	    client.processInput("login datastore-stresser password");
	    try { Thread.sleep(1000); } catch (Throwable t) { }
	    client.processInput("create_channel common");

	    List<Thread> threads = new LinkedList<Thread>();

	    
	    final int bytes = this.bytes;

	    for (int i = 0; i < clients; ++i) {
		final int j = i;
		threads.add(new Thread() {
			public void run() {
			    try {
				BenchmarkClient c = new BenchmarkClient();
				c.processInput("login sender-" + j + " pswd");
				sleep(100 + (int)(100 * Math.random()));
				c.processInput("join common");
				c.processInput("wait_for join_channel");
				sleep(1000);
				while(true) {
				    sleep(delay);
				    c.processInput("send_channel common " +
						   bytes);
				}
			    }
			    catch (Throwable t) {
				t.printStackTrace();
			    }				
			}
		    });
	    }

	    try { Thread.sleep(1000); } catch (Throwable t) { }

	    for (Thread t : threads) {
		t.start();
		try {
		    Thread.sleep(50 + (int)(100 * Math.random()));
		}
		catch (InterruptedException ie) { } // silent
	    }

	}
	catch (ParseException e) {
	    e.printStackTrace();
	}		    
    }
    


    private static void usage() {
	System.out.println("Usage: java ChannelMessageSend "
			   + "<#clients> <delay (ms)> "
			   + "<message size (bytes)>");
	System.exit(1);
    }
    
    public static void main(String[] args) {
	if (args.length > 0) {
	    if (args.length != 4)
		usage();
	    int clients = 0, delay = 0, bytes = 0;
	    try {
		clients = Integer.parseInt(args[0]);		
		delay = Integer.parseInt(args[2]);		
		bytes = Integer.parseInt(args[3]);		
	    }
	    catch (Throwable t) {
		usage();
	    }
	    new ChannelMessageSend(clients,delay,bytes).run();
	}
	else
	    new ChannelMessageSend().run();
    }
}