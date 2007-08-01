/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.app.impl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.benchmark.app.BehaviorModule;
import com.sun.sgs.benchmark.app.BehaviorException;

import com.sun.sgs.benchmark.server.RemoteMethodRequestHandler;

import com.sun.sgs.benchmark.shared.CustomTaskType;
import com.sun.sgs.benchmark.shared.MethodRequest;

/**
 * TODO
 */
public class PeriodicTaskModule extends AbstractModuleImpl implements Serializable {

    private static final long serialVersionUID = 1L;
    
    /** Counts the number of periodic tasks queued so far. */
    private static int periodicTasksQueued = 0;
    
    /** Empty constructor */
    public PeriodicTaskModule() { }
    
    // implement AbstractModuleImpl
    
    /**
     * {@inheritDoc}
     */
    public List<Runnable> getOperations(ClientSession session, Object[] args)
        throws BehaviorException
    {
        Long delay = null, period = null;
        CustomTaskType type = null;
        Integer cmdCount = null;
        List<MethodRequest> commands = new LinkedList<MethodRequest>();
        
        initVars(new Object[] { type, delay, period, cmdCount },
            new Class[] { CustomTaskType.class, Long.class,
                          Long.class, Integer.class },
            args, 4);
        
        if (cmdCount.intValue() != (args.length - 4))
            throw new IllegalArgumentException("Not enough arguments found - " +
                "count arg=" + cmdCount.intValue() + ", but only " +
                (args.length - 4) + " additional arguments present.");
        
        for (int i=4; i < args.length; i++) {
            commands.add((MethodRequest)args[i]);
        }
        
        return createOperations(session, type, delay.longValue(), period.longValue(),
            commands);
    }
    
    /*
     * {@inheritDoc}
     */
    protected List<Runnable> getOperations(ClientSession session,
        ObjectInputStream in)
        throws BehaviorException, ClassNotFoundException, IOException
    {
        CustomTaskType type = (CustomTaskType)in.readObject();
        long delay = in.readLong();
        long period = in.readLong();
        int cmdCount = in.readInt();
        
        List<MethodRequest> commands = new LinkedList<MethodRequest>();
        
        for (int i=0; i < cmdCount; i++) {
            commands.add((MethodRequest)in.readObject());
        }
        
        return createOperations(session, type, delay, period, commands);
    }
    
    /**
     * Does the actual work of creating the {@code Runnable} objects.
     */
    private List<Runnable> createOperations(final ClientSession session,
        final CustomTaskType type, final long delay, final long period,
        final List<MethodRequest> commands)
    {
        List<Runnable> operations = new LinkedList<Runnable>();
        
	operations.add(new Runnable() {
		public void run() {
                    TaskManager tm = AppContext.getTaskManager();
                    Task task;
                    
                    if (type == CustomTaskType.ONESHOT) {
                        task = new CustomClientTask(session, commands, type);
                        tm.scheduleTask(task, delay);
                    } else {
                        /** some kind of periodic task */
                        if (periodicTasksQueued == 0)
                            task = new CustomClientTask(session, commands, type);
                        else
                            task = new CustomClientTask2(session, commands, type);
                        
                        tm.schedulePeriodicTask(task, delay, period);
                        periodicTasksQueued++;
                    }
                    
                    if (BehaviorModule.ENABLE_INFO_OUTPUT)
                        System.out.printf("%s: Scheduled custom task.  " +
                            "type=%s, #commands=%d, delay=%d, period=%d.\n",
                            "PeriodicTaskModule", type, commands.size(), delay,
                            period);
		}
	    });
	return operations;
    }
}

class CustomClientTask implements Serializable, Task {
    
    private static final long serialVersionUID = 1L;
    
    /* The {@code ClientSession} that created this task, or an ancestor task. */
    private ClientSession session;
    
    /* The commands to be executed by this task. */
    private List<MethodRequest> commands;
    
    /* The task-type, which dictates how the commands are executed. */
    private CustomTaskType type;
    
    public CustomClientTask(ClientSession session, MethodRequest command,
        CustomTaskType type)
    {
        this.session = session;
        this.type = type;
        commands = new LinkedList<MethodRequest>();
        commands.add(command);
    }
    
    public CustomClientTask(ClientSession session, List<MethodRequest> commands,
        CustomTaskType type)
    {
        this.session = session;
        this.commands = commands;
        this.type = type;
    }
    
    public void run() throws Exception {
        TaskManager tm;
        
        if (commands.size() == 0) return;
        
        switch (type) {
        case PARALLEL:
            tm = AppContext.getTaskManager();
            
            /* Create a new task for each individual command. */
            for (MethodRequest req : commands) {
                tm.scheduleTask(new CustomClientTask(session, req,
                                    CustomTaskType.SINGULAR));
            }
            break;

        case SERIAL:
            tm = AppContext.getTaskManager();
            
            /* Execute the first command. */
            RemoteMethodRequestHandler.runMethodRequest(commands.get(0),
                session);
            
            /* Create a new (serial) task for the remaining commands. */
            if (commands.size() > 1) {
                List<MethodRequest> remaining =
                    new LinkedList<MethodRequest>(commands.subList(1,
                                                      commands.size()));
                
                tm.scheduleTask(new CustomClientTask(session, remaining,
                                    CustomTaskType.SERIAL));
            }
            break;

        case ONESHOT:
        case SINGULAR:
            /* Execute all commands in a row right now. */
            for (MethodRequest req : commands) {
                RemoteMethodRequestHandler.runMethodRequest(req, session);
            }
            break;
            
        default:
            throw new IllegalStateException("Unknown CustomTaskType: " + type);
        }
    }
}

/*
 * Ugly, ugly hack needed for profiling operations to work.  (no other way to
 * distinguish between different periodic tasks other than to change the base
 * task class)
 */
class CustomClientTask2 extends CustomClientTask {

    public CustomClientTask2(ClientSession session, MethodRequest command,
        CustomTaskType type)
    {
        super(session, command, type);
    }
    
    public CustomClientTask2(ClientSession session, List<MethodRequest> commands,
        CustomTaskType type)
    {
        super(session, commands, type);
    }
}
