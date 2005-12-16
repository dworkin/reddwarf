package com.sun.gi.logic.impl;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import java.lang.reflect.Method;
import java.io.Serializable;
import java.lang.reflect.*;
import java.nio.ByteBuffer;

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.Simulation;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */

class OutputRecord {
	UserID[] targets;

	UserID uid;

	ByteBuffer data;

	boolean reliable;

	ChannelID channel;

	public OutputRecord(ChannelID cid, UserID[] to, ByteBuffer buff,
			boolean reliableFlag) {
		channel = cid;
		data = ByteBuffer.allocate(buff.capacity());
		buff.flip(); // flip for read
		data.put(buff);
		reliable = reliableFlag;
		targets = new UserID[to.length];
		System.arraycopy(to, 0, targets, 0, to.length);
	}
}

public class SimTaskImpl implements SimTask {
	private Transaction trans;

	private GLOReference startObject;

	private Method startMethod;

	private Object[] startArgs;

	private Simulation simulation;

	private ClassLoader loader;

	private List<OutputRecord> outputList = new ArrayList<OutputRecord>();
	
	private Map<Serializable,Long> gloIDMap = new HashMap<Serializable,Long>();

	public SimTaskImpl(Simulation sim, ClassLoader loader, long startObjectID,
			Method startMethod, Object[] startArgs) {
		this.simulation = sim;
		this.startObject = this.makeReference(startObjectID);
		this.startMethod = startMethod;
		this.loader = loader;

		this.simulation = sim;
		Object newargs[] = new Object[startArgs.length + 1];
		newargs[0] = this;
		System.arraycopy(startArgs, 0, newargs, 1, startArgs.length);
		this.startArgs = newargs;

	}

	public void execute(ObjectStore ostore) {
		this.trans = ostore.newTransaction(simulation.getAppID(), loader);		
		outputList.clear();
		gloIDMap.clear();
		Serializable runobj = startObject.get(this);
		try {
			startMethod.invoke(runobj, startArgs);
			doOutput();
			trans.commit();
		} catch (InvocationTargetException ex) {
			ex.printStackTrace();
			trans.abort();
		} catch (IllegalArgumentException ex) {
			System.err.println("Exception on task execution:");
			System.err.println("Class of target:" + runobj.getClass());
			System.err.println("Name of method: " + startMethod.getName());
			System.err.println("Class of method: "
					+ startMethod.getDeclaringClass());
			ex.printStackTrace();
			trans.abort();
		} catch (IllegalAccessException ex) {
			ex.printStackTrace();
			trans.abort();
		} catch (DeadlockException de) {
			outputList.clear();
			simulation.queueTask(this); // requeue for later execution
		}
		outputList.clear();
	}

	/**
	 * doOutput
	 */
	private void doOutput() {
		for (OutputRecord rec : outputList) {
			simulation.sendMulticastData(rec.channel, rec.targets, rec.data,
					rec.reliable);
		}
	}

	public GLOReference makeReference(long id) {
		return new GLOReferenceImpl(id);
	}
	
	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimTask#makeReference(java.io.Serializable)
	 */
	public GLOReference makeReference(Serializable glo) throws InstantiationException {		
		Long idl = gloIDMap.get(glo);
		if (idl == null){
			throw new InstantiationException("Have no ID for supposed GLO ");
		}
		return makeReference(idl.longValue());
	}

	public Transaction getTransaction() {
		return trans;
	}

	public long getAppID() {
		return trans.getCurrentAppID();
	}

	/**
	 * addUserListener
	 * 
	 * @param ref
	 *            SOReference
	 * @return long
	 */
	public void addUserListener(GLOReference ref) {
		simulation.addUserListener(ref);
	}

	/**
	 * findSO
	 * 
	 * @param soName
	 *            String
	 * @return SOReference
	 */
	public GLOReference findSO(String soName) {
		return makeReference(trans.lookup(soName));
	}

	/**
	 * sendData
	 * 
	 * @param cid
	 *            ChannelID
	 * @param from
	 *            UserID
	 * @param bs
	 *            byte[]
	 */
	public void sendData(ChannelID cid, UserID[] to, ByteBuffer bs,
			boolean reliable) {
		outputList.add(new OutputRecord(cid, to, bs, reliable));
	}

	/**
	 * addUserDataListener
	 * 
	 * @param ref
	 *            SOReference
	 */
	public void addUserDataListener(UserID user, GLOReference ref) {
		simulation.addUserDataListener(user, ref);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.SimTask#addChannelListener(com.sun.gi.comm.routing.ChannelID,
	 *      com.sun.gi.logic.GLOReference)
	 */
	public void addChannelListener(ChannelID cid, GLOReference ref) {
		simulation.addChannelListener(cid, ref);
	}

	/**
	 * createSO
	 * 
	 * @param wurmPlayer
	 *            WurmPlayer
	 * @return SOReference
	 */
	public GLOReference createSO(Serializable simObject, String name) {
		
		GLOReferenceImpl ref = (GLOReferenceImpl) makeReference(trans.create(simObject, name));
		registerGLOID(ref.objID,simObject);
		return ref;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.SimTask#openChannel(java.lang.String)
	 */
	public ChannelID openChannel(String string) {
		return simulation.openChannel(string);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.SimTask#registerTimerEvent(long, boolean,
	 *      com.sun.gi.logic.GLOReference)
	 */
	public long registerTimerEvent(long delay, boolean repeat, GLOReference ref) {
		return simulation.registerTimerEvent(ref, delay, repeat);
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimTask#registerGLOID(long, java.io.Serializable)
	 */
	public void registerGLOID(long objID, Serializable glo) {
		gloIDMap.put(glo,new Long(objID));		
	}

	

}
