/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.app.impl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.NameExistsException;

import com.sun.sgs.benchmark.app.BehaviorModule;
import com.sun.sgs.benchmark.app.BehaviorException;

/**
 * TODO
 */
public class CreateChannelModule extends AbstractModuleImpl implements Serializable {

    private static final long serialVersionUID = 0x82F9E38CF1DL;
    
    /** Empty constructor */
    public CreateChannelModule() { }
    
    // implement AbstractModuleImpl
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args)
        throws BehaviorException
    {
        String channelName = null;
        
        initVars(new Object[] { channelName }, new Class<?>[] { String.class },
            args, 1);
        
        return createOperations(channelName);
    }
    
    /*
     * {@inheritDoc}
     */
    protected List<Runnable> getOperations(ClientSession session,
        ObjectInputStream in)
        throws BehaviorException, ClassNotFoundException, IOException
    {
        String channelName = (String)in.readObject();
        return createOperations(channelName);
    }
    
    /**
     * Does the actual work of creating the {@code Runnable} objects.
     */
    private List<Runnable> createOperations(final String channelName) {
        List<Runnable> operations = new LinkedList<Runnable>();
        
	operations.add(new Runnable() {
		public void run() {
		    ChannelManager cm = AppContext.getChannelManager();
		    try {
			cm.createChannel(channelName, null, Delivery.RELIABLE);
			if (BehaviorModule.ENABLE_INFO_OUTPUT)
                            System.out.printf("%s: created new channel \"%s\"\n",
                                "CreateChannelModule", channelName);
		    } catch (NameExistsException nee) {
                        System.err.println("**Error: Client tried to create " +
                            "an existing channel: " + channelName);
		    }
		}
	    });
	return operations;
    }
}
