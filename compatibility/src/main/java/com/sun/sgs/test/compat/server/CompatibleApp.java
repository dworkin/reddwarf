package com.sun.sgs.test.compat.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ManagerNotFoundException;
import com.sun.sgs.app.MessageRejectedException;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TaskRejectedException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.app.util.ScalableHashMap;
import com.sun.sgs.app.util.ScalableHashSet;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * An application to use for checking compatibility across releases.
 */
public class CompatibleApp implements AppListener, Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** How long to wait in between running tasks. */
    private static final long DELAY = 2000;

    /** The name of the persistent channel to create. */
    private static final String channelName = "Channel1";

    /** The channel manager. */
    private static final ChannelManager channelManager =
	AppContext.getChannelManager();

    /** The data manager. */
    private static final DataManager dataManager = AppContext.getDataManager();

    /** The task manager. */
    private static final TaskManager taskManager = AppContext.getTaskManager();

    /** Creates an instance of this class. */
    public CompatibleApp() { }

    /** Converts a byte buffer into a string using UTF-8 encoding. */
    static String bufferToString(ByteBuffer buffer) {
	byte[] bytes = new byte[buffer.remaining()];
	buffer.get(bytes);
	try {
	    return new String(bytes, "UTF-8");
	} catch (UnsupportedEncodingException e) {
	    throw new AssertionError(e);
	}
    }

    /** Converts a string into a byte buffer using UTF-8 encoding. */
    static ByteBuffer stringToBuffer(String string) {
	try {
	    return ByteBuffer.wrap(string.getBytes("UTF-8"));
	} catch (UnsupportedEncodingException e) {
	    throw new AssertionError(e);
	}
    }

    /* -- Implement AppListener -- */

    /** Returns client session listener. */
    public ClientSessionListener loggedIn(ClientSession session) {
	return new MyClientSessionListener(session);
    }

    /** Creates persistent tasks and channel. */
    public void initialize(Properties props) {
	taskManager.schedulePeriodicTask(new PeriodicTask(), 0, DELAY);
	taskManager.scheduleTask(new ManagedTask());
	taskManager.scheduleTask(new NonManagedTask());
	AppContext.getChannelManager().createChannel(
	    channelName, new MyChannelListener(), Delivery.RELIABLE);
	CheckPersistentScalableHashMap.initialize();
    }

    /* -- Utility classes -- */

    /**
     * An abstract base class for creating tasks the perform checks and notify
     * the client when the checks pass.
     */
    abstract static class CheckTask
	implements ManagedObject, Serializable, Task
    {
	private static final long serialVersionUID = 1;
	private static final String counterBinding = "checkTaskCounter";
	private static final List<CheckTask> checks =
	    new ArrayList<CheckTask>();
	static final String requestChecks = "Run checks";
	static final String checksCompleted = "Checks completed";
	private final int index;
	private ManagedReference<ClientSession> session;
	private int numChecks;

	/**
	 * Creates an instance of this class, and adds it to the list of tasks
	 * to be run.
	 */
	CheckTask() {
	    checks.add(this);
	    this.index = checks.size();
	}

	/** Runs the check tasks. */
	static void runChecks(ClientSession session) {
	    try {
		ManagedCounter counter =
		    (ManagedCounter) dataManager.getBinding(counterBinding);
		counter.reset();
	    } catch (NameNotBoundException e) {
	    }
	    int numChecks = checks.size();
	    for (CheckTask task : checks) {
		task.setSession(session);
		task.setNumChecks(numChecks);
		taskManager.scheduleTask(task);
	    }
	}

	/** Returns the client session. */
	ClientSession getSession() {
	    if (session == null) {
		throw new IllegalStateException("Session must be set first");
	    }
	    return session.get();
	}
	
	/**
	 * Subclasses should call this method when their run method determines
	 * that the check has passed.
	 */
	void completed() {
	    getSession().send(
		stringToBuffer(
		    this + ": Completed " + index + " of " + checks.size()));
	    ManagedCounter counter;
	    try {
		counter =
		    (ManagedCounter) dataManager.getBinding(counterBinding);
	    } catch (NameNotBoundException e) {
		counter = new ManagedCounter();
		dataManager.setBinding(counterBinding, counter);
	    }
	    counter.incrementCount();
	    if (numChecks == counter.getCount()) {
		getSession().send(stringToBuffer("Checks completed"));
	    }
	}

	/** Stores the client session. */
	private void setSession(ClientSession session) {
	    dataManager.markForUpdate(this);
	    this.session = dataManager.createReference(session);
	}

	/** Stores the total number of checks. */
	private void setNumChecks(int numChecks) {
	    dataManager.markForUpdate(this);
	    this.numChecks = numChecks;
	}
    }

    /** The client session listener. */
    private static class MyClientSessionListener
	implements ClientSessionListener, ManagedObject, Serializable
    {
	private static final long serialVersionUID = 1;
	private final ManagedReference<ClientSession> session;
	MyClientSessionListener(ClientSession session) {
	    this.session = dataManager.createReference(session);
	    session.send(
		stringToBuffer("Message to session " + session.getName()));
	    channelManager.getChannel(channelName).join(session);
	}
	public void disconnected(boolean graceful) {
	    System.out.println("Session disconnected session:" +
			       session.get().getName() +
			       ", graceful:" + graceful);
	}
	public void receivedMessage(ByteBuffer message) {
	    String messageString = bufferToString(message);
	    System.out.println(
		"Received message from session " + session.get().getName() +
		": " + messageString);
	    if (messageString.equals("Run checks")) {
		CheckTask.runChecks(session.get());
	    }
	}
    }

    /** The channel listener. */
    private static class MyChannelListener
	implements ChannelListener, ManagedObject, Serializable
    {
	private static final long serialVersionUID = 1;
	MyChannelListener() { }
	public void receivedMessage(
	    Channel channel, ClientSession sender, ByteBuffer message)
	{
	    channel.send(sender, message);
	    System.out.println("Received message on channel " +
			       channel.getName() +
			       " from sender " + sender.getName() + ": " +
			       bufferToString(message));

	}
    }

    /** A simple managed object. */
    private static class Marker implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	Marker() { }
    }

    /** A managed object counter. */
    private static class ManagedCounter
	implements ManagedObject, Serializable, ManagedObjectRemoval
    {
	private static final long serialVersionUID = 1;
	private int count = 0;
	ManagedCounter() { }
	int getCount() {
	    return count;
	}
	void incrementCount() {
	    dataManager.markForUpdate(this);
	    count++;
	}
	void reset() {
	    dataManager.markForUpdate(this);
	    count = 0;
	}
	public void removingObject() { }
    }

    /* -- Checks and associated classes -- */

    /** A persistent periodic task. */
    private static class PeriodicTask extends ManagedCounter implements Task {
	private static final long serialVersionUID = 1;
	private static final String notifyBinding = "periodicTaskNotify";
	private static final String counterBinding = "periodicTaskCounter";
	PeriodicTask() { }
	static int getCurrentCount() {
	    try {
		ManagedCounter counter =
		    (ManagedCounter) dataManager.getBinding(counterBinding);
		return counter.getCount();
	    } catch (NameNotBoundException e) {
		return 0;
	    }
	}
	static void requestNotify() {
	    dataManager.setBinding(notifyBinding, new Marker());
	}
	public void run() {
	    boolean notify = false;
	    try {
		dataManager.getBinding(notifyBinding);
		notify = true;
	    } catch (NameNotBoundException e) {
	    }
	    if (notify) {
		dataManager.removeBinding(notifyBinding);
		incrementCount();
		dataManager.setBinding(counterBinding, this);
	    }
	}
    }

    private static class CheckPeriodicTask extends CheckTask {
	private static final long serialVersionUID = 1;
	private boolean started;
	private int count;
	CheckPeriodicTask() { }
	public void run() {
	    if (!started) {
		dataManager.markForUpdate(this);
		started = true;
		PeriodicTask.requestNotify();
		count = PeriodicTask.getCurrentCount();
	    } else {
		int currentCount = PeriodicTask.getCurrentCount();
		if (currentCount > count) {
		    completed();
		    return;
		}
	    }
	    taskManager.scheduleTask(this, DELAY);
	}
    }

    static {
	new CheckPeriodicTask();
    }

    /** A persistent non-managed task. */
    private static class NonManagedTask implements Serializable, Task {
	private static final long serialVersionUID = 1;
	private static final String notifyBinding = "nonManagedTaskNotify";
	private static final String counterBinding = "nonManagedTaskCounter";
	NonManagedTask() { }
	static int getCurrentCount() {
	    try {
		ManagedCounter counter =
		    (ManagedCounter) dataManager.getBinding(counterBinding);
		return counter.getCount();
	    } catch (NameNotBoundException e) {
		return 0;
	    }
	}
	static void requestNotify() {
	    dataManager.setBinding(notifyBinding, new Marker());
	}
	public void run() {
	    boolean notify = false;
	    try {
		dataManager.getBinding(notifyBinding);
		notify = true;
	    } catch (NameNotBoundException e) {
	    }
	    if (notify) {
		dataManager.removeBinding(notifyBinding);
		ManagedCounter counter;
		try {
		    counter = (ManagedCounter)
			dataManager.getBinding(counterBinding);
		} catch (NameNotBoundException e) {
		    counter = new ManagedCounter();
		    dataManager.setBinding(counterBinding, counter);
		}
		counter.incrementCount();
	    }
	    taskManager.scheduleTask(new NonManagedTask(), DELAY);
	}
    }

    private static class CheckNonManagedTask extends CheckTask {
	private static final long serialVersionUID = 1;
	private boolean started;
	private int count;
	CheckNonManagedTask() { }
	public void run() {
	    if (!started) {
		dataManager.markForUpdate(this);
		started = true;
		NonManagedTask.requestNotify();
		count = NonManagedTask.getCurrentCount();
	    } else {
		int currentCount = NonManagedTask.getCurrentCount();
		if (currentCount > count) {
		    completed();
		    return;
		}
	    }
	    taskManager.scheduleTask(this, DELAY);
	}
    }

    static {
	new CheckNonManagedTask();
    }

    /** A persistent managed class. */
    private static class ManagedTask extends ManagedCounter implements Task {
	private static final long serialVersionUID = 1;
	private static final String notifyBinding = "managedTaskNotify";
	private static final String counterBinding = "managedTaskCounter";
	ManagedTask() { }
	static int getCurrentCount() {
	    try {
		ManagedCounter counter =
		    (ManagedTask) dataManager.getBinding(counterBinding);
		return counter.getCount();
	    } catch (NameNotBoundException e) {
		return 0;
	    }
	}
	static void requestNotify() {
	    dataManager.setBinding(notifyBinding, new Marker());
	}
	public void run() {
	    boolean notify = false;
	    try {
		dataManager.getBinding(notifyBinding);
		notify = true;
	    } catch (NameNotBoundException e) {
	    }
	    if (notify) {
		dataManager.removeBinding(notifyBinding);
		incrementCount();
		dataManager.setBinding(counterBinding, this);
	    }
	    taskManager.scheduleTask(this, DELAY);
	}
    }

    private static class CheckManagedTask extends CheckTask {
	private static final long serialVersionUID = 1;
	private boolean started;
	private int count;
	CheckManagedTask() { }
	public void run() {
	    if (!started) {
		dataManager.markForUpdate(this);
		started = true;
		ManagedTask.requestNotify();
		count = ManagedTask.getCurrentCount();
	    } else {
		int currentCount = ManagedTask.getCurrentCount();
		if (currentCount > count) {
		    completed();
		    return;
		}
	    }
	    taskManager.scheduleTask(this, DELAY);
	}
    }

    static {
	new CheckManagedTask();
    }

    private static class CheckExistingChannelTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckExistingChannelTask() { }
	public void run() {
	    channelManager.getChannel(channelName).send(
		null, stringToBuffer("Message from server to channel"));
	    completed();
	}
    }

    static {
	new CheckExistingChannelTask();
    }

    /** Test persistent ScalableHashMap **/

    private static class CheckPersistentScalableHashMap extends CheckTask {
	private static final long serialVersionUID = 1;
	private static final String name = "scalableHashMap";
	CheckPersistentScalableHashMap() { }
	static void initialize() {
	    Map<Integer, String> map = new ScalableHashMap<Integer, String>();
	    for (int i = 0; i < 100; i++) {
		map.put(i, String.valueOf(i));
	    }
	    dataManager.setBinding(name, map);
	}
	public void run() {
	    @SuppressWarnings("unchecked")
	    Map<Integer, String> map =
		(Map<Integer, String>) dataManager.getBinding(name);
	    for (int i = 0; i < 100; i++) {
		String expected = String.valueOf(i);
		String value = map.get(i);
		if (!expected.equals(value)) {
		    throw new RuntimeException(
			"Found " + value + ", expected " + expected);
		}
	    }
	    completed();
	}
    }

    static {
	new CheckPersistentScalableHashMap();
    }

    /* -- API Checks -- */

    private static class CheckAppContextTask extends CheckTask {
	private static final long serialVersionUID = 1;	
	CheckAppContextTask() { }
	public void run() {
	    AppContext.getChannelManager();
	    AppContext.getDataManager();
	    AppContext.getManager(DataManager.class);
	    AppContext.getTaskManager();
	    completed();
	}
    }

    static {
	new CheckAppContextTask();
    }

    /* AppListener already checked */

    private static class CheckChannelTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckChannelTask() { }
	public void run() {
	    Channel channel = channelManager.createChannel(
		"some channel", null, Delivery.RELIABLE);
	    channel.getDeliveryRequirement();
	    channel.getName();
	    channel.getSessions();
	    channel.hasSessions();
	    channel.join(getSession());
	    channel.leave(getSession());
	    Set<ClientSession> sessions = new HashSet<ClientSession>();
	    sessions.add(getSession());
	    channel.join(sessions);
	    channel.leave(sessions);
	    channel.leaveAll();
	    channel.send(getSession(), stringToBuffer("hi"));
	    dataManager.removeObject(channel);
	    completed();
	}
    }

    static {
	new CheckChannelTask();
    }

    /* ChannelListener already checked */

    /* ChannelManager already checked */

    private static class CheckClientSessionTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckClientSessionTask() { }
	public void run() {
	    getSession().getName();
	    getSession().isConnected();
	    getSession().send(stringToBuffer("hi"));
	    completed();
	}
    }

    static {
	new CheckClientSessionTask();
    }

    /* ClientSessionListener already checked */

    private static class CheckDataManagerTask extends CheckTask {
	private static final long serialVersionUID = 1;	
	CheckDataManagerTask() { }
	public void run() {
	    ManagedCounter counter = new ManagedCounter();
	    dataManager.createReference(counter);
	    dataManager.setBinding("Binding1", counter);
	    dataManager.getBinding("Binding1");
	    dataManager.removeBinding("Binding1");
	    dataManager.nextBoundName(null);
	    dataManager.markForUpdate(counter);
	    dataManager.removeObject(counter);
	    completed();
	}
    }

    static {
	new CheckDataManagerTask();
    }

    private static class CheckDeliveryTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckDeliveryTask() { }
	public void run() {
	    Delivery[] d = new Delivery[] {
		Delivery.ORDERED_UNRELIABLE, Delivery.RELIABLE,
		Delivery.UNORDERED_RELIABLE, Delivery.UNRELIABLE
	    };
	    completed();
	}
    }

    static {
	new CheckDeliveryTask();
    }

    private static class CheckExceptionsTask extends CheckTask {
	private static final long serialVersionUID = 1;	
	CheckExceptionsTask() { }
	public void run() {
	    new ManagerNotFoundException("");
	    new ManagerNotFoundException("", new Error());
	    ResourceUnavailableException rue = new MessageRejectedException("");
	    new MessageRejectedException("", new Error());
	    new NameExistsException("");
	    new NameExistsException("", new Error());
	    new NameNotBoundException("");
	    new NameNotBoundException("", new Error());
	    ExceptionRetryStatus ers = new ObjectIOException("", false);
	    ers.shouldRetry();
	    new ObjectIOException("", new Error(), false);
	    new ObjectNotFoundException("");
	    new ObjectNotFoundException("", new Error());
	    ers = new ResourceUnavailableException("");
	    new ResourceUnavailableException("", new Error());
	    rue = new TaskRejectedException("");
	    new TaskRejectedException("", new Error());
	    ers = new TransactionAbortedException("");
	    new TransactionAbortedException("", new Error());
	    ers = new TransactionConflictException("");
	    new TransactionConflictException("", new Error());
	    new TransactionException("");
	    new TransactionException("", new Error());
	    new TransactionNotActiveException("");
	    new TransactionNotActiveException("", new Error());
	    ers = new TransactionTimeoutException("");
	    new TransactionTimeoutException("", new Error());
	    completed();
	}
    }

    static {
	new CheckExceptionsTask();
    }

    /* ManagedObject already checked */

    /* ManagedObjectRemoval already checked */

    private static class CheckManagedReferenceTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckManagedReferenceTask() { }
	public void run() {
	    DataManager dataManager = AppContext.getDataManager();
	    ManagedCounter counter = new ManagedCounter();
	    ManagedReference<ManagedCounter> reference =
		dataManager.createReference(counter);
	    reference.get();
	    reference.getForUpdate();
	    reference.getId();
	    completed();
	}
    }

    static {
	new CheckManagedReferenceTask();
    }

    private static class CheckPeriodicTaskHandleTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckPeriodicTaskHandleTask() { }
	public void run() {
	    taskManager.schedulePeriodicTask(
		new DummyTask(), 1000, 1000).cancel();
	    completed();
	}
	private static class DummyTask implements Serializable, Task {
	    private static final long serialVersionUID = 1;
	    DummyTask() { }
	    public void run() { }
	}
    }

    static {
	new CheckPeriodicTaskHandleTask();
    }

    private static class CheckScalableCollectionsTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckScalableCollectionsTask() { }
	public void run() {
	    new ScalableHashMap<String, String>();
	    new ScalableHashMap<String, String>(3);
	    new ScalableHashMap<String, String>(
		new HashMap<String, String>());
	    new ScalableHashSet<String>();
	    new ScalableHashSet<String>(new ArrayList<String>());
	    new ScalableHashSet<String>(3);
	    completed();
	}
    }

    static {
	new CheckScalableCollectionsTask();
    }

    private static class CheckSimpleSgsProtocolTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckSimpleSgsProtocolTask() { }
	public void run() {
	    byte[] b = new byte[] {
		SimpleSgsProtocol.CHANNEL_JOIN,
		SimpleSgsProtocol.CHANNEL_LEAVE,
		SimpleSgsProtocol.CHANNEL_MESSAGE,
		SimpleSgsProtocol.LOGIN_FAILURE,
		SimpleSgsProtocol.LOGIN_REDIRECT,
		SimpleSgsProtocol.LOGIN_REQUEST,
		SimpleSgsProtocol.LOGIN_SUCCESS,
		SimpleSgsProtocol.LOGOUT_REQUEST,
		SimpleSgsProtocol.LOGOUT_SUCCESS,
		SimpleSgsProtocol.RECONNECT_FAILURE,
		SimpleSgsProtocol.RECONNECT_REQUEST,
		SimpleSgsProtocol.RECONNECT_SUCCESS,
		SimpleSgsProtocol.SESSION_MESSAGE,
		SimpleSgsProtocol.VERSION
	    };
	    int[] i = new int[] {
		SimpleSgsProtocol.MAX_MESSAGE_LENGTH,
		SimpleSgsProtocol.MAX_PAYLOAD_LENGTH
	    };
	    completed();
	}
    }

    static {
	new CheckSimpleSgsProtocolTask();
    }

    /* Task already checked */

    /* TaskManager already checked */
}
