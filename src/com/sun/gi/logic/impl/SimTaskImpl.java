package com.sun.gi.logic.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
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
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.logic.AccessTypeViolationException;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;

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

class OpenSocketRecord {
    long socketID;
    ACCESS_TYPE access;
    long objID;
    String host; 
    int port; 
    boolean reliable;

    public OpenSocketRecord(long sid, ACCESS_TYPE access, long objID,
	    String host, int port, boolean reliable) {
	this.socketID = sid;
	this.access = access;
	this.objID = objID;
	this.host = host;
	this.port = port;
	this.reliable = reliable;
    }
}

class SocketSendRecord{
    long socketID;
    ByteBuffer data;

    public SocketSendRecord(long socketID, ByteBuffer buff) {
	this.socketID = socketID;
	data = ByteBuffer.allocate(buff.capacity());
	buff.flip(); // flip for read
	data.put(buff);
    }
}

class CloseSocketRecord {
}

class TimerRecord {
    long tid;
    ACCESS_TYPE access;
    long delay;
    boolean repeat;
    long objID;

    public TimerRecord(long tid, ACCESS_TYPE access, long delay, boolean
	    repeat, long objID) {

	this.tid = tid;
	this.access = access;
	this.delay = delay;
	this.repeat = repeat;
	this.objID = objID;
    }
}

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

    private Map<GLO, Long> gloIDMap = new HashMap<GLO, Long>();
    private Map<GLO, ACCESS_TYPE> gloAccessMap =
	new HashMap<GLO, ACCESS_TYPE>();
    private List<TimerRecord> timerRecordQueue = 
	new ArrayList<TimerRecord>();
    private List<OpenSocketRecord> socketOpenQueue =
	new ArrayList<OpenSocketRecord>();
    private List<SocketSendRecord> socketSendQueue =
	new ArrayList<SocketSendRecord>();
    private List<Long> socketCloseQueue = new ArrayList<Long>();

    public SimTaskImpl(Simulation sim, ClassLoader loader,
	    ACCESS_TYPE access, long startObjectID,
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

    public void execute() {
	this.trans = simulation.getObjectStore().newTransaction(loader);	
	this.trans.start(); //tell trans its waking up to begin anew	
	GLO runobj = null;
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
	    processTimerEvents();
	    processOpenSocketRecords();
	    processRawSocketSends();
	    processSocketCloseRecords();
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
	    timerRecordQueue.clear();
	    taskLaunchList.clear();
	    socketCloseQueue.clear();
	    socketOpenQueue.clear();
	    socketSendQueue.clear();
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

    public GLOReference makeReference(GLO glo) throws InstantiationException {		
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

    public void addUserListener(GLOReference ref) {
	simulation.addUserListener(ref);
    }

    public GLOReference findGLO(String gloName) {
	long oid = trans.lookup(gloName);
	return (oid == DataSpace.INVALID_ID) ? null : makeReference(oid);
    }


    public void sendData(ChannelID cid, UserID[] to, ByteBuffer bs,
	    boolean reliable) {
	outputList.add(new OutputRecord(cid, to, bs, reliable));
    }

    public void addUserDataListener(UserID user, GLOReference ref) {
	simulation.addUserDataListener(user, ref);
    }

    public void addChannelListener(ChannelID cid, GLOReference ref) {
	simulation.addChannelListener(cid, ref);
    }

    public void addChannelDataListener(ChannelID cid, GLOReference ref) {
	simulation.addChannelDataListener(cid, ref);
    }

    /**
     * createGLO
     * 
     * @param simObject		the object to store
     * @param name		an optional name to map the object to.
     * 
     * @return A GLOReference
     */
    public GLOReference createGLO(GLO simObject, String name) {		
	GLOReferenceImpl ref = (GLOReferenceImpl)
	    makeReference(trans.create(simObject, name));
	registerGLOID(ref.objID,simObject,ACCESS_TYPE.GET);
	return ref;
    }

    /**
     * This method is called to create a GLO in the objectstore.
     *
     * @param simObject the GLO to insert into the objects store
     * 
     * @return A GLOReference that references the newly created GLO
     */
    public GLOReference createGLO(GLO simObject) {
	return createGLO(simObject, null);
    }

    public ChannelID openChannel(String string) {
	return simulation.openChannel(string);
    }

    public long registerTimerEvent(ACCESS_TYPE access, long delay,
	    boolean repeat, GLOReference ref) {

	long tid = simulation.getNextTimerID();
	TimerRecord rec = new TimerRecord(tid, access, delay, repeat,
	    ((GLOReferenceImpl)ref).objID);
	timerRecordQueue.add(rec);
	return tid; 
    }

    private void processTimerEvents() {
	for (TimerRecord rec : timerRecordQueue) {
	    simulation.registerTimerEvent(rec.tid, rec.access, rec.objID,
		rec.delay, rec.repeat);
	}
	timerRecordQueue.clear();

    }

    public void registerGLOID(long objID, GLO glo, ACCESS_TYPE access) {
	gloIDMap.put(glo,new Long(objID));	
	gloAccessMap.put(glo,access);
    }

    public long registerTimerEvent(long delay, boolean repeat,
	    GLOReference reference) {
	return registerTimerEvent(ACCESS_TYPE.GET,delay,repeat,reference);

    }

    public void queueTask(ACCESS_TYPE accessType, GLOReference target,
	    Method method, Object[] parameters) {		
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

    // used by scrub and copy
    class NoGLOObjectOutputStream extends ObjectOutputStream {

	protected NoGLOObjectOutputStream(ByteArrayOutputStream os)
		throws IOException, SecurityException {

	    super(os);	
	    enableReplaceObject(true);
	}		

	protected Object replaceObject(Object obj) throws IOException {
	    if (gloIDMap.containsKey(obj)) {
		throw new IOException("Attempt to serialize GLO! ("+obj+")");
	    }
	    return obj;
	}
    }

    private Object[] scrubAndCopy(Object[] parameters)
	    throws SecurityException, IOException, ClassNotFoundException {

	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	NoGLOObjectOutputStream oos = new NoGLOObjectOutputStream(baos);
	oos.writeObject(parameters);
	oos.close();

	ByteArrayInputStream bais =
	    new ByteArrayInputStream(baos.toByteArray());
	ObjectInputStream ois = new ObjectInputStream(bais);
	parameters = (Object[])ois.readObject();
	return parameters;
    }

    public void queueTask(GLOReference target, Method method, Object[] parameters) {		
	queueTask(ACCESS_TYPE.GET,target,method,parameters);

    }

    public void access_check(ACCESS_TYPE accessType, Object glo) {
	ACCESS_TYPE gloAcc = gloAccessMap.get(glo);
	if (gloAcc != accessType){
	    throw new AccessTypeViolationException("Expected "+accessType+
		    " check returned "+gloAcc);
	}
    }

    public long openSocket(ACCESS_TYPE access, GLOReference ref, String host, 
	    int port, boolean reliable) {
	long sid = simulation.getNextSocketID();		
	socketOpenQueue.add(
		new OpenSocketRecord(sid,access,((GLOReferenceImpl)ref).objID,
		    host,port,reliable));
	return sid;
    }

    private void processOpenSocketRecords(){
	for (OpenSocketRecord rec:socketOpenQueue){
	    simulation.openSocket(rec.socketID,rec.access,rec.objID,
		    rec.host,rec.port,rec.reliable);
	}
	socketOpenQueue.clear();
    }

    public void sendRawSocketData(long socketID, ByteBuffer data) {
	socketSendQueue .add(new SocketSendRecord(socketID, data));
    }

    private void processRawSocketSends(){
	for(SocketSendRecord rec: socketSendQueue){
	    simulation.sendRawSocketData(rec.socketID,rec.data);
	}
	socketSendQueue.clear();
    }

    public void closeSocket(long socketID) {
	socketCloseQueue.add(new Long(socketID));
    }

    private void processSocketCloseRecords(){
	for(Long l : socketCloseQueue){
	    simulation.closeSocket(l);
	}
    }

    /**
     * Joins the specified user to the Channel referenced by the
     * given ChannelID.
     * 
     * @param user				the user
     * @param id				the ChannelID
     */
    public void join(UserID user, ChannelID id) {
	simulation.join(user, id);
    }

    /**
     * Removes the specified user from the Channel referenced by the
     * given ChannelID.
     * 
     * @param user				the user
     * @param id				the ChannelID
     */
    public void leave(UserID user, ChannelID id) {
	simulation.leave(user, id);
    }

    /**
     * Locks the given channel based on shouldLock.  Users cannot join/leave
     * locked channels except by way of the Router.
     * 
     * @param cid		the channel ID
     * @param shouldLock	if true, lock the channel, otherwise unlock it.
     */
    public void lock(ChannelID cid, boolean shouldLock) {
	simulation.lock(cid, shouldLock);
    }

    /**
     * Closes the local view of the channel mapped to ChannelID.
     * Any remaining users will be notified as the channel is closing.
     * 
     * @param id		the ID of the channel to close.
     */
    public void closeChannel(ChannelID id) {
	simulation.closeChannel(id);
    }
}
