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
import com.sun.sgs.app.ClientSession;

import com.sun.sgs.benchmark.app.BehaviorException;

/**
 * TODO
 */
public class SendDirectMessageModule extends AbstractModuleImpl implements Serializable {

    private static final long serialVersionUID = 0x82F9E38CF1DL;
    
    /** Empty constructor */
    public SendDirectMessageModule() { }
    
    // implement AbstractModuleImpl
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args)
        throws BehaviorException
    {
        String message = null;
        Integer multiples = new Integer(1);
        
        initVars(new Object[] { message, multiples },
            new Class<?>[] { String.class, Integer.class }, args, 1);
        
        message = multiplyString(message, multiples.intValue());
        return createOperations(session, message);
    }
    
    /*
     * {@inheritDoc}
     */
    protected List<Runnable> getOperations(ClientSession session,
        ObjectInputStream in)
        throws BehaviorException, ClassNotFoundException, IOException
    {
        String message = (String)in.readObject();
        int multiples = (in.available() > 0) ? in.readInt() : 1;
        
        message = multiplyString(message, multiples);
        return createOperations(session, message);
    }
    
    /**
     * Does the actual work of creating the {@code Runnable} objects.
     */
    private List<Runnable> createOperations(final ClientSession session,
        final String message)
        throws BehaviorException
    {
        List<Runnable> operations = new LinkedList<Runnable>();
        
	operations.add(new Runnable() {
		public void run() {
                    session.send(message.getBytes());
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
