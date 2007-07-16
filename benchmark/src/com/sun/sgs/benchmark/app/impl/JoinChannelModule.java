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
import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.benchmark.app.BehaviorException;

/**
 * TODO
 */
public class JoinChannelModule extends AbstractModuleImpl implements Serializable {

    private static final long serialVersionUID = 0x82F9E38CF1DL;
    
    /** Empty constructor */
    public JoinChannelModule() { }
    
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
        
        return createOperations(session, channelName);
    }
    
    /*
     * {@inheritDoc}
     */
    protected List<Runnable> getOperations(ClientSession session,
        ObjectInputStream in)
        throws BehaviorException, ClassNotFoundException, IOException
    {
        String channelName = (String)in.readObject();
        return createOperations(session, channelName);
    }
    
    /**
     * Does the actual work of creating the {@code Runnable} objects.
     */
    private List<Runnable> createOperations(final ClientSession session,
        final String channelName)
    {
        List<Runnable> operations = new LinkedList<Runnable>();
        
	operations.add(new Runnable() {
		public void run() {
		    ChannelManager cm = AppContext.getChannelManager();
		    try {			
			Channel chan = cm.getChannel(channelName);
			chan.join(session, null);
		    } catch (NameNotBoundException nnbe) {
                        System.err.println("**Error: Client tried to join a " +
                            "non-existent channel: " + channelName);
		    }
		}
	    });
	return operations;
    }
}
