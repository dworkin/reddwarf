package com.sun.sgs.benchmark.scripts;

import com.sun.sgs.benchmark.client.*;

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class DirectSend {
    
    static final int DEFAULT_CLIENTS = 60;
    static final long DEFAULT_DELAY = 500;
    static final int DEFAULT_BYTES = 500;//1 << 12;
    
    private final int clients;
    private final long delay;
    private final int bytes;
    
    public DirectSend(int clients, long delay, int bytes) {
	this.clients = clients;
	this.delay = delay;
	this.bytes = bytes;
        
        System.out.println("Starting up with #client=" + clients +
            ",  delay=" + delay + "ms,  bytes=" + bytes + ".");
    }

    public DirectSend() {
	this(DEFAULT_CLIENTS, DEFAULT_DELAY, DEFAULT_BYTES);
    }
    
    public void run() {
        List<Thread> threads = new LinkedList<Thread>();
        
        final int bytes = this.bytes;
        
        for (int i = 0; i < clients; ++i) {
            final int j = i;
            threads.add(new Thread() {
                    public void run() {
                        try {
                            BenchmarkClient c = new BenchmarkClient();
                            c.printCommands(false);
                            c.printNotices(true);
                            c.processInput("config dstar2");
                            c.processInput("login sender-" + j + " pswd");
                            c.processInput("wait_for login");
                            Thread.sleep(1000);
                            while (true) {
                                sleep(delay);
                                c.processInput("send_direct " + bytes);
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
                //Thread.sleep(50 + (int)(100 * Math.random()));
                Thread.sleep(1000);
            }
            catch (InterruptedException ie) { } // silent
        }
    }
    
    private static void usage() {
	System.out.println("Usage: java DirectSend "
            + "<#clients> <delay (ms)> <message size (bytes)>");
    }
    
    public static void main(String[] args) {
	if (args.length > 0) {
	    if (args.length != 4) {
		usage();
            } else {
                int clients = 0, delay = 0, bytes = 0;
                try {
                    clients = Integer.parseInt(args[0]);
                    delay = Integer.parseInt(args[2]);
                    bytes = Integer.parseInt(args[3]);
                    new DirectSend(clients,delay,bytes).run();
                }
                catch (Throwable t) {
                    usage();
                }
            }
	}
	else {
	    new DirectSend().run();
        }
    }
}
