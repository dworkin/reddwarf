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

import com.sun.sgs.benchmark.app.BehaviorModule;
import com.sun.sgs.benchmark.app.BehaviorException;

/**
 * TODO
 */
public class SendChannelMessageModule extends AbstractModuleImpl implements Serializable {

    private static final long serialVersionUID = 0x82F9E38CF1DL;
    
    /** Empty constructor */
    public SendChannelMessageModule() { }
    
    // implement AbstractModuleImpl
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args)
        throws BehaviorException
    {
        String channelName = null, message = null;
        Integer multiples = new Integer(1);
        
        initVars(new Object[] { channelName, message, multiples },
            new Class<?>[] { String.class, String.class, Integer.class}, args, 2);
        
        message = multiplyString(message, multiples.intValue());
        
        return createOperations(channelName, message);
    }
    
    /*
     * {@inheritDoc}
     */
    protected List<Runnable> getOperations(ClientSession session,
        ObjectInputStream in)
        throws BehaviorException, ClassNotFoundException, IOException
    {
        String channelName = (String)in.readObject();
        String message = (String)in.readObject();
        int multiples = (in.available() > 0) ? in.readInt() : 1;
        
        message = multiplyString(message, multiples);
        return createOperations(channelName, message);
    }
    
    /**
     * Does the actual work of creating the {@code Runnable} objects.
     */
    private List<Runnable> createOperations(final String channelName,
        final String message)
    {
        List<Runnable> operations = new LinkedList<Runnable>();
        
	operations.add(new Runnable() {
		public void run() {
		    ChannelManager cm = AppContext.getChannelManager();
		    try {
			Channel chan = cm.getChannel(channelName);
                        chan.send(message.getBytes());
                        if (BehaviorModule.ENABLE_INFO_OUTPUT)
                            System.out.printf("%s: Sending on channel \"%s\" " +
                                ": %s\n", "SendChannelMessageModule",
                                channelName, message);
		    } catch (NameNotBoundException nnbe) {
			System.err.println("**Error: Client requested " +
                            "that server send a message on a non-existent " +
                            "channel: " + channelName);
		    }
		}
	    });
	return operations;
    }
    
    /*
     * Returns a {@code String} consisting of {@code count} concatenations of
     * {@code s}.
     *
     * @throws IllegalArgumentException if {@code len} is negative.
     */
    private static String multiplyString(String s, int count) {
        if (count < 0)
            throw new IllegalArgumentException("count must be non-negative: " +
                count);
        
        StringBuffer sb = new StringBuffer();
        for (int i=0; i < count; i++) sb.append(s);
        return new String(sb);
    }
}
