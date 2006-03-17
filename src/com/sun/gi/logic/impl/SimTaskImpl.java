/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.logic.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.AccessTypeViolationException;
import com.sun.gi.logic.ExecutionOutsideOfTaskException;
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

public class SimTaskImpl extends SimTask {

    private static Logger log = Logger.getLogger("com.sun.gi.logic");
    private static final boolean DEBUG = true;

    private int executionCount;

    private final Transaction trans;
    private final ACCESS_TYPE accessType;
    private final GLOReference startObject;
    private final Method startMethod;
    private final Object[] startArgs;
    private final Simulation simulation;
    private final ClassLoader loader;
    private final UserID uid;

    private final Map<GLO, Long> gloIDMap;
    private final Map<GLO, ACCESS_TYPE> gloAccessMap;
    private final Queue<DeferredSimCommand> deferredCommands;

    public SimTaskImpl(Simulation sim, ClassLoader loader, ACCESS_TYPE access,
            long startObjectID, Method startMethod, Object[] startArgs,
	    UserID uid)
    {
        this.simulation = sim;
        this.loader = loader;
        this.accessType = access;
        this.startObject = this.makeReference(startObjectID);
        this.startMethod = startMethod;
        this.startArgs = (Object[]) startArgs.clone();
	this.uid = uid;

	this.executionCount = 0;
	this.trans = simulation.getObjectStore().newTransaction(loader);

        this.gloIDMap = new HashMap<GLO, Long>();
        this.gloAccessMap = new HashMap<GLO, ACCESS_TYPE>();

        this.deferredCommands = new LinkedList<DeferredSimCommand>();
    }

    protected void checkTaskIsCurrent() {
	if (DEBUG && (this != SimTask.getCurrent())) {
	    throw new ExecutionOutsideOfTaskException();
	}
    }

    public void execute() {

	executionCount++;

        setAsCurrent();

	if (DEBUG && executionCount > 1) {
	    log.finest("Task for " + uid +
		" reusing txn " + 
		((com.sun.gi.objectstore.tso.TSOTransaction) trans).getUUID() +
		" for try #" + executionCount);
	}

        this.trans.start(); // tell trans its waking up to begin anew

        GLO runobj = null;
        try {
            switch (accessType) {
                case GET:
                    runobj = startObject.get(this);
                    break;

                case PEEK:
                    runobj = startObject.peek(this);
                    break;

                case ATTEMPT:
                    runobj = startObject.attempt(this);
                    if (runobj == null) {
                        // attempt failed
                        trans.abort();

			// DJE: we're done with this.
			simulation.taskDone(this);
                        return;
                    }
                    break;
            }

            startMethod.invoke(runobj, startArgs);
            trans.commit();
            processDeferredCommands();

	    // DJE If we reach this, then the task is finished.

        } catch (DeadlockException de) {
	    log.throwing(getClass().getName(), "run", de);
	    requeueAfterDeadlock();

	    // DJE: NOT finished or dead

        } catch (InvocationTargetException ex) {
	    Throwable realException = ex.getCause();
	    log.throwing(getClass().getName(), "run", realException);

	    if (realException instanceof DeadlockException) {
		requeueAfterDeadlock();

		// DJE: NOT finished or dead

	    } else {
		realException.printStackTrace();
		trans.abort();

		// DJE: If we reach this, then the task is DEAD.
	    }
        } catch (IllegalAccessException ex) {
            ex.printStackTrace();
	    log.throwing(getClass().getName(), "run", ex);
            trans.abort();

	    // DJE: If we reach this, then the task is DEAD.
        } catch (RuntimeException ex) {
	    if (log.isLoggable(Level.WARNING)) {
		log.warning("Exception on task execution:" +
		    "\n  target: " + (runobj == null
			? "<null> "
			: runobj.getClass().getName()) +
		    (startMethod == null ? "<null method>"
			: ("\n  method: " + startMethod.getName() +
			   "\n  declared on: " +
			   startMethod.getDeclaringClass().getName())));
	    }
            ex.printStackTrace();
	    log.throwing(getClass().getName(), "run", ex);
            trans.abort();

	    // DJE: If we reach this, then the task is DEAD.
        }

	simulation.taskDone(this);
    }

    protected void requeueAfterDeadlock() {
	log.warning("Requeue after deadlock txn " +
		((com.sun.gi.objectstore.tso.TSOTransaction)
		    trans).getUUID());

	// the transaction has already been aborted
	deferredCommands.clear();
	gloIDMap.clear();
	gloAccessMap.clear();

	// XXX sleep a bit to give the other guy
	// a chance to run.  Sleeping is a bit ugly; we
	// should either find a different mechanism or
	// tune this carefully. -jm
	// DJE: pruning back the sleep time (from 500 to 50ms)
	try {
	    Thread.sleep(50);
	} catch (InterruptedException ie) { }

	// requeue for later execution
	simulation.requeueTask(this);
    }

    public GLOReference<? extends GLO> makeReference(long id) {
        return new GLOReferenceImpl<GLO>(this, id);
    }

    public <T extends GLO> GLOReference<T> lookupReferenceFor(T glo)
    {
	checkTaskIsCurrent();
        Long oid = gloIDMap.get(glo);
        if (oid == null) {
            log.warning("No existing GLOReference found for GLO " + glo);
	    return null;
        }
        return (GLOReference<T>) makeReference(oid.longValue());
    }

    public Transaction getTransaction() {
        return trans;
    }

    public long getAppID() {
        return trans.getCurrentAppID();
    }

    public void addUserListener(GLOReference ref) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredUserListener(
                ((GLOReferenceImpl) ref).getObjID()));
    }

    public GLOReference findGLO(String gloName) {
	checkTaskIsCurrent();
        long oid = trans.lookup(gloName);
        return (oid == DataSpace.INVALID_ID) ? null : makeReference(oid);
    }

    public void sendData(ChannelID cid, UserID to, ByteBuffer data,
            boolean reliable) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredUnicast(cid, to, data, reliable));
    }

    public void sendData(ChannelID cid, UserID[] to, ByteBuffer data,
            boolean reliable) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredMulticast(cid, to, data, reliable));
    }

    public void broadcastData(ChannelID cid, ByteBuffer data, boolean reliable)
    {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredBroadcast(cid, data, reliable));
    }

    public void addUserDataListener(UserID user, GLOReference ref) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredUserDataListener(user, ref));
    }

    /**
     * createGLO
     * 
     * @param templateObj a template-object to store. Note that the
     * templateObj should not be used after it is passed to createGLO;
     * instead, call get() on the reference that is returned.
     * 
     * @param name an optional name to map the object to.
     * 
     * @return A GLOReference
     */
    public <T extends GLO> GLOReference<T> createGLO(T templateObj,
                String name)
    {
	checkTaskIsCurrent();
        long oid = trans.create(templateObj, name);
        if (oid == ObjectStore.INVALID_ID) {
            return null;
        }
        GLOReferenceImpl ref = (GLOReferenceImpl) makeReference(oid);
        registerGLOID(ref.getObjID(), templateObj, ACCESS_TYPE.GET);
        return (GLOReference<T>) ref;
    }

    /**
     * This method is called to create a GLO in the objectstore.
     * 
     * @param templateObj a template-object to store. Note that the
     * templateObj should not be used after it is passed to createGLO;
     * instead, call get() on the reference that is returned.
     * 
     * @return A GLOReference that references the newly created GLO
     */
    public <T extends GLO> GLOReference<T> createGLO(T templateObj) {
	checkTaskIsCurrent();
        return createGLO(templateObj, null);
    }

    public void destroyGLO(GLOReference<? extends GLO> ref) {
	checkTaskIsCurrent();
        ref.delete(this);
    }

    public long registerTimerEvent(ACCESS_TYPE access, long delay,
            boolean repeat, GLOReference ref) {
	checkTaskIsCurrent();

        long tid = simulation.getNextTimerID();
        DeferredNewTimer rec = new DeferredNewTimer(tid, access, delay, repeat,
                ((GLOReferenceImpl) ref).getObjID());
        deferredCommands.add(rec);
        return tid;
    }

    public void registerGLOID(long objID, GLO glo, ACCESS_TYPE access) {
        gloIDMap.put(glo, new Long(objID));
        gloAccessMap.put(glo, access);
    }

    public long registerTimerEvent(long delay, boolean repeat,
            GLOReference reference) {
	checkTaskIsCurrent();
        return registerTimerEvent(ACCESS_TYPE.GET, delay, repeat, reference);
    }

    public void queueTask(ACCESS_TYPE theAccessType,
            GLOReference<? extends GLO> target, Method method,
            Object[] parameters) {
	checkTaskIsCurrent();
        try {
            deferredCommands.add(new DeferredNewTask(simulation.newTask(
                    theAccessType, target, method, scrubAndCopy(parameters),
		    uid)));
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
                throw new IOException("Attempt to serialize GLO! (" + obj + ")");
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

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        CLObjectInputStream ois = new CLObjectInputStream(bais, loader);
        parameters = (Object[]) ois.readObject();
        return parameters;
    }

    public void queueTask(GLOReference<? extends GLO> target, Method method,
            Object[] parameters) {
	checkTaskIsCurrent();
        queueTask(ACCESS_TYPE.GET, target, method, parameters);
    }

    public void access_check(ACCESS_TYPE theAccessType, GLO glo) {
	checkTaskIsCurrent();
        ACCESS_TYPE gloAcc = gloAccessMap.get(glo);
        if (gloAcc != theAccessType) {
            throw new AccessTypeViolationException("Expected " + theAccessType
                    + " check returned " + gloAcc);
        }
    }

    public long openSocket(ACCESS_TYPE access, GLOReference ref, String host,
            int port, boolean reliable) {
	checkTaskIsCurrent();
        long sid = simulation.getNextSocketID();
        deferredCommands.add(new DeferredSocketOpen(sid, access,
                ((GLOReferenceImpl) ref).getObjID(), host, port, reliable));
        return sid;
    }

    public void sendRawSocketData(long socketID, ByteBuffer data) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredSocketSend(socketID, data));
    }

    public void closeSocket(long socketID) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredSocketClose(socketID));
    }

    public ChannelID openChannel(String string) {
	checkTaskIsCurrent();
        // @@ It should be ok to open the channel immediately -jm
        return simulation.openChannel(string);
    }

    /**
     * Joins the specified user to the Channel referenced by the given
     * ChannelID.
     * 
     * @param uid the user
     * @param cid the ChannelID
     */
    public void join(UserID uid, ChannelID cid) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredChannelJoin(uid, cid));
    }

    /**
     * Removes the specified user from the Channel referenced by the
     * given ChannelID.
     * 
     * @param uid the user
     * @param cid the ChannelID
     */
    public void leave(UserID uid, ChannelID cid) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredChannelLeave(uid, cid));
    }

    /**
     * Locks the given channel based on shouldLock. Users cannot
     * join/leave locked channels except by way of the Router.
     * 
     * @param cid the channel ID
     * @param shouldLock if true, lock the channel, otherwise unlock it.
     */
    public void lock(ChannelID cid, boolean shouldLock) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredChannelLock(cid, shouldLock));
    }

    /**
     * Closes the local view of the channel mapped to ChannelID. Any
     * remaining users will be notified as the channel is closing.
     * 
     * @param cid the ID of the channel to close.
     */
    public void closeChannel(ChannelID cid) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredChannelClose(cid));
    }

    public void setEvesdroppingEnabled(UserID uid, ChannelID cid,
            boolean setting) {
	checkTaskIsCurrent();
        deferredCommands.add(new DeferredChannelSnoop(uid, cid, setting));
    }
    
    public String getAppName() {
	return simulation.getAppName();
    }

    private void processDeferredCommands() {
        while (!deferredCommands.isEmpty()) {
            DeferredSimCommand cmd = deferredCommands.remove();
            // System.err.println("exec deffered " + cmd.getClass());
            cmd.execute(simulation);
        }
    }

    /**
     * {@inheritDoc}
     */
    public UserID getUserID() {
	return uid;
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
    private final long socketID;

    private final ACCESS_TYPE access;

    private final long objID;

    private final String host;

    private final int port;

    private final boolean reliable;

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
    private final long socketID;

    private final ByteBuffer data;

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
    private final SimTask task;

    public DeferredNewTask(SimTask t) {
        this.task = t;
    }

    public void execute(Simulation sim) {
        sim.queueTask(task);
    }
}

class DeferredNewTimer implements DeferredSimCommand {
    private final long tid;

    private final ACCESS_TYPE access;

    private final long delay;

    private final boolean repeat;

    private final long objID;

    public DeferredNewTimer(long tid, ACCESS_TYPE access, long delay,
            boolean repeat, long objID) {

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
    private final UserID uid;

    private final long objID;

    public DeferredUserDataListener(UserID uid, GLOReference glo) {
        this.uid = uid; // XXX does this need to be cloned?
        this.objID = ((GLOReferenceImpl) glo).getObjID();
    }

    public void execute(Simulation sim) {
        sim.addUserDataListener(uid, new GLOReferenceImpl(objID));
    }
}

class DeferredSocketClose implements DeferredSimCommand {
    private final long socketID;

    public DeferredSocketClose(long socketID) {
        this.socketID = socketID;
    }

    public void execute(Simulation sim) {
        sim.closeSocket(socketID);
    }
}

class DeferredUserListener implements DeferredSimCommand {
    private final long gloID;

    public DeferredUserListener(long gloID) {
        this.gloID = gloID;
    }

    public void execute(Simulation sim) {
        sim.addUserListener(new GLOReferenceImpl(gloID));
    }
}

class DeferredChannelJoin implements DeferredSimCommand {
    private final UserID uid;

    private final ChannelID cid;

    public DeferredChannelJoin(UserID userID, ChannelID chanID) {
        this.uid = userID;
        this.cid = chanID;
    }

    public void execute(Simulation sim) {
        sim.join(uid, cid);
    }
}

class DeferredChannelLeave implements DeferredSimCommand {
    private final UserID uid;

    private final ChannelID cid;

    public DeferredChannelLeave(UserID userID, ChannelID chanID) {
        this.uid = userID;
        this.cid = chanID;
    }

    public void execute(Simulation sim) {
        sim.leave(uid, cid);
    }
}

class DeferredChannelLock implements DeferredSimCommand {
    private final ChannelID cid;

    private final boolean lock;

    public DeferredChannelLock(ChannelID chanID, boolean shouldLock) {
        this.cid = chanID;
        this.lock = shouldLock;
    }

    public void execute(Simulation sim) {
        sim.lock(cid, lock);
    }
}

class DeferredChannelClose implements DeferredSimCommand {
    private final ChannelID cid;

    public DeferredChannelClose(ChannelID chanID) {
        this.cid = chanID;
    }

    public void execute(Simulation sim) {
        sim.closeChannel(cid);
    }
}

class DeferredChannelSnoop implements DeferredSimCommand {
    private final UserID uid;

    private final ChannelID cid;

    private final boolean enable;

    public DeferredChannelSnoop(UserID userID, ChannelID chanID, boolean setting) {
        this.uid = userID;
        this.cid = chanID;
        this.enable = setting;
    }

    public void execute(Simulation sim) {
        sim.enableEvesdropping(uid, cid, enable);
    }
}
