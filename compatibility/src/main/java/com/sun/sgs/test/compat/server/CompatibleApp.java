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
 * An application to use for checking compatibility across releases, both for
 * persistent data structures and APIs.
 */
public class CompatibleApp implements AppListener, Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** How many milliseconds to wait in between running tasks. */
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

    /** Objects to initialize. */
    private static final List<Initialize> initializers =
	new ArrayList<Initialize>();

    /** Creates an instance of this class. */
    public CompatibleApp() { }

    /* -- Implement AppListener -- */

    /** Returns the client session listener. */
    public ClientSessionListener loggedIn(ClientSession session) {
	return new MyClientSessionListener(session);
    }

    /** Runs initializers. */
    public void initialize(Properties props) {
	for (Initialize init : initializers) {
	    init.initialize();
	}
    }

    /* -- Implement ClientSessionListener -- */

    /**
     * The client session listener.  Responsible for calling {@link
     * CheckTask#runChecks CheckTask.runChecks} when it receives the proper
     * request from the client.
     */
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

    /* -- Implement ChannelListener -- */

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

    /* -- Persistent checks -- */

    /**
     * Checks that periodic tasks get run. <p>
     *
     * When the application first starts up, the {@code initialize} method
     * schedules an instance of {@link PeriodicTask} to run periodically. <p>
     *
     * When the client requests that checks be performed, the run method sets a
     * flag asking {@code PeriodicTask} to increment a counter, and then checks
     * that the counter has been incremented.
     */
    private static class CheckPeriodicTask extends CheckTask
	implements Initialize
    {
	private static final long serialVersionUID = 1;
	private boolean started;
	private int count;
	CheckPeriodicTask() {
	    initializers.add(this);
	}
	public void initialize() {
	    taskManager.schedulePeriodicTask(new PeriodicTask(), 0, DELAY);
	}
	boolean runInternal() {
	    if (!started) {
		dataManager.markForUpdate(this);
		started = true;
		PeriodicTask.requestNotify();
		count = PeriodicTask.getCurrentCount();
	    } else {
		int currentCount = PeriodicTask.getCurrentCount();
		if (currentCount > count) {
		    return true;
		}
	    }
	    taskManager.scheduleTask(this, DELAY);
	    return false;
	}
    }

    static {
	new CheckPeriodicTask();
    }

    /**
     * A persistent periodic task. <p>
     *
     * Each time this task is run, it checks to see if {@code
     * periodicTaskNotify} is bound.  If so, it clears the binding and
     * increments the counter bound to {@code periodicTaskCounter}.
     */
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

    /**
     * Checks that non-managed tasks get run. <p>
     *
     * When the application first starts up, the initialize method schedules an
     * instance of {@link NonManagedTask} to run.  That task reschedules itself
     * each time it runs. <p>
     *
     * When the client requests that checks be performed, the run method sets a
     * flag asking {@code NonManagedTask} to increment a counter, and then
     * checks that the counter has been incremented.
     */
    private static class CheckNonManagedTask extends CheckTask
	implements Initialize
    {
	private static final long serialVersionUID = 1;
	private boolean started;
	private int count;
	CheckNonManagedTask() {
	    initializers.add(this);
	}
	public void initialize() {
	    taskManager.scheduleTask(new NonManagedTask());
	}
	boolean runInternal() {
	    if (!started) {
		dataManager.markForUpdate(this);
		started = true;
		NonManagedTask.requestNotify();
		count = NonManagedTask.getCurrentCount();
	    } else {
		int currentCount = NonManagedTask.getCurrentCount();
		if (currentCount > count) {
		    return true;
		}
	    }
	    taskManager.scheduleTask(this, DELAY);
	    return false;
	}
    }

    static {
	new CheckNonManagedTask();
    }

    /**
     * A persistent non-managed task. <p>
     *
     * Each time this task is run, it checks to see if {@code
     * nonManagedTaskNotify} is bound.  If so, it clear the binding and
     * increments the counter bound to {@code nonManagedTaskCounter}.  The task
     * always reschedules itself.
     */
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

    /**
     * Checks that managed tasks get run. <p>
     *
     * When the application first starts up, the initialize method schedules an
     * instance of {@link ManagedTask} to run.  That task reschedules itself
     * each time it runs. <p>
     *
     * When the client requests that checks be performed, the run method sets a
     * flag asking {@code ManagedTask} to increment a counter, and then checks
     * that the counter has been incremented.
     */
    private static class CheckManagedTask extends CheckTask
	implements Initialize
    {
	private static final long serialVersionUID = 1;
	private boolean started;
	private int count;
	CheckManagedTask() {
	    initializers.add(this);
	}
	public void initialize() {
	    taskManager.scheduleTask(new ManagedTask());
	}
	boolean runInternal() {
	    if (!started) {
		dataManager.markForUpdate(this);
		started = true;
		ManagedTask.requestNotify();
		count = ManagedTask.getCurrentCount();
	    } else {
		int currentCount = ManagedTask.getCurrentCount();
		if (currentCount > count) {
		    return true;
		}
	    }
	    taskManager.scheduleTask(this, DELAY);
	    return false;
	}
    }

    static {
	new CheckManagedTask();
    }

    /**
     * A persistent managed task. <p>
     * 
     * Each time this task is run, it checks to see if {@code
     * managedTaskNotify} is bound.  If so, it clears the binding and
     * increments the counter bound to {@code managedTaskCounter}.  The task
     * always reschedules itself.
     */
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

    /** Checks that we can find an existing channel. */
    private static class CheckExistingChannelTask extends CheckTask
	implements Initialize
    {
	private static final long serialVersionUID = 1;
	CheckExistingChannelTask() {
	    initializers.add(this);
	}
	public void initialize() {
	    AppContext.getChannelManager().createChannel(
		channelName, new MyChannelListener(), Delivery.RELIABLE);
	}
	boolean runInternal() {
	    channelManager.getChannel(channelName).send(
		null, stringToBuffer("Message from server to channel"));
	    return true;
	}
    }

    static {
	new CheckExistingChannelTask();
    }

    /** Tests an existing {@lnk ScalableHashMap}. **/
    private static class CheckPersistentScalableHashMap extends CheckTask
	implements Initialize
    {
	private static final long serialVersionUID = 1;
	private static final int size = 20;
	private static final String name = "scalableHashMap";
	CheckPersistentScalableHashMap() {
	    initializers.add(this);
	}
	public void initialize() {
	    Map<Integer, String> map = new ScalableHashMap<Integer, String>();
	    for (int i = 0; i < size; i++) {
		map.put(i, String.valueOf(i));
	    }
	    dataManager.setBinding(name, map);
	}
	boolean runInternal() {
	    @SuppressWarnings("unchecked")
	    Map<Integer, String> map =
		(Map<Integer, String>) dataManager.getBinding(name);
	    for (int i = 0; i < size; i++) {
		String expected = String.valueOf(i);
		String value = map.get(i);
		if (!expected.equals(value)) {
		    throw new RuntimeException(
			"Found " + value + ", expected " + expected);
		}
	    }
	    return true;
	}
    }

    static {
	new CheckPersistentScalableHashMap();
    }

    /** Tests an existing {@link ScalableHashSet}. **/
    private static class CheckPersistentScalableHashSet extends CheckTask
	implements Initialize
    {
	private static final long serialVersionUID = 1;
	private static final int size = 20;
	private static final String name = "scalableHashSet";
	CheckPersistentScalableHashSet() {
	    initializers.add(this);
	}
	public void initialize() {
	    Set<Integer> map = new ScalableHashSet<Integer>();
	    for (int i = 0; i < size; i++) {
		map.add(i);
	    }
	    dataManager.setBinding(name, map);
	}
	boolean runInternal() {
	    @SuppressWarnings("unchecked")
	    Set<Integer> set = (Set<Integer>) dataManager.getBinding(name);
	    for (int i = 0; i < size; i++) {
		if (!set.contains(i)) {
		    throw new RuntimeException("Value not found: " + i);
		}
	    }
	    return true;
	}
    }

    static {
	new CheckPersistentScalableHashSet();
    }

    /* -- API Checks -- */

    /** Checks the API of the {@link AppContext} class. */
    private static class CheckAppContextTask extends CheckTask {
	private static final long serialVersionUID = 1;	
	CheckAppContextTask() { }
	boolean runInternal() {
	    AppContext.getChannelManager();
	    AppContext.getDataManager();
	    AppContext.getManager(DataManager.class);
	    AppContext.getTaskManager();
	    return true;
	}
    }

    static {
	new CheckAppContextTask();
    }

    /* AppListener already checked */

    /** Checks API of the {@link Channel} interface. */
    private static class CheckChannelTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckChannelTask() { }
	boolean runInternal() {
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
	    return true;
	}
    }

    static {
	new CheckChannelTask();
    }

    /* ChannelListener already checked */

    /* ChannelManager already checked */

    /** Checks the API of the {@link ClientSession} interface. */
    private static class CheckClientSessionTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckClientSessionTask() { }
	boolean runInternal() {
	    getSession().getName();
	    getSession().isConnected();
	    getSession().send(stringToBuffer("hi"));
	    return true;
	}
    }

    static {
	new CheckClientSessionTask();
    }

    /* ClientSessionListener already checked */

    /** Checks the API of the {@link DataManager} interface. */
    private static class CheckDataManagerTask extends CheckTask {
	private static final long serialVersionUID = 1;	
	CheckDataManagerTask() { }
	boolean runInternal() {
	    ManagedCounter counter = new ManagedCounter();
	    dataManager.createReference(counter);
	    dataManager.setBinding("Binding1", counter);
	    dataManager.getBinding("Binding1");
	    dataManager.removeBinding("Binding1");
	    dataManager.nextBoundName(null);
	    dataManager.markForUpdate(counter);
	    dataManager.removeObject(counter);
	    return true;
	}
    }

    static {
	new CheckDataManagerTask();
    }

    /** Checks the API of the {@link Delivery} interface. */
    private static class CheckDeliveryTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckDeliveryTask() { }
	boolean runInternal() {
	    Delivery[] d = new Delivery[] {
		Delivery.ORDERED_UNRELIABLE, Delivery.RELIABLE,
		Delivery.UNORDERED_RELIABLE, Delivery.UNRELIABLE
	    };
	    return true;
	}
    }

    static {
	new CheckDeliveryTask();
    }

    /** Checks the APIs of the various exception classes. */
    private static class CheckExceptionsTask extends CheckTask {
	private static final long serialVersionUID = 1;	
	CheckExceptionsTask() { }
	boolean runInternal() {
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
	    return true;
	}
    }

    static {
	new CheckExceptionsTask();
    }

    /* ManagedObject already checked */

    /* ManagedObjectRemoval already checked */

    /** Checks the API of the {@link ManagedReference} interface. */
    private static class CheckManagedReferenceTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckManagedReferenceTask() { }
	boolean runInternal() {
	    DataManager dataManager = AppContext.getDataManager();
	    ManagedCounter counter = new ManagedCounter();
	    ManagedReference<ManagedCounter> reference =
		dataManager.createReference(counter);
	    reference.get();
	    reference.getForUpdate();
	    reference.getId();
	    return true;
	}
    }

    static {
	new CheckManagedReferenceTask();
    }

    /** Checks the API of the {@link PeriodicTaskHandle} interface. */
    private static class CheckPeriodicTaskHandleTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckPeriodicTaskHandleTask() { }
	boolean runInternal() {
	    taskManager.schedulePeriodicTask(
		new DummyTask(), 1000, 1000).cancel();
	    return true;
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

    /** Checks the APIs for the various scalable collection classes. */
    private static class CheckScalableCollectionsTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckScalableCollectionsTask() { }
	boolean runInternal() {
	    new ScalableHashMap<String, String>();
	    new ScalableHashMap<String, String>(3);
	    new ScalableHashMap<String, String>(
		new HashMap<String, String>());
	    new ScalableHashSet<String>();
	    new ScalableHashSet<String>(new ArrayList<String>());
	    new ScalableHashSet<String>(3);
	    return true;
	}
    }

    static {
	new CheckScalableCollectionsTask();
    }
    /** Checks the API of the {@link SimpleSgsProtocol} interface. */
    private static class CheckSimpleSgsProtocolTask extends CheckTask {
	private static final long serialVersionUID = 1;
	CheckSimpleSgsProtocolTask() { }
	boolean runInternal() {
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
	    return true;
	}
    }

    static {
	new CheckSimpleSgsProtocolTask();
    }

    /* Task already checked */

    /* TaskManager already checked */

    /* -- Utility classes -- */

    /**
     * An interface that classes should implement if they want to be called by
     * the implementation of the {@link #initialize AppListener.initialize}
     * method.
     */
    interface Initialize {

	/** Perform initial operations on startup. */
	void initialize();
    }

    /**
     * An abstract base class for creating tasks that perform checks when
     * requested by the client, and notify the client when the checks have
     * passed. <p>
     *
     * The constructor adds the instance to the list of tasks to be run. <p>
     *
     * The {@link #runChecks runChecks} method runs the check tasks. <p>
     *
     * Each task's {@link #runInternal runInternal} method should return {@code
     * true} if the check has passed, return {@code false} if the check is not
     * yet completed, and should throw a non-retryable runtime exception if the
     * check failed.  When all the checks have been completed, then it sends a
     * message to the client.
     */
    abstract static class CheckTask
	implements ManagedObject, Serializable, Task
    {
	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/**
	 * The name that stores the counter that records how many checks have
	 * been completed.
	 */
	private static final String completedBinding = "checkTaskCompleted";

	/**
	 * The name that stores the counter that records how many checks have
	 * failed.
	 */
	private static final String failedBinding = "checkTaskFailed";

	/** The list of check tasks. */
	private static final List<CheckTask> checks =
	    new ArrayList<CheckTask>();

	/** The message that clients will send to request running checks. */
	static final String requestChecks = "Run checks";

	/**
	 * The message to send to clients when checks are completed.  The
	 * number of failures will be appended.
	 */
	static final String checksCompleted = "Checks completed, failures: ";

	/** The position of this test in the list of checks. */
	private final int index;

	/**
	 * A reference to the client session, for sending messages to clients.
	 */
	private ManagedReference<ClientSession> session;

	/** The total number of checks that need to be performed. */
	private int totalChecks;

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
		ManagedCounter completed =
		    (ManagedCounter) dataManager.getBinding(completedBinding);
		completed.reset();
		ManagedCounter failed =
		    (ManagedCounter) dataManager.getBinding(failedBinding);
		failed.reset();
	    } catch (NameNotBoundException e) {
	    }
	    int numChecks = checks.size();
	    for (CheckTask task : checks) {
		/*
		 * For each check, first store the session and the total number
		 * of checks, and then schedule the task.
		 */
		task.setSession(session);
		task.setTotalChecks(numChecks);
		taskManager.scheduleTask(task);
	    }
	}

	/**
	 * Implements {@link Task#run Task.run} by calling {@link #runInternal
	 * runInternal}.  The check will be marked completed successfully if
	 * {@code runInternal} returns {@code true}, will be considered still
	 * in progress if it returns {@code false}, and will be considered
	 * failed if it throw a non-retryable runtime exception.
	 */
	public final void run() {
	    try {
		if (runInternal()) {
		    completed(true);
		}
	    } catch (RuntimeException e) {
		if (e instanceof ExceptionRetryStatus &&
		    ((ExceptionRetryStatus) e).shouldRetry())
		{
		    throw e;
		} else {
		    System.out.println("Task " + this + " failed: " + e);
		    e.printStackTrace();
		    completed(false);
		}
	    }
	}

	/**
	 * Performs the main operation of the task.
	 *
	 * @returns	{@code true} if the check passed, else {@code false} if
	 *		the check is still in progress
	 * @throws	RuntimeException if the check failed
	 */
	abstract boolean runInternal();

	/** Returns the client session. */
	ClientSession getSession() {
	    if (session == null) {
		throw new IllegalStateException("Session must be set first");
	    }
	    return session.get();
	}
	
	/**
	 * Subclasses should call this method when their run method determines
	 * that the check has been completed.
	 */
	private void completed(boolean passed) {
	    getSession().send(
		stringToBuffer(
		    this + ": Completed " + index + " of " + totalChecks));
	    ManagedCounter completed;
	    ManagedCounter failed;
	    try {
		completed =
		    (ManagedCounter) dataManager.getBinding(completedBinding);
		failed =
		    (ManagedCounter) dataManager.getBinding(failedBinding);
	    } catch (NameNotBoundException e) {
		completed = new ManagedCounter();
		dataManager.setBinding(completedBinding, completed);
		failed = new ManagedCounter();
		dataManager.setBinding(failedBinding, failed);
	    }
	    completed.incrementCount();
	    if (!passed) {
		failed.incrementCount();
	    }
	    if (totalChecks == completed.getCount()) {
		getSession().send(
		    stringToBuffer(checksCompleted + failed.getCount()));
	    }
	}

	/** Stores the client session. */
	private void setSession(ClientSession session) {
	    dataManager.markForUpdate(this);
	    this.session = dataManager.createReference(session);
	}

	/** Stores the total number of checks. */
	private void setTotalChecks(int totalChecks) {
	    dataManager.markForUpdate(this);
	    this.totalChecks = totalChecks;
	}
    }

    /** A simple managed object. */
    private static class Marker implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	Marker() { }
    }

    /**
     * A managed object counter.  Implements {@link ManagedObjectRemoval} as a
     * simple way to make sure that interface is defined.
     */
    private static class ManagedCounter
	implements ManagedObject, Serializable, ManagedObjectRemoval
    {
	private static final long serialVersionUID = 1;

	/** The count. */
	private int count = 0;

	ManagedCounter() { }

	/** Returns the current count. */
	int getCount() {
	    return count;
	}

	/** Increments the current count. */
	void incrementCount() {
	    dataManager.markForUpdate(this);
	    count++;
	}

	/** Resets the current count to zero. */
	void reset() {
	    dataManager.markForUpdate(this);
	    count = 0;
	}

	/** Implements {@link ManagedObjectRemoval}. */
	public void removingObject() { }
    }

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
}
