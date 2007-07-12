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
       	List<Runnable> operations = new LinkedList<Runnable>();
	if (args.length < 2) {
	    System.out.printf("invalid parameter(s) to %s: %s\n",
			      this, Arrays.toString(args));
	    return operations;
	}
	String channelName = null;
	String message = null;
	try {
	    channelName = (String)(args[0]);
	    message = (String)(args[1]);
	}
	catch (ClassCastException cce) {
	    System.out.printf("invalid parameter(s) to %s: %s\n" +
			      "expected java.lang.String\n" ,
			      this, args[0]);
	    return operations;
	}

	final String name = channelName;
	final String m = message;

	operations.add(new Runnable() {
		public void run() {
		    ChannelManager cm = AppContext.getChannelManager();
		    try {
			Channel chan = cm.getChannel(name);
			chan.send(m.getBytes());
		    } catch (NameNotBoundException nnbe) {
			System.out.printf("Client tried send message on "
					  + "non-existent channel: %s", name);
		    }
		}
	    });
	return operations;

    }

}
