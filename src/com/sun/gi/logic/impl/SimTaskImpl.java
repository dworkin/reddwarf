package com.sun.gi.logic.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.AccessTypeViolationException;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.Simulation;
import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;

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
	private ACCESS_TYPE accessType;

	private GLOReference startObject;

	private Method startMethod;

	private Object[] startArgs;

	private Simulation simulation;

	private ClassLoader loader;

	private List<OutputRecord> outputList = new ArrayList<OutputRecord>();
	private List<SimTask> taskLaunchList = new ArrayList<SimTask>();
	
	private Map<Serializable,Long> gloIDMap = new HashMap<Serializable,Long>();
	private Map<Serializable,ACCESS_TYPE> gloAccessMap = new HashMap<Serializable,ACCESS_TYPE>();

	public SimTaskImpl(Simulation sim, ClassLoader loader, ACCESS_TYPE access, long startObjectID,
			Method startMethod, Object[] startArgs) {
		this.simulation = sim;
		this.startObject = this.makeReference(startObjectID);
		this.startMethod = startMethod;
		this.loader = loader;
		this.accessType = access;
		this.simulation = sim;
		Object newargs[] = new Object[startArgs.length + 1];		
		newargs[0] = this;		
		System.arraycopy(startArgs,0,newargs,1,startArgs.length);
		this.startArgs = newargs;
	}

	

	public void execute(ObjectStore ostore) {
		this.trans = ostore.newTransaction(simulation.getAppID(), loader);	
		this.trans.start(); //tell trans its waking up to begin anew		
		Serializable runobj = null;
		switch (accessType){
			case GET:
				runobj = startObject.get(this);
				break;
			case PEEK:
				runobj = startObject.peek(this);
				break;
			case ATTEMPT:
				runobj = startObject.attempt(this);
				if (runobj == null){ // attempt failed
					trans.abort();				
					return;
				}
				break;
		}
		try {
			startMethod.invoke(runobj, startArgs);
			doOutput();
			trans.commit();
			for(SimTask task : taskLaunchList){
				simulation.queueTask(task);				
			}
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
			taskLaunchList.clear();
			gloIDMap.clear();
			gloAccessMap.clear();
			simulation.queueTask(this); // requeue for later execution
		}
		
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
		registerGLOID(ref.objID,simObject,ACCESS_TYPE.GET);
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
	public long registerTimerEvent(ACCESS_TYPE access, long delay, boolean repeat, GLOReference ref) {
		return simulation.registerTimerEvent(access, ref, delay, repeat);
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimTask#registerGLOID(long, java.io.Serializable)
	 */
	public void registerGLOID(long objID, Serializable glo,ACCESS_TYPE access) {
		gloIDMap.put(glo,new Long(objID));	
		gloAccessMap.put(glo,access);
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimTask#registerTimerEvent(com.sun.gi.logic.Simulation.ACCESS_TYPE, long, boolean, com.sun.gi.logic.GLOReference)
	 */
	public long registerTimerEvent(long delay, boolean repeat, GLOReference reference) {
		return registerTimerEvent(ACCESS_TYPE.GET,delay,repeat,reference);
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimTask#queueTask(com.sun.gi.logic.Simulation.ACCESS_TYPE, com.sun.gi.logic.GLOReference, java.lang.String, java.lang.Object[])
	 */
	public void queueTask(ACCESS_TYPE accessType, GLOReference target, Method method, Object[] parameters) {		
		try {
			taskLaunchList.add(simulation.newTask(accessType,target,method,
					scrubAndCopy(parameters)));
		} catch (SecurityException e) {
			
			e.printStackTrace();
		} catch (IOException e) {
			
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			
			e.printStackTrace();
		}
		
	}
	

	//used by scrub and copy
	class NoGLOObjectOutputStream extends ObjectOutputStream{

		/**
		 * @throws IOException
		 * @throws SecurityException
		 */
		protected NoGLOObjectOutputStream(ByteArrayOutputStream os) throws IOException, SecurityException {
			super(os);	
			enableReplaceObject(true);
		}		
		
		protected Object replaceObject(Object obj) throws IOException {
			if (gloIDMap.containsKey(obj)){
				throw new IOException("Attempt to serialize GLO!");
			}
			return obj;			
		}
		
	}



	/**
	 * @param parameters
	 * @return
	 * @throws IOException 
	 * @throws SecurityException 
	 * @throws ClassNotFoundException 
	 */
	private Object[] scrubAndCopy(Object[] parameters) throws SecurityException, IOException, ClassNotFoundException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		NoGLOObjectOutputStream oos = new NoGLOObjectOutputStream(baos);
		oos.writeObject(parameters);
		oos.close();
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		ObjectInputStream ois = new ObjectInputStream(bais);
		parameters = (Object[])ois.readObject();
		return parameters;
	}



	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimTask#queueTask(com.sun.gi.logic.Simulation.ACCESS_TYPE, com.sun.gi.logic.GLOReference, java.lang.String, java.lang.Object[])
	 */
	public void queueTask(GLOReference target, Method method, Object[] parameters) {		
		queueTask(ACCESS_TYPE.GET,target,method,parameters);
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimTask#access_check(com.sun.gi.logic.Simulation.ACCESS_TYPE)
	 */
	public void access_check(ACCESS_TYPE accessType, Object glo) {
		ACCESS_TYPE gloAcc = gloAccessMap.get(glo);
		if (gloAcc != accessType){
			throw new AccessTypeViolationException("Expected "+accessType+
					" check returned "+gloAcc);
		}
	}

	

}
