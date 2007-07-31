package com.sun.sgs.benchmark.scripts;

import com.sun.sgs.benchmark.client.*;

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ChannelMessageSend {
    
    static final int DEFAULT_CLIENTS = 60;
    static final long DEFAULT_DELAY = 500;
    static final int DEFAULT_BYTES = 500;
    
    /** The host that the server is running on. */
    public static final String HOSTNAME = "dstar3";
    
    public static final String CHANNEL_NAME = "ChannelMessageSend_script";
    
    /** 
     * If true, all clients send to all recipients on the channel; if false,
     * clients just send to one recipient on the channel.
     */
    public static final boolean BCAST_TEST = false;
    
    private final int clients;
    private final long delay;
    private final int bytes;
    private final String message;
    
    public ChannelMessageSend(int clients, long delay, int bytes) {
	this.clients = clients;
	this.delay = delay;
	this.bytes = bytes;
        
        System.out.println("Starting up with #client=" + clients +
            ",  delay=" + delay + "ms,  bytes=" + bytes + ".");
        
        StringBuffer sb = new StringBuffer();
        for (int i=0; i < bytes; i++) sb.append("A");
        message = sb.toString();
    }

    public ChannelMessageSend() {
	this(DEFAULT_CLIENTS, DEFAULT_DELAY, DEFAULT_BYTES);
    }
    
    public void run() {
	try {
	    BenchmarkClient client = new BenchmarkClient();
            client.processInput("config " + HOSTNAME);
            client.processInput("login user password");
            client.processInput("wait_for login");
	    client.processInput("create_channel " + CHANNEL_NAME);
            
	    List<Thread> threads = new LinkedList<Thread>();
            
	    for (int i = 0; i < clients; ++i) {
		final int j = i;
		threads.add(new Thread() {
			public void run() {
			    try {
				BenchmarkClient c = new BenchmarkClient();
                                c.printCommands(false);
                                c.printNotices(true);
                                c.processInput("config " + HOSTNAME);
				c.processInput("login sender-" + j + " pswd");
                                c.processInput("wait_for login");
                                c.processInput("join " + CHANNEL_NAME);
				c.processInput("wait_for join_channel");
				Thread.sleep(1000);
                                
                                String sendCmd;
                                
                                if (BCAST_TEST) {
                                    sendCmd = String.format("chsend %s %s",
                                        CHANNEL_NAME, message);
                                } else {
                                    int recipientId = (j == 0) ? 1 : j - 1;
                                    
                                    sendCmd = String.format("pm %s %s %s",
                                        CHANNEL_NAME,
                                        Integer.toHexString(recipientId),
                                        message);
                                }
                                
				while (true) {
				    sleep(delay);
                                    c.processInput(sendCmd);
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
		    Thread.sleep(500 + (int)(1000 * Math.random()));
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
	    if (args.length != 3)
		usage();
	    int clients = 0, delay = 0, bytes = 0;
	    try {
		clients = Integer.parseInt(args[0]);
		delay = Integer.parseInt(args[1]);
		bytes = Integer.parseInt(args[2]);
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
