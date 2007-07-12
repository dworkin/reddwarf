package com.sun.sgs.benchmark.app.impl;

import java.io.Serializable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.TransactionException;

import com.sun.sgs.benchmark.app.BehaviorModule;

/**
 * 
 * 
 */
public class SendDirectMessageModule implements BehaviorModule, Serializable {
    
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

        System.out.println("SendDirectMessageModule invoked.");
        
       	List<Runnable> operations = new LinkedList<Runnable>();
	if (args.length != 1) {
	    System.out.printf("invalid parameter(s) to %s: %s\n",
                this, Arrays.toString(args));
	    return operations;
	}
        
	final String message;
	
        try {
	    if (args[0] instanceof String) {
                message = (String)(args[0]);
            }
            else {
                message = junkString((Integer)(args[0]));
            }
	}
	catch (ClassCastException cce) {
	    System.out.printf("invalid parameter(s) to %s: %s\nexpected" +
                "java.lang.String of java.lang.Integer\n", this, args[0]);
	    return operations;
	}
        
	operations.add(new Runnable() {
		public void run() {
		    try {
			session.send(message.getBytes());
		    } catch (TransactionException te) {
			System.out.printf("Channel.join() failed due to a " +
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
