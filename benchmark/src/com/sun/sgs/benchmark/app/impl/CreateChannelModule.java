package com.sun.sgs.benchmark.app.impl;

import java.io.Serializable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameExistsException;

import com.sun.sgs.benchmark.app.BehaviorModule;

/**
 * A loadable module that always generates an empty list of {@code
 * Runnable} operations.
 */
public class CreateChannelModule implements BehaviorModule, Serializable {

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
     *
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
			cm.createChannel(name, null,
					 Delivery.RELIABLE);
			System.out.printf("created new channel: %s\n",
					  name);
		    } catch (NameExistsException nee) {
			System.out.printf("Client tried to recreate an" +
					  " already existing channel: %s\n", 
					  name);
		    }
		}
	    });
	return operations;

    }

}