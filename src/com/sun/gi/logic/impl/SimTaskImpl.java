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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

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
import com.sun.gi.utils.classes.CLObjectInputStream;

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
public class SimTaskImpl extends SimTask {

    private Transaction   trans;
    private ACCESS_TYPE   accessType;
    private GLOReference  startObject;
    private Method        startMethod;
    private Object[]      startArgs;

    private Simulation    simulation;
    private ClassLoader   loader;

    private Map<GLO, Long>         gloIDMap;
    private Map<GLO, ACCESS_TYPE>  gloAccessMap;

    private Queue<DeferredSimCommand> deferredCommands;

    public SimTaskImpl(Simulation sim, ClassLoader loader, ACCESS_TYPE access,
	    long startObjectID, Method startMethod, Object[] startArgs) {

	this.simulation = sim;
	this.startObject = this.makeReference(startObjectID);
	this.startMethod = startMethod;
	this.loader = loader;
	this.accessType = access;
	this.simulation = sim;
	Object newargs[] = new Object[startArgs.length];
	System.arraycopy(startArgs, 0, newargs, 0, startArgs.length);
	this.startArgs = newargs;

	gloIDMap = new HashMap<GLO, Long>();
	gloAccessMap = new HashMap<GLO, ACCESS_TYPE>();

	deferredCommands = new LinkedList<DeferredSimCommand>();
    }

    public void execute() {

	setAsCurrent();

	this.trans = simulation.getObjectStore().newTransaction(loader);
	this.trans.start(); // tell trans its waking up to begin anew

	GLO runobj = null;
	switch (accessType) {
	case GET:
	    runobj = startObject.get(this);
	    break;

	case PEEK:
	    runobj = startObject.peek(this);
	    break;

	case ATTEMPT:
	    runobj = startObject.attempt(this);
	    if (runobj == null) { // attempt failed
		trans.abort();
		return;
	    }
	    break;
	}

	try {
	    startMethod.invoke(runobj, startArgs);
	    trans.commit();
	    processDeferredCommands();
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
	    deferredCommands.clear();
	    gloIDMap.clear();
	    gloAccessMap.clear();
	    simulation.queueTask(this); // requeue for later execution
	}
    }

    public GLOReference makeReference(long id) {
	return new GLOReferenceImpl(id);
    }

    public GLOReference makeReference(GLO glo) throws InstantiationException {
	Long idl = gloIDMap.get(glo);
	if (idl == null) {
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
	deferredCommands.add(
	    new DeferredUserListener(((GLOReferenceImpl) ref).objID));
    }

    public GLOReference findGLO(String gloName) {
	long oid = trans.lookup(gloName);
	return (oid == DataSpace.INVALID_ID) ? null : makeReference(oid);
    }

    public void sendData(ChannelID cid, UserID to, ByteBuffer data,
	    boolean reliable) {
	deferredCommands.add(new DeferredUnicast(cid, to, data, reliable));
    }

    public void sendData(ChannelID cid, UserID[] to, ByteBuffer data,
	    boolean reliable) {
	deferredCommands.add(new DeferredMulticast(cid, to, data, reliable));
    }

    public void broadcastData(ChannelID cid, ByteBuffer data,
	    boolean reliable) {
	deferredCommands.add(new DeferredBroadcast(cid, data, reliable));
    }

    public void addUserDataListener(UserID user, GLOReference ref) {
	deferredCommands.add(new DeferredUserDataListener(user, ref));
    }

    /**
     * createGLO
     *
     * @param templateObj a template-object to store.  Note that
     *                    the templateObj should not be used after
     *                    it is passed to createGLO; instead, call
     *                    get() on the reference that is returned.
     *
     * @param name        an optional name to map the object to.
     *
     * @return A GLOReference
     */
    public GLOReference createGLO(GLO templateObj, String name) {
	long objid = trans.create(templateObj, name);
	if (objid == ObjectStore.INVALID_ID) {
	    return null;
	}
	GLOReferenceImpl ref = (GLOReferenceImpl) makeReference(objid);
	registerGLOID(ref.objID, templateObj, ACCESS_TYPE.GET);
	return ref;
    }

    /**
     * This method is called to create a GLO in the objectstore.
     *
     * @param templateObj a template-object to store.  Note that
     *                    the templateObj should not be used after
     *                    it is passed to createGLO; instead, call
     *                    get() on the reference that is returned.
     *
     * @return A GLOReference that references the newly created GLO
     */
    public GLOReference createGLO(GLO templateObj) {
	return createGLO(templateObj, null);
    }

    public void destroyGLO(GLOReference ref) {
	ref.delete(this);
    }

    public long registerTimerEvent(ACCESS_TYPE access, long delay,
	    boolean repeat, GLOReference ref) {

	long tid = simulation.getNextTimerID();
	DeferredNewTimer rec = new DeferredNewTimer(tid, access, delay,
	    repeat, ((GLOReferenceImpl) ref).objID);
	deferredCommands.add(rec);
	return tid;
    }

    public void registerGLOID(long objID, GLO glo, ACCESS_TYPE access) {
	gloIDMap.put(glo, new Long(objID));
	gloAccessMap.put(glo, access);
    }

    public long registerTimerEvent(long delay, boolean repeat,
	    GLOReference reference) {
	return registerTimerEvent(ACCESS_TYPE.GET, delay, repeat, reference);
    }

    public void queueTask(ACCESS_TYPE accessType, GLOReference target,
	    Method method, Object[] parameters) {
	try {
	    deferredCommands.add(
		new DeferredNewTask(
		    simulation.newTask(accessType, target, method,
				       scrubAndCopy(parameters))));
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
		throw new IOException(
		    "Attempt to serialize GLO! (" + obj + ")");
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
	CLObjectInputStream ois = new CLObjectInputStream(bais, loader);
	parameters = (Object[]) ois.readObject();
	return parameters;
    }

    public void queueTask(GLOReference target, Method method,
	    Object[] parameters) {
	queueTask(ACCESS_TYPE.GET, target, method, parameters);
    }

    public void access_check(ACCESS_TYPE accessType, GLO glo) {
	ACCESS_TYPE gloAcc = gloAccessMap.get(glo);
	if (gloAcc != accessType) {
	    throw new AccessTypeViolationException("Expected " + accessType
		    + " check returned " + gloAcc);
	}
    }

    public long openSocket(ACCESS_TYPE access, GLOReference ref, String host,
	    int port, boolean reliable) {
	long sid = simulation.getNextSocketID();
	deferredCommands.add(new DeferredSocketOpen(sid, access,
		    ((GLOReferenceImpl) ref).objID, host, port, reliable));
	return sid;
    }

    public void sendRawSocketData(long socketID, ByteBuffer data) {
	deferredCommands.add(new DeferredSocketSend(socketID, data));
    }

    public void closeSocket(long socketID) {
	deferredCommands.add(new DeferredSocketClose(socketID));
    }

    public ChannelID openChannel(String string) {
	// @@ It should be ok to open the channel immediately -jm
	return simulation.openChannel(string);
    }

    /**
     * Joins the specified user to the Channel referenced by the given
     * ChannelID.
     *
     * @param user
     *            the user
     * @param id
     *            the ChannelID
     */
    public void join(UserID user, ChannelID cid) {
	deferredCommands.add(new DeferredChannelJoin(user, cid));
    }

    /**
     * Removes the specified user from the Channel referenced by the given
     * ChannelID.
     *
     * @param user
     *            the user
     * @param id
     *            the ChannelID
     */
    public void leave(UserID user, ChannelID cid) {
	deferredCommands.add(new DeferredChannelLeave(user, cid));
    }

    /**
     * Locks the given channel based on shouldLock. Users cannot join/leave
     * locked channels except by way of the Router.
     *
     * @param cid
     *            the channel ID
     * @param shouldLock
     *            if true, lock the channel, otherwise unlock it.
     */
    public void lock(ChannelID cid, boolean shouldLock) {
	deferredCommands.add(new DeferredChannelLock(cid, shouldLock));
    }

    /**
     * Closes the local view of the channel mapped to ChannelID. Any remaining
     * users will be notified as the channel is closing.
     *
     * @param id
     *            the ID of the channel to close.
     */
    public void closeChannel(ChannelID cid) {
	deferredCommands.add(new DeferredChannelClose(cid));
    }

    public void setEvesdroppingEnabled(UserID uid, ChannelID cid,
	    boolean setting) {
	deferredCommands.add(new DeferredChannelSnoop(uid, cid, setting));
    }

    private void processDeferredCommands() {
	while (! deferredCommands.isEmpty()) {
	    DeferredSimCommand cmd = deferredCommands.remove();
	    //System.err.println("exec deffered " + cmd.getClass());
	    cmd.execute(simulation);
	}
    }
}


interface DeferredSimCommand {
    public void execute(Simulation sim);
}

class DeferredUnicast implements DeferredSimCommand {
    UserID target;
    UserID uid;
    ByteBuffer data;
    boolean reliable;
    ChannelID channel;

    public DeferredUnicast(ChannelID cid, UserID to, ByteBuffer buff,
	    boolean reliableFlag) {
	channel = cid;
	data = ByteBuffer.allocate(buff.capacity());
	buff.flip(); // flip for read
	data.put(buff);
	reliable = reliableFlag;
	target = to;
    }

    public void execute(Simulation sim) {
	sim.sendUnicastData(channel, target, data, reliable);
    }
}

class DeferredMulticast implements DeferredSimCommand {
    UserID[] targets;
    UserID uid;
    ByteBuffer data;
    boolean reliable;
    ChannelID channel;

    public DeferredMulticast(ChannelID cid, UserID[] to, ByteBuffer buff,
	    boolean reliableFlag) {
	channel = cid;
	data = ByteBuffer.allocate(buff.capacity());
	buff.flip(); // flip for read
	data.put(buff);
	reliable = reliableFlag;
	targets = new UserID[to.length];
	System.arraycopy(to, 0, targets, 0, to.length);
    }

    public void execute(Simulation sim) {
	sim.sendMulticastData(channel, targets, data, reliable);
    }
}

class DeferredBroadcast implements DeferredSimCommand {
    UserID uid;
    ByteBuffer data;
    boolean reliable;
    ChannelID channel;

    public DeferredBroadcast(ChannelID cid, ByteBuffer buff,
	    boolean reliableFlag) {
	channel = cid;
	data = ByteBuffer.allocate(buff.capacity());
	buff.flip(); // flip for read
	data.put(buff);
	reliable = reliableFlag;
    }

    public void execute(Simulation sim) {
	sim.sendBroadcastData(channel, data, reliable);
    }
}

class DeferredSocketOpen implements DeferredSimCommand {
    private final long         socketID;
    private final ACCESS_TYPE  access;
    private final long         objID;
    private final String       host;
    private final int          port;
    private final boolean      reliable;

    public DeferredSocketOpen(long sid, ACCESS_TYPE access, long objID,
	    String host, int port, boolean reliable) {
	this.socketID = sid;
	this.access = access;
	this.objID = objID;
	this.host = host;
	this.port = port;
	this.reliable = reliable;
    }

    public void execute(Simulation sim) {
	sim.openSocket(socketID, access, objID, host, port, reliable);
    }
}

class DeferredSocketSend implements DeferredSimCommand {
    private final long        socketID;
    private final ByteBuffer  data;

    public DeferredSocketSend(long socketID, ByteBuffer buff) {
	this.socketID = socketID;
	data = ByteBuffer.allocate(buff.capacity());
	buff.flip(); // flip for read
	data.put(buff);
    }

    public void execute(Simulation sim) {
	sim.sendRawSocketData(socketID, data);
    }
}

class DeferredNewTask implements DeferredSimCommand {
    private final SimTask  task;

    public DeferredNewTask(SimTask t) {
	this.task = t;
    }

    public void execute(Simulation sim) {
	sim.queueTask(task);
    }
}

class DeferredNewTimer implements DeferredSimCommand {
    private final long         tid;
    private final ACCESS_TYPE  access;
    private final long         delay;
    private final boolean      repeat;
    private final long         objID;

    public DeferredNewTimer(long tid, ACCESS_TYPE access,
	    long delay, boolean repeat, long objID) {

	this.tid = tid;
	this.access = access;
	this.delay = delay;
	this.repeat = repeat;
	this.objID = objID;
    }

    public void execute(Simulation sim) {
	sim.registerTimerEvent(tid, access, objID, delay, repeat);
    }
}

class DeferredUserDataListener implements DeferredSimCommand {
    private final UserID  uid;
    private final long    objID;

    public DeferredUserDataListener(UserID uid, GLOReference glo) {
	this.uid = uid; // XXX does this need to be cloned?
	this.objID = ((GLOReferenceImpl) glo).objID;
    }

    public void execute(Simulation sim) {
	sim.addUserDataListener(uid, new GLOReferenceImpl(objID));
    }
}

class DeferredSocketClose implements DeferredSimCommand {
    private final long  socketID;

    public DeferredSocketClose(long socketID) {
	this.socketID = socketID;
    }

    public void execute(Simulation sim) {
	sim.closeSocket(socketID);
    }
}

class DeferredUserListener implements DeferredSimCommand {
    private final long  gloID;

    public DeferredUserListener(long gloID) {
	this.gloID = gloID;
    }

    public void execute(Simulation sim) {
	sim.addUserListener(new GLOReferenceImpl(gloID));
    }
}

class DeferredChannelJoin implements DeferredSimCommand {
    private final UserID     uid;
    private final ChannelID  cid;

    public DeferredChannelJoin(UserID userID, ChannelID chanID) {
	this.uid = userID;
	this.cid = chanID;
    }

    public void execute(Simulation sim) {
	sim.join(uid, cid);
    }
}

class DeferredChannelLeave implements DeferredSimCommand {
    private final UserID     uid;
    private final ChannelID  cid;

    public DeferredChannelLeave(UserID userID, ChannelID chanID) {
	this.uid = userID;
	this.cid = chanID;
    }

    public void execute(Simulation sim) {
	sim.leave(uid, cid);
    }
}

class DeferredChannelLock implements DeferredSimCommand {
    private final ChannelID  cid;
    private final boolean    lock;

    public DeferredChannelLock(ChannelID chanID, boolean shouldLock) {
	this.cid = chanID;
	this.lock = shouldLock;
    }

    public void execute(Simulation sim) {
	sim.lock(cid, lock);
    }
}

class DeferredChannelClose implements DeferredSimCommand {
    private final ChannelID  cid;

    public DeferredChannelClose(ChannelID chanID) {
	this.cid = chanID;
    }

    public void execute(Simulation sim) {
	sim.closeChannel(cid);
    }
}

class DeferredChannelSnoop implements DeferredSimCommand {
    private final UserID     uid;
    private final ChannelID  cid;
    private final boolean    enable;

    public DeferredChannelSnoop(UserID userID, ChannelID chanID,
	    boolean setting) {
	this.uid = userID;
	this.cid = chanID;
	this.enable = setting;
    }

    public void execute(Simulation sim) {
	sim.enableEvesdropping(uid, cid, enable);
    }
}
