/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.service.session;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import static com.sun.sgs.impl.util.AbstractService.isRetryableException;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.protocol.LoginFailureException;
import com.sun.sgs.protocol.LoginFailureException.FailureReason;
import com.sun.sgs.protocol.LoginRedirectException;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.protocol.RequestCompletionHandler;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles sending/receiving messages to/from a client session and
 * disconnecting a client session.
 */
class ClientSessionHandler implements SessionProtocolHandler {

    /** Connection state. */
    private static enum State {
        /** Session is connected */
        CONNECTED,
	/** Session login ack (success, failure, redirect) has been sent */
	LOGIN_HANDLED,
        /** Disconnection is in progress */
        DISCONNECTING, 
        /** Session is disconnected */
        DISCONNECTED
    }

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(
	    "com.sun.sgs.impl.service.session.handler"));

    /** Message for indicating login/authentication failure. */
    private static final String LOGIN_REFUSED_REASON = "Login refused";

    /** The client session service that created this client session. */
    private final ClientSessionServiceImpl sessionService;

    /** The data service. */
    private final DataService dataService;

    /** The I/O channel for sending messages to the client. */
    private final SessionProtocol protocol;
    
    /** The session ID as a BigInteger. */
    private volatile BigInteger sessionRefId;

    /** The identity for this session. */
    private final Identity identity;

    /** The login status. */
    private volatile boolean loggedIn;

    /** The lock for accessing the following fields: {@code state},
     * {@code disconnectHandled}, and {@code shutdown}.
     */
    private final Object lock = new Object();

    /** The connection state. */
    private State state = State.CONNECTED;

    /** Indicates whether session disconnection has been handled. */
    private boolean disconnectHandled = false;

    /** Indicates whether this session is shut down. */
    private boolean shutdown = false;

    /** Login completion future for this handler. */
    private final LoginCompletionFuture loginCompletionFuture;

    /** The queue of tasks for notifying listeners of received messages. */
    private volatile TaskQueue taskQueue = null;

    /**
     * Constructs an instance of this class using the provided byte channel,
     * and starts reading from the connection.
     *
     * @param	sessionService the ClientSessionService instance
     * @param	dataService the DataService instance
     * @param	sessionProtocol a session protocol
     * @param	identity an identity
     * @param	completionHandler a completion handler for the login request
     */
    ClientSessionHandler(
	ClientSessionServiceImpl sessionService,
	DataService dataService,
	SessionProtocol sessionProtocol,
	Identity identity,
	RequestCompletionHandler<SessionProtocolHandler> completionHandler)
    {
	checkNull("sessionService", sessionService);
	checkNull("dataService", dataService);
	checkNull("sessionProtocol", sessionProtocol);
	checkNull("identity", identity);
	checkNull("completionHandler", completionHandler);
	this.sessionService = sessionService;
        this.dataService = dataService;
	this.protocol = sessionProtocol;
	this.identity = identity;
	loginCompletionFuture =
	    new LoginCompletionFuture(this, completionHandler);
	
	scheduleNonTransactionalTask(
	    new AbstractKernelRunnable("HandleLoginRequest") {
		public void run() {
		    handleLoginRequest();
		} });

	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "creating new ClientSessionHandler on nodeId:{0}",
		        sessionService.getLocalNodeId());
	}
    }

    /* -- Implement SessionProtocolHandler -- */
    
    /** {@inheritDoc} */
    public void sessionMessage(
	final ByteBuffer message,
	RequestCompletionHandler<Void> completionHandler)
    {
	CompletionFutureImpl future = new CompletionFutureImpl();
	if (!loggedIn) {
	    logger.log(
		Level.WARNING,
		"session message received before login completed:{0}",
		this);
	    future.done();
	    completionHandler.completed(future);
	    return;
	}
	taskQueue.addTask(
	    new AbstractKernelRunnable("NotifyListenerMessageReceived") {
		public void run() {
		    ClientSessionImpl sessionImpl =
			ClientSessionImpl.getSession(dataService, sessionRefId);
		    if (sessionImpl != null) {
			if (isConnected()) {
			    sessionImpl.getClientSessionListener(dataService).
				receivedMessage(
				    message.asReadOnlyBuffer());
			}
		    } else {
			scheduleHandleDisconnect(false, true);
		    }
		} }, identity);

	// Wait until processing is complete before notifying future
	enqueueCompletionNotification(completionHandler, future);
    }

    /** {@inheritDoc} */
    public void channelMessage(final BigInteger channelId,
			       final ByteBuffer message,
			       RequestCompletionHandler<Void> completionHandler)
    {
	CompletionFutureImpl future = new CompletionFutureImpl();
	if (!loggedIn) {
	    logger.log(
		Level.WARNING,
		"channel message received before login completed:{0}",
		this);
	    future.done();
	    completionHandler.completed(future);
	    return;
	}

	taskQueue.addTask(
	    new AbstractKernelRunnable("HandleChannelMessage") {
		public void run() {
		    ClientSessionImpl sessionImpl =
			ClientSessionImpl.getSession(dataService, sessionRefId);
		    if (sessionImpl != null) {
			if (isConnected()) {
			    sessionService.getChannelService().
				handleChannelMessage(
				    channelId,
				    sessionImpl.getWrappedClientSession(),
				    message.asReadOnlyBuffer());
			}
		    } else {
			scheduleHandleDisconnect(false, true);
		    }
		} }, identity);

	// Wait until processing is complete before notifying future
	enqueueCompletionNotification(completionHandler, future);
    }

    /** {@inheritDoc} */
    public void logoutRequest(
	RequestCompletionHandler<Void> completionHandler)
    {
	scheduleHandleDisconnect(isConnected(), false);

	// Enable protocol message channel to read immediately
	CompletionFutureImpl future = new CompletionFutureImpl();
	future.done();
	completionHandler.completed(future);
    }

    /** {@inheritDoc} */
    public void disconnect(RequestCompletionHandler<Void> completionHandler) {
	scheduleHandleDisconnect(false, true);
	
	// TBD: should we wait to notify until client disconnects connection?
	CompletionFutureImpl future = new CompletionFutureImpl();
	future.done();
	completionHandler.completed(future);
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + identity + "]@" + sessionRefId;
    }
    
    /* -- Instance methods -- */

    /**
     * Returns {@code true} if this handler is connected, otherwise
     * returns {@code false}.
     *
     * @return	{@code true} if this handler is connected
     */
    boolean isConnected() {

	State currentState = getCurrentState();
	return
	    currentState == State.CONNECTED ||
	    currentState == State.LOGIN_HANDLED;
    }

    /**
     * Returns the protocol for the associated client session.
     *
     * @return	a protocol
     */
    SessionProtocol getSessionProtocol() {
	return protocol;
    }
 
    /**
     * Returns {@code true} if the login for this session has been handled,
     * otherwise returns {@code false}.
     *
     * @return	{@code true} if the login for this session has been handled
     */
    boolean loginHandled() {
	return getCurrentState() != State.CONNECTED;
    }

    /**
     * Sends a login success message to the client, and sets local state
     * indicating that the login request has been handled and the client is
     * logged in.
     */
    void loginSuccess() {
	synchronized (lock) {
	    checkConnectedState();
	    loggedIn = true;
	    loginCompletionFuture.done();
	    state = State.LOGIN_HANDLED;
	}
    }
	
    /**
     * Sends a login failure message for the specified {@code reason} to
     * the client, and sets local state indicating that the login request
     * has been handled.
     *
     * @param	exception the login failure exception
     */
    void loginFailure(LoginFailureException exception) {
	checkNull("exception", exception);
	synchronized (lock) {
	    checkConnectedState();
	    loginCompletionFuture.setException(exception);
	    state = State.LOGIN_HANDLED;
	}
    }

    /**
     * Handles a disconnect request (if not already handled) by doing
     * the following: <ol>
     *
     * <li> sending a disconnect acknowledgment (logout success)
     *    if 'graceful' is true
     *
     * <li> if {@code closeConnection} is {@code true}, closing this
     *    session's connection, otherwise monitor the connection's status
     *    to ensure that the client disconnects it. 
     *
     * <li> submitting a transactional task to call the 'disconnected'
     *    callback on the listener for this session.
     *
     * <li> notifying the identity that the session has
     *    logged out.
     *
     * <li> notifying the node mapping service that the identity is no
     *	  longer active. 
     * </ol>
     *
     * <p>Note:if {@code graceful} is {@code true}, then {@code
     * closeConnection} must be {@code false} so that the client will receive
     * the logout success message.  The client may not
     * receive the message if the connection is disconnected immediately
     * after sending the message.
     *
     * @param	graceful if {@code true}, the disconnection was graceful
     *		(i.e., due to a logout request).
     * @param	closeConnection if {@code true}, close this session's
     *		connection immediately, otherwise monitor the connection to
     *		ensure that it is terminated in a timely manner by the client
     */
    void handleDisconnect(final boolean graceful, boolean closeConnection) {

	logger.log(Level.FINEST, "handleDisconnect handler:{0}", this);
	
	synchronized (lock) {
	    if (disconnectHandled) {
		return;
	    }
	    disconnectHandled = true;
	    if (state != State.DISCONNECTED) {
		state = State.DISCONNECTING;
	    }
	}

	if (sessionRefId != null) {
	    sessionService.removeHandler(sessionRefId);
	}
	
	// TBD: Due to the scheduler's behavior, this notification
	// may happen out of order with respect to the
	// 'notifyLoggedIn' callback.  Also, this notification may
	// also happen even though 'notifyLoggedIn' was not invoked.
	// Are these behaviors okay?  -- ann (3/19/07)
	scheduleTask(new AbstractKernelRunnable("NotifyLoggedOut") {
		public void run() {
		    identity.notifyLoggedOut();
		} });
	if (sessionService.removeUserLogin(identity, this)) {
	    deactivateIdentity();
	}

	if (getCurrentState() != State.DISCONNECTED) {
	    if (graceful) {
		assert !closeConnection;
	    }

	    if (closeConnection) {
		closeConnection();
	    }
	}

	if (sessionRefId != null) {
	    scheduleTask(
	      new AbstractKernelRunnable("NotifyListenerAndRemoveSession") {
	        public void run() {
		    ClientSessionImpl sessionImpl = 
			ClientSessionImpl.getSession(dataService, sessionRefId);
		    sessionImpl.notifyListenerAndRemoveSession(
			dataService, graceful, true);
		}
	    });
	}
    }

    /**
     * Schedule a non-transactional task for disconnecting the client.
     *
     * <p>Note:if {@code graceful} is {@code true}, then {@code
     * closeConnection} must be {@code false} so that the client will receive
     * the logout success message.  The client may not
     * receive the message if the connection is disconnected immediately
     * after sending the message.
     *
     * @param	graceful if {@code true}, disconnection is graceful (i.e.,
     * 		a logout success message is sent before
     * 		disconnecting the client session)
     * @param	closeConnection if {@code true}, close this session's
     *		connection immediately, otherwise monitor the connection to
     *		ensure that it is terminated in a timely manner by the client
     */
    private void scheduleHandleDisconnect(
	final boolean graceful, final boolean closeConnection)
    {
        synchronized (lock) {
            if (state != State.DISCONNECTED) {
                state = State.DISCONNECTING;
	    }
        }
	scheduleNonTransactionalTask(
	  new AbstractKernelRunnable("HandleDisconnect") {
	    public void run() {
		handleDisconnect(graceful, closeConnection);
	    } });
    }

    /**
     * Closes the connection associated with this instance.
     */
    void closeConnection() {
	if (protocol.isOpen()) {
	    try {
		protocol.close();
	    } catch (IOException e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e,
			"closing connection for handle:{0} throws",
			protocol);
		}
	    }
	}
    }

    /**
     * Flags this session as shut down, and closes the connection.
     */
    void shutdown() {
	synchronized (lock) {
	    if (shutdown) {
		return;
	    }
	    shutdown = true;
	    disconnectHandled = true;
	    state = State.DISCONNECTED;
	    closeConnection();
	}
    }
    
    /* -- other private methods and classes -- */

    /**
     * Schedules a task to notify the completion handler.  Use this method
     * to delay notification until a task resulting from an earlier request
     * has been completed.
     *
     * @param	completionHandler a completion handler
     * @param	future a completion future
     */
    private void enqueueCompletionNotification(
	final RequestCompletionHandler<Void> completionHandler,
	final CompletionFutureImpl future)
{
	taskQueue.addTask(
	    new AbstractKernelRunnable("ScheduleCompletionNotification") {
		public void run() {
		    future.done();
		    completionHandler.completed(future);
		} }, identity);
    }

    /**
     * Invokes the {@code setStatus} method on the node mapping service
     * with {@code false} to mark
     * the identity as inactive.  This method is invoked when a login is
     * redirected and also when a this client session is disconnected.
     */
    private void deactivateIdentity() {
	try {
	    /*
	     * Set identity's status for this class to 'false'.
	     */
	    sessionService.nodeMapService.setStatus(
		ClientSessionHandler.class, identity, false);
	} catch (Exception e) {
	    logger.logThrow(
		Level.WARNING, e,
		"setting status for identity:{0} throws",
		identity.getName());
	}
    }
    
    /**
     * Returns the current state.
     */
    private State getCurrentState() {
	State currentState;
	synchronized (lock) {
	    currentState = state;
	}
	return currentState;
    }

    /**
     * Handles a login request, notifying the specified {@code future} when
     * the request is completed.
     */
    private void handleLoginRequest() {
	logger.log(
	    Level.FINEST, 
	    "handling login request for identity:{0}", identity);
	
	final Node node;
	try {
	    /*
	     * Get node assignment.
	     */
	    sessionService.nodeMapService.assignNode(
		ClientSessionHandler.class, identity);
	    GetNodeTask getNodeTask = new GetNodeTask(identity);		
	    sessionService.runTransactionalTask(getNodeTask, identity);
	    node = getNodeTask.getNode();
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE, "identity:{0} assigned to node:{1}",
			   identity, node);
	    }
	    
	} catch (Exception e) {
	    logger.logThrow(
	        Level.WARNING, e,
		"getting node assignment for identity:{0} throws", identity);
	    sendLoginFailureAndDisconnect(
		new LoginFailureException(LOGIN_REFUSED_REASON, e));
	    return;
	}

	long assignedNodeId = node.getId();
	if (assignedNodeId == sessionService.getLocalNodeId()) {
	    /*
	     * Handle this login request locally: Validate that the
	     * user is allowed to log in, store the client session in
	     * the data store (which assigns it an ID--the ID of the
	     * reference to the client session object), inform the
	     * session service that this handler is available (by
	     * invoking "addHandler"), and schedule a task to perform
	     * client login (call the AppListener.loggedIn method).
	     */
	    if (!sessionService.validateUserLogin(
		    identity, ClientSessionHandler.this))
	    {
		// This login request is not allowed to proceed.
		sendLoginFailureAndDisconnect(
		    new LoginFailureException(
			LOGIN_REFUSED_REASON, FailureReason.DUPLICATE_LOGIN));
		return;
	    }
	    taskQueue = sessionService.createTaskQueue();
	    CreateClientSessionTask createTask =
		new CreateClientSessionTask();
	    try {
		sessionService.runTransactionalTask(createTask, identity);
	    } catch (Exception e) {
		logger.logThrow(
		    Level.WARNING, e,
		    "Storing ClientSession for identity:{0} throws", identity);
		sendLoginFailureAndDisconnect(
		    new LoginFailureException(LOGIN_REFUSED_REASON, e));
		return;
	    }
	    sessionService.addHandler(
		sessionRefId, ClientSessionHandler.this);
	    scheduleTask(new LoginTask());
	    
	} else {
	    /*
	     * Redirect login to assigned (non-local) node.
	     */
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(
		    Level.FINE,
		    "redirecting login for identity:{0} " +
		    "from nodeId:{1} to node:{2}",
		    identity, sessionService.getLocalNodeId(), node);
	    }
	    
	    scheduleNonTransactionalTask(
	        new AbstractKernelRunnable("SendLoginRedirectMessage") {
		    public void run() {
			try {
                            try {
                                loginRedirect(node);
                            } catch (Exception ex) {
                                loginFailure(
                                    new LoginFailureException("Redirect failed",
                                                              ex));
                            }
                        } finally {
                            handleDisconnect(false, false);
                        }
		    } });
	}
    }

    /**
     * Sends a login redirect message for the specified {@code host} and
     * {@code port} to the client, and sets local state indicating that
     * the login request has been handled.
     *
     * @param	node a node
     */
    private void loginRedirect(Node node) {
	synchronized (lock) {
	    checkConnectedState();
	    Set<ProtocolDescriptor> descriptors =
		ClientSessionServiceImpl.getInstance().
		    getProtocolDescriptors(node.getId());
	    loginCompletionFuture.setException(
 		new LoginRedirectException(node, descriptors));
	    state = State.LOGIN_HANDLED;
	}
    }

    /**
     * Throws an {@code IllegalStateException} if the associated client
     * session handler is not in the {@code CONNECTED} state.
     */
    private void checkConnectedState() {
	assert Thread.holdsLock(lock);
	
	if (state != State.CONNECTED) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.log(
		    Level.WARNING,
		    "unexpected state:{0} for login protocol message, " +
		    "session:{1}", state.toString(), this);
	    }
	    throw new IllegalStateException("unexpected state: " +
					    state.toString());
	}
    }

    /**
     * Sends a login failure message to the client and
     * disconnects the client session.
     *
     * @param	throwable an exception that occurred while processing the
     * 		login request, or {@code null}
     */
    private void sendLoginFailureAndDisconnect(
 	final LoginFailureException exception)
    {
	scheduleNonTransactionalTask(
	    new AbstractKernelRunnable("SendLoginFailureMessage") {
		public void run() {
		    try {
                        loginFailure(exception);
                    } finally {
                        handleDisconnect(false, false);
                    }
		} });
    }
    
    /**
     * Schedules a non-durable, transactional task.
     */
    private void scheduleTask(KernelRunnable task) {
	sessionService.scheduleTask(task, identity);
    }

    /**
     * Schedules a non-durable, non-transactional task.
     */
    private void scheduleNonTransactionalTask(KernelRunnable task) {
	sessionService.scheduleNonTransactionalTask(task, identity);
    }

    /**
     * This is a transactional task to obtain the node assignment for
     * a given identity.
     */
    private class GetNodeTask extends AbstractKernelRunnable {

	private final Identity authenticatedIdentity;
	private volatile Node node = null;

	GetNodeTask(Identity authenticatedIdentity) {
	    super(null);
	    this.authenticatedIdentity = authenticatedIdentity;
	}

	public void run() throws Exception {
	    node = sessionService.
		nodeMapService.getNode(authenticatedIdentity);
	}

	Node getNode() {
	    return node;
	}
    }

    /**
     * Constructs the ClientSession.
     */
    private class CreateClientSessionTask extends AbstractKernelRunnable {

	/** Constructs and instance. */
	CreateClientSessionTask() {
	    super(null);
	}

	/** {@inheritDoc} */
	public void run() {
	    ClientSessionImpl sessionImpl =
                    new ClientSessionImpl(sessionService,
                                          identity,
                                          protocol.getDeliveries(),
                                          protocol.getMaxMessageLength());
	    sessionRefId = sessionImpl.getId();
	}
    }
    
    /**
     * This is a transactional task to notify the application's
     * {@code AppListener} that this session has logged in.
     */
    private class LoginTask extends AbstractKernelRunnable {
	
	/** Constructs an instance. */
	LoginTask() {
	    super(null);
	}
        
        /**
         * Retrieve the {@code AppListener} from the {@code DataService},
         * unwrapping it from its {@code ManagedSerializable} if necessary.
         * 
         * @return the {@code AppListener} for the application
         */
        @SuppressWarnings("unchecked")
        private AppListener getAppListener() {
            ManagedObject obj = dataService.getServiceBinding(
                    StandardProperties.APP_LISTENER);
            return (obj instanceof AppListener) ?
                (AppListener) obj :
                ((ManagedSerializable<AppListener>) obj).get();
        }
	
	/**
	 * Invokes the {@code AppListener}'s {@code loggedIn}
	 * callback, which returns a client session listener.  If the
	 * returned listener is serializable, then this method does
	 * the following:
	 *
	 * a) queues the appropriate acknowledgment to be
	 * sent when this transaction commits, and
	 * b) schedules a task (on transaction commit) to call
	 * {@code notifyLoggedIn} on the identity.
	 *
	 * If the client session needs to be disconnected (if {@code
	 * loggedIn} returns a non-serializable listener (including
	 * {@code null}), or throws a non-retryable {@code
	 * RuntimeException}, then this method submits a
	 * non-transactional task to disconnect the client session.
	 * If {@code loggedIn} throws a retryable {@code
	 * RuntimeException}, then that exception is thrown to the
	 * caller.
	 */
	public void run() {
	    AppListener appListener = getAppListener();
	    logger.log(
		Level.FINEST,
		"invoking AppListener.loggedIn session:{0}", identity);

	    ClientSessionListener returnedListener = null;
	    RuntimeException ex = null;

	    ClientSessionImpl sessionImpl =
		ClientSessionImpl.getSession(dataService, sessionRefId);
	    try {
		returnedListener =
		    appListener.loggedIn(sessionImpl.getWrappedClientSession());
	    } catch (RuntimeException e) {
		ex = e;
	    }
		
	    if (returnedListener instanceof Serializable) {
		logger.log(
		    Level.FINEST,
		    "AppListener.loggedIn returned {0}", returnedListener);

		sessionImpl.putClientSessionListener(
		    dataService, returnedListener);

		sessionService.addLoginResult(sessionImpl, true, null);
		
		sessionService.scheduleTaskOnCommit(
		    new AbstractKernelRunnable("NotifyLoggedIn") {
			public void run() {
			    logger.log(
			        Level.FINE,
				"calling notifyLoggedIn on identity:{0}",
				identity);
			    // notify that this identity logged in,
			    // whether or not this session is connected at
			    // the time of notification.
			    identity.notifyLoggedIn();
			} });
		
	    } else {
		LoginFailureException loginFailureEx;
		if (ex == null) {
		    logger.log(
		        Level.WARNING,
			"AppListener.loggedIn returned non-serializable " +
			"ClientSessionListener:{0}", returnedListener);
		    loginFailureEx = new LoginFailureException(
			LOGIN_REFUSED_REASON, FailureReason.REJECTED_LOGIN);
		} else if (!isRetryableException(ex)) {
		    logger.logThrow(
			Level.WARNING, ex,
			"Invoking loggedIn on AppListener:{0} with " +
			"session: {1} throws",
			appListener, ClientSessionHandler.this);
		    loginFailureEx =
			new LoginFailureException(LOGIN_REFUSED_REASON, ex);
		} else {
		    throw ex;
		}
		sessionService.addLoginResult(
		    sessionImpl, false, loginFailureEx);

		sessionImpl.disconnect();
	    }
	}
    }

    /**
     * This future is an abstract implementation for the futures
     * returned by {@code ProtocolListener} and {@code SessionProtocolHandler}.
     */
    private abstract static class AbstractCompletionFuture<T>
            implements Future<T>
    {
	/**
	 * Indicates whether the operation associated with this future
	 * is complete.
	 */
	private boolean done = false;

	/** Lock for accessing the {@code done} field. */
	private Object lock = new Object();
	
	/** Constructs an instance. */
	protected AbstractCompletionFuture() {
	}

	/**
	 * Returns the value associated with this future, or throws
	 * {@code ExecutionException} if there is a problem
	 * processing the operation associated with this future.
	 *
	 * @return	the value for this future
	 * @throws	ExecutionException if there is a problem processing
	 *		the operation associated with this future
	 */
	protected abstract T getValue()
	    throws ExecutionException;


	/** {@inheritDoc} */
	public boolean cancel(boolean mayInterruptIfRunning) {
	    return false;
	}

	/** {@inheritDoc} */
	public T get() throws InterruptedException, ExecutionException {
	    synchronized (lock) {
		while (!done) {
		    lock.wait();
		}
	    }
	    return getValue();
	}

	/** {@inheritDoc} */
	public T get(long timeout, TimeUnit unit)
	    throws InterruptedException, ExecutionException, TimeoutException
	{
	    synchronized (lock) {
		if (!done) {
		    unit.timedWait(lock, timeout);
		}
		if (!done) {
		    throw new TimeoutException();
		}
		return getValue();
	    }
	}

	/** {@inheritDoc} */
	public boolean isCancelled() {
	    return false;
	}

	/** {@inheritDoc} */
	public boolean isDone() {
	    synchronized (lock) {
		return done;
	    }
	}

	/**
	 * Indicates that the operation associated with this future
	 * is complete. Subsequent invocations to {@link #isDone
	 * isDone} will return {@code true}.
	 */
	void done() {
	    synchronized (lock) {
		done = true;
		lock.notifyAll();
	    }
	}
    }

    /**
     * This future is returned from {@link SessionProtocolHandler}
     * operations.
     */
    private static class CompletionFutureImpl
            extends AbstractCompletionFuture<Void>
    {
	/** {@inheritDoc} */
	protected Void getValue() {
	    return null;
	}
    }

    /**
     * This future is returned from the {@code ProtocolListener}'s
     * {@code newLogin} method.
     */
    private static class LoginCompletionFuture
	extends AbstractCompletionFuture<SessionProtocolHandler>
    {
	/** The session protocol handler. */
	private final SessionProtocolHandler protocolHandler;

	/** The completion handler for the login request. */
	private final RequestCompletionHandler<SessionProtocolHandler>
	    completionHandler;

	/** An exception cause, or {@code null}. */
	private volatile Throwable exceptionCause = null;

	/**
	 * Constructs an instance with the specified {@code protocolHandler}
	 * and {@code completionHandler).
	 *
	 * @param	protocolHandler a session protocol handler
	 * @param	completionHandler a completionHandler
	 */
	LoginCompletionFuture(
	    SessionProtocolHandler protocolHandler,
	    RequestCompletionHandler<SessionProtocolHandler> completionHandler)
        {
	    super();
	    this.protocolHandler = protocolHandler;
	    this.completionHandler = completionHandler;
	}

	/** {@inheritDoc} */
	protected SessionProtocolHandler getValue()
	    throws ExecutionException
	{
	    if (exceptionCause != null) {
		throw new ExecutionException(exceptionCause);
	    } else {
		return protocolHandler;
	    }
	}

	/**
	 * Sets the exception cause for this future to the specified
	 * {@code throwable}.  The given exception will be used as
	 * the cause of the {@code ExecutionException} thrown by
	 * this future's {@code get} methods.
	 *
	 * @param	throwable an exception cause
	 */
	void setException(Throwable throwable) {
	    checkNull("throwable", throwable);
	    exceptionCause = throwable;
	    done();
	}

	/**
	 * Notifies the completion handler that the login request
	 * processing is complete.
	 */
	void done() {
	    super.done();
	    completionHandler.completed(this);
	}
    }
}
