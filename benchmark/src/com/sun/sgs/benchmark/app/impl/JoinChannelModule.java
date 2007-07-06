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
 * A loadable module that always generates an empty list of {@code
 * Runnable} operations.
 */
public class JoinChannelModule implements BehaviorModule, Serializable {

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
     * Returns a list with one {@code Runnable} that connects this
     * client session to the channel specified in {@code args}.
     */
    public List<Runnable> getOperations(final ClientSession session, Object[] args) {
       	List<Runnable> operations = new LinkedList<Runnable>();
	if (args.length != 1) {
	    System.out.printf("invalid parameter(s) to %s: %s\n",
			      this, Arrays.toString(args));
	    return operations;
	}
	String channelName = null;
	try {
	    channelName = (String)(args[0]);
	}
	catch (ClassCastException cce) {
	    System.out.printf("invalid parameter(s) to %s: %s\n" +
			      "expected java.lang.String\n" ,
			      this, args[0]);
	}

	final String name = channelName;
	operations.add(new Runnable() {
		public void run() {
		    ChannelManager cm = AppContext.getChannelManager();
		    try {
			Channel chan = cm.getChannel(name);
			chan.join(session, null);
		    } catch (NameNotBoundException nnbe) {
			System.out.printf("Client tried to join non-existent"
					  + " channel: %s\n", name);
		    } catch (TransactionException te) {
			System.out.printf("Channel.join() failed due to a " +
					  "transaction exception: %s\n", 
					  te.getMessage());
		    }
		}
	    });
	return operations;

    }

}