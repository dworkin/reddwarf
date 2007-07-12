package com.sun.sgs.benchmark.app.impl;

import java.io.Serializable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionException;

import com.sun.sgs.benchmark.app.BehaviorModule;

/**
 * 
 * 
 */
public class SendChannelMessageModule implements BehaviorModule, Serializable {

    private static final long serialVersionUID = 0x82F9E38CF1DL;

   /**
    * Returns an empty list of {@code Runnable} operations.
     *
     * @param args op-codes denoting the arguments
     *
     * @return an empty list
     */
    public List<Runnable> getOperations(ClientSession session, byte[] args) {
	return new LinkedList<Runnable>();
    }

    /**
     * Fill me in
     */
    public List<Runnable> getOperations(final ClientSession session, Object[] args) {
        
        System.out.println("SendChannelMessageModule invoked.");
        
       	List<Runnable> operations = new LinkedList<Runnable>();
	if (args.length != 2) {
	    System.out.printf("invalid parameter(s) to %s: %s\n",
                this, Arrays.toString(args));
	    return operations;
	}
        
	final String channelName, message;
        
	try {
	    channelName = (String)(args[0]);
        }
	catch (ClassCastException cce) {
	    System.out.printf("invalid parameter(s) to %s: %s\n" +
                "expected java.lang.String\n", this, args[0]);
	    return operations;
	}
        
        try {
            if (args[1] instanceof String) {
                message = (String)(args[1]);
            }
            else {
                message = junkString((Integer)(args[1]));
            }
	}
	catch (ClassCastException cce) {
	    System.out.printf("invalid parameter(s) to %s: %s\nexpected" +
                "java.lang.String or java.lang.Integer\n", this, args[1]);
	    return operations;
	}
        
	operations.add(new Runnable() {
		public void run() {
		    ChannelManager cm = AppContext.getChannelManager();
		    try {
			Channel chan = cm.getChannel(channelName);
			chan.send(message.getBytes());
		    } catch (NameNotBoundException nnbe) {
			System.out.printf("Client requested that server send a" +
                            " message on non-existent channel: %s", channelName);
		    } catch (TransactionException te) {
			System.out.printf("Channel.send() failed due to a " +
                            "transaction exception: %s\n", te.getMessage());
		    }
		}
	    });
	return operations;

    }
    
    /*
     * Returns a {@code String} of the specified length, filled with an
     * arbitrary character.
     *
     * @throws IllegalArgumentException if {@code len} is negative.
     */
    private static String junkString(int len) {
        if (len < 0)
            throw new IllegalArgumentException("Invalid length: " + len);
        
        char[] ca = new char[len];
        for (int i=0; i < len; i++) ca[i] = 'a';
        return new String(ca);
    }
}
