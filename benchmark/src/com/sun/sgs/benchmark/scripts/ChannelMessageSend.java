package com.sun.sgs.benchmark.scripts;

import com.sun.sgs.benchmark.client.*;

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ChannelMessageSend {
    public static enum SendType {
        BCAST,  /* send to all on channel */
        SINGLE, /* send to one recipient*/
        NONE;   /* don't send anything */
    };
    
    static final int DEFAULT_CLIENTS = 60;
    static final long DEFAULT_INTERVAL = 500;
    static final int DEFAULT_BYTES = 500;
    
    /** The host that the server is running on. */
    public static final String HOSTNAME = "dstar3";
    
    public static final String CHANNEL_NAME = "ChannelMessageSend_script";
    
    /** 
     * Dictates the type of test being run.
     */
    public static final SendType sendType = SendType.BCAST;
    
    private final int clients;
    private final long interval;
    private final int bytes;
    private final String message;
    
    public ChannelMessageSend(int clients, long interval, int bytes) {
	this.clients = clients;
	this.interval = interval;
	this.bytes = bytes;
        
        System.out.printf("Starting up with clients=%d,  interval=%d ms" +
            ", strlen=%d, sendType=%s\n", clients, interval, bytes,
            sendType.toString());
        
        StringBuffer sb = new StringBuffer();
        for (int i=0; i < bytes; i++) sb.append("A");
        message = sb.toString();
    }

    public ChannelMessageSend() {
	this(DEFAULT_CLIENTS, DEFAULT_INTERVAL, DEFAULT_BYTES);
    }
    
    public void run() {
	try {
	    BenchmarkClient client = new BenchmarkClient();
            client.processInput("config " + HOSTNAME);
            client.processInput("login user password");
            client.processInput("wait_for login");
	    client.processInput("create_channel " + CHANNEL_NAME);
            
	    List<Thread> threads = new LinkedList<Thread>();
            
            /** Create all of the threads. */
	    for (int i = 0; i < clients; ++i) {
		threads.add(new SenderClientThread(i));
	    }
            
            /** Pause 1 second just to calm down... */
	    try { Thread.sleep(1000); } catch (Throwable t) { }
            
            /* Start up each thread with a small random pause after each one. */
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
    
    /**
     * Returns a string constructed with the contents of the byte
     * array converted to hex format.
     *
     * @param bytes a byte array to convert
     * @return the converted byte array as a hex-formatted string
     */
    private static String toHexString(byte[] bytes) {
        StringBuffer buf = new StringBuffer(2 * bytes.length);
        for (byte b : bytes) {
            buf.append(String.format("%02X", b));
        }
        return buf.toString();
    }

    private static void usage() {
	System.out.println("Usage: java ChannelMessageSend "
			   + "<#clients> <interval (ms)> "
			   + "<message size (bytes)>");
	System.exit(1);
    }

    public static void main(String[] args) {
	if (args.length > 0) {
	    if (args.length != 3)
		usage();
	    int clients = 0, interval = 0, bytes = 0;
	    try {
		clients = Integer.parseInt(args[0]);
		interval = Integer.parseInt(args[1]);
		bytes = Integer.parseInt(args[2]);
	    }
	    catch (Throwable t) {
		usage();
	    }
	    new ChannelMessageSend(clients,interval,bytes).run();
	}
	else
	    new ChannelMessageSend().run();
    }
    
    /**
     * Inner class: SenderClientThread
     */
    public class SenderClientThread extends Thread {
        /**
         * Local ID for this client.
         */
        private final int id;
        
        /**
         * Creates a new {@code SenderClientThread}.
         */
        public SenderClientThread(int id) {
            super("SenderClientThread-" + id);
            this.id = id;
        }
        
        @Override
        public void run() {
            try {
                BenchmarkClient client = new BenchmarkClient();
                client.printCommands(false);
                client.printNotices(true);
                client.processInput("config " + HOSTNAME);
                client.processInput("login sender-" + id + " pswd");
                client.processInput("wait_for login");
                client.processInput("join " + CHANNEL_NAME);
                client.processInput("wait_for join_channel");
                Thread.sleep(1000);
                
                String sendCmd = null;
                String hexSessionId =
                    toHexString(client.getSessionId().toBytes());
                
                switch (sendType) {
                case BCAST:
                    System.out.printf("Client #%d [%s] will broadcast to" +
                        " channel.\n", id, hexSessionId);
                    
                    sendCmd = String.format("chsend %s %s", CHANNEL_NAME,
                        message);
                    break;

                case SINGLE:
                    long longSessionId = Long.parseLong(hexSessionId, 16);
                    long longRecipSessionId;
                    
                    if (longSessionId <= 1)
                        longRecipSessionId = 2;
                    else
                        longRecipSessionId = longSessionId - 1;
                    
                    String hexRecipSessionId =
                        Long.toHexString(longRecipSessionId);
                    
                    System.out.printf("Client #%d [%s] will send to recipient" +
                        " [%s].\n", id, hexSessionId, hexRecipSessionId);
                    
                    sendCmd = String.format("pm %s %s %s", CHANNEL_NAME,
                        hexRecipSessionId, message);
                    break;

                case NONE:
                    System.out.printf("Client $%d [%s] will not send at all.\n",
                        id, hexSessionId);
                    
                    sendCmd = null;
                    break;
                }
                
                while (true) {
                    sleep(interval);
                    if (sendCmd != null) client.processInput(sendCmd);
                }
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
