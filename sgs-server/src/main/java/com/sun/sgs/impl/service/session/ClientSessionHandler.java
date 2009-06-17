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
import com.sun.sgs.impl.service.session.ClientSessionImpl.SendEvent;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl.Action;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import static com.sun.sgs.impl.util.AbstractService.isRetryableException;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.protocol.LoginFailureException;
import com.sun.sgs.protocol.LoginRedirectException;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.protocol.RelocateFailureException;
import com.sun.sgs.protocol.RequestCompletionHandler;
import com.sun.sgs.protocol.RequestFailureException;
import com.sun.sgs.protocol.SessionProtocol;
import com.sun.sgs.protocol.SessionProtocolHandler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.SimpleCompletionHandler;
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

    /** Message indicating login was refused for a non-specific reason. */
    private static final String LOGIN_REFUSED_REASON = "Login refused";

    /** Message indicating relocation was refused for a non-specific reason. */
    static final String RELOCATE_REFUSED_REASON = "Relocate refused";

    /** The client session service that created this client session. */
    private final ClientSessionServiceImpl sessionService;

    /** The data service. */
    private final DataService dataService;

    /** The I/O channel for sending messages to the client. */
    private final SessionProtocol protocol;
    
    /** The session ID as a BigInteger. */
    volatile BigInteger sessionRefId;

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

    /** If non-null, contains the completion handler for
     * relocating this session to a new node. */
    private volatile SimpleCompletionHandler relocateCompletionHandler = null;
    
    /** Indicates whether this session is shut down. */
    private boolean shutdown = false;

    /** Completion future for setting up the client session. */
    private final SetupCompletionFuture setupCompletionFuture;

    /** The queue of tasks for notifying listeners of received messages. */
    private volatile TaskQueue taskQueue = null;

    /**
     * Constructs an handler for a client session that is logging in.
     *
     * @param	sessionService the ClientSessionService instance
     * @param	dataService the DataService instance
     * @param	sessionProtocol a session protocol
     * @param	identity an identity
     * @param	completionHandler a completion handler for the associated
     *		request
     */
    ClientSessionHandler(
	ClientSessionServiceImpl sessionService,
	DataService dataService,
	SessionProtocol sessionProtocol,
	Identity identity,
	RequestCompletionHandler<SessionProtocolHandler> completionHandler)
    {
	this(sessionService, dataService, sessionProtocol, identity,
	     completionHandler, null);
    }

    /**
     * Constructs an handler for a client session.  If {@code sessionRefId}
     * is non-{@code null}, then the associated client session is relocating
     * from another node, otherwise it is considered a new client session
     * logging in.
     *
     * @param	sessionService the ClientSessionService instance
     * @param	dataService the DataService instance
     * @param	sessionProtocol a session protocol
     * @param	identity an identity
     * @param	completionHandler a completion handler for the associated
     *		request
     * @param	sessionRefId the client session ID, or {@code null}
     */
    ClientSessionHandler(
	ClientSessionServiceImpl sessionService,
	DataService dataService,
	SessionProtocol sessionProtocol,
	Identity identity,
	RequestCompletionHandler<SessionProtocolHandler> completionHandler,
	BigInteger sessionRefId)
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
	this.sessionRefId = sessionRefId;
	setupCompletionFuture =
	    new SetupCompletionFuture(this, completionHandler);

	final boolean loggingIn = sessionRefId == null;
	scheduleNonTransactionalTask(
	    new AbstractKernelRunnable("HandleLoginOrRelocateRequest") {
		public void run() {
		    setupClientSession(loggingIn);
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
	RequestCompletionFuture future =
	    new RequestCompletionFuture(completionHandler);
	if (!readyForRequests(future)) {
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
	enqueueCompletionNotification(future);
    }

    /** {@inheritDoc} */
    public void channelMessage(final BigInteger channelId,
			       final ByteBuffer message,
			       RequestCompletionHandler<Void> completionHandler)
    {
	RequestCompletionFuture future =
	    new RequestCompletionFuture(completionHandler);
	if (!readyForRequests(future)) {
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
	enqueueCompletionNotification(future);
    }

    /** {@inheritDoc} */
    public void logoutRequest(
	RequestCompletionHandler<Void> completionHandler)
    {
	RequestCompletionFuture future =
	    new RequestCompletionFuture(completionHandler);
	if (!readyForRequests(future)) {
	    return;
	}
	scheduleHandleDisconnect(isConnected(), false);

	// Enable protocol message channel to read immediately
	future.done();
    }

    /** {@inheritDoc} */
    public void disconnect(RequestCompletionHandler<Void> completionHandler) {
	RequestCompletionFuture future =
	    new RequestCompletionFuture(completionHandler);
	if (!readyForRequests(future)) {
	    return;
	}
	scheduleHandleDisconnect(false, true);
	
	// TBD: should we wait to notify until client disconnects connection?
	future.done();
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
    private boolean isConnected() {

	State currentState = getCurrentState();
	return
	    currentState == State.CONNECTED ||
	    currentState == State.LOGIN_HANDLED;
    }

    /**
     * Returns {@code true} if this client session is relocating to another
     * node.
     */
    boolean isRelocating() {
	return relocateCompletionHandler != null;
    }

    /**
     * Indicates that all parties are done with relocation preparation, and
     * notifies the client that it is relocating to another node.
     */    
    void relocatePreparationComplete() {
	if (isRelocating()) {
	    relocateCompletionHandler.completed();
	}
    }

    /**
     * Returns {@code true} if the associated client session is ready for
     * requests (i.e., it has completed login and it is not relocating);
     * otherwise, sets the appropriate {@code RequestFailureException} on
     * the specified {@code future} and returns {@code false}. <p>
     *
     * This method is invoked before proceeding with processing a
     * request, and if this method returns {@code false}, the request
     * should be dropped.
     *
     * @param	future a future on which to set an exception if this
     * 		session is not ready to process requests
     * @return	{@code true} if requests can be processed by the
     *		associated client session and {@code false} otherwise
     */
    private boolean readyForRequests(RequestCompletionFuture future) {
	if (!loggedIn) {
	    logger.log(
		Level.FINE,
		"request received before login completed:{0}", this);
	    future.setException(
		new RequestFailureException(
		    "session is not logged in",
		    RequestFailureException.FailureReason.LOGIN_PENDING));
	    return false;
	} else if (isRelocating()) {
	    logger.log(
		Level.FINE,
		"request received while session is relocating:{0}", this);
	    future.setException(
		new RequestFailureException(
		    "session is relocating",
		    RequestFailureException.FailureReason.RELOCATE_PENDING));
	    return false;
	} else if (!isConnected()) {
	    logger.log(
		Level.FINE,
		"request received while session is disconnecting:{0}", this);
	    future.setException(
		new RequestFailureException(
		    "session is disconnecting",
		    RequestFailureException.FailureReason.DISCONNECT_PENDING));
	    return false;
	} else {
	    return true;
	}
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
     * Notifies the "setup" future that the setup was successful so that it can
     * send the appropriate success indication (either successful login
     * or relocation) to the client, and sets local state indicating that
     * the request has been handled and the client is logged in.
     */
    private void setupSuccess() {
	synchronized (lock) {
	    checkConnectedState();
	    loggedIn = true;
	    setupCompletionFuture.done();
	    state = State.LOGIN_HANDLED;
	}
    }
	
    /**
     * Notifies the "setup" future that setup failed with the specified
     * {@code exception} so that it can send the appropriate failure
     * indication to the client, and sets local state indicating that
     * request has been handled.
     *
     * @param	exception the login failure exception
     */
    private void setupFailure(Exception exception) {
	checkNull("exception", exception);
	synchronized (lock) {
	    checkConnectedState();
	    setupCompletionFuture.setException(exception);
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

	synchronized (lock) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "handleDisconnect handler:{0} disconnectHandled:{1}",
			   this, disconnectHandled);
	    }
	    if (disconnectHandled) {
		return;
	    }
	    disconnectHandled = true;
	    if (state != State.DISCONNECTED) {
		state = State.DISCONNECTING;
	    }
	}

	if (sessionRefId != null) {
	    sessionService.removeHandler(sessionRefId, isRelocating());
	}
	
	// TBD: Due to the scheduler's behavior, this notification
	// may happen out of order with respect to the
	// 'notifyLoggedIn' callback.  Also, this notification may
	// also happen even though 'notifyLoggedIn' was not invoked.
	// Are these behaviors okay?  -- ann (3/19/07)
	if (!isRelocating()) {
	    scheduleTask(new AbstractKernelRunnable("NotifyLoggedOut") {
		    public void run() {
			identity.notifyLoggedOut();
		    } });
	}
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

	if (sessionRefId != null && !isRelocating()) {
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
	    if (disconnectHandled) {
		return;
	    }
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
	final RequestCompletionFuture future)
    {
	taskQueue.addTask(
	    new AbstractKernelRunnable("ScheduleCompletionNotification") {
		public void run() {
		    future.done();
		} }, identity);
    }

    /**
     * Invokes the {@code setStatus} method on the node mapping service
     * with {@code false} to mark the identity as inactive.  This method
     * is invoked when a login is redirected and also when this client
     * session is disconnected.
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
     * If {@code loggingIn} is {@code true} handles a login request to
     * establish a client session); otherwise handles a relocate request
     * to re-establish a client session.  In either case, this method
     * notifies the completion handler specified during construction when
     * the request is completed.
     */
    private void setupClientSession(final boolean loggingIn) {
	logger.log(
	    Level.FINEST, 
	    "setting up client session for identity:{0} loggingIn:{1}",
	    identity, loggingIn);
	
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
	    notifySetupFailureAndDisconnect(
		loggingIn ?
		new LoginFailureException(LOGIN_REFUSED_REASON, e) :
		new RelocateFailureException(RELOCATE_REFUSED_REASON, e));
	    return;
	}

	long assignedNodeId = node.getId();
	if (assignedNodeId == sessionService.getLocalNodeId()) {
	    /*
	     * Handle this login (or relocation) request locally.  First,
	     * validate that the user is allowed to log in (or relocate).
	     */
	    if (!sessionService.validateUserLogin(
		    identity, ClientSessionHandler.this, loggingIn))
	    {
		// This client session is not allowed to proceed.
		notifySetupFailureAndDisconnect(
		    loggingIn ?
		    new LoginFailureException(
			LOGIN_REFUSED_REASON,
			LoginFailureException.FailureReason.DUPLICATE_LOGIN) :
		    new RelocateFailureException(
 			RELOCATE_REFUSED_REASON,
			RelocateFailureException.
			    FailureReason.DUPLICATE_LOGIN));
		return;
	    }
	    taskQueue = sessionService.createTaskQueue();
	    /*
	     * If logging in, store the client session in the data store
	     * (which assigns it an ID--the ID of the reference to the
	     * client session object).
	     */
	    if (loggingIn) {
		CreateClientSessionTask createTask =
		    new CreateClientSessionTask();
		try {
		    sessionService.runTransactionalTask(createTask, identity);
		} catch (Exception e) {
		    logger.logThrow(
		        Level.WARNING, e,
			"Storing ClientSession for identity:{0} throws",
			identity);
		    notifySetupFailureAndDisconnect(
			new LoginFailureException(LOGIN_REFUSED_REASON, e));
		    return;
		}
		sessionRefId = createTask.getId();
	    }

	    /*
	     * Inform the session service that this handler is available
	     * (by invoking "addHandler").  If logging in, schedule a task
	     * to perform client login (which calls the AppListener.loggedIn
	     * method), otherwise set the "relocating" flag in the client
	     * session's state to false to indicate that relocation is
	     * complete.
	     */
	    sessionService.addHandler(
		sessionRefId, ClientSessionHandler.this,
		loggingIn ? null : identity);
	    if (loggingIn) {
		scheduleTask(new LoginTask());
	    } else {
		try {
		    sessionService.runTransactionalTask(
 			new AbstractKernelRunnable("SetSessionRelocated") {
			    public void run() {
				ClientSessionImpl sessionImpl =
				    ClientSessionImpl.getSession(
 				        dataService, sessionRefId);
				sessionImpl.relocationComplete();
			    } }, identity);
		    setupSuccess();
		} catch (Exception e) {
		    logger.logThrow(
		        Level.WARNING, e,
			"Relocating ClientSession for identity:{0} " +
			"to local node:{1} throws",
			identity, sessionService.getLocalNodeId());
		    notifySetupFailureAndDisconnect(
			new RelocateFailureException(
			    RELOCATE_REFUSED_REASON, e));
		    return;
		}
	    }
	    
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
                                setupFailure(
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
     * Notifies the "setup" future that login has been redirected to the
     * specified {@code node}, and sets local state indicating that the
     * login request has been handled.
     *
     * @param	node a node
     */
    private void loginRedirect(Node node) {
	synchronized (lock) {
	    checkConnectedState();
	    Set<ProtocolDescriptor> descriptors =
		sessionService.getProtocolDescriptors(node.getId());
	    setupCompletionFuture.setException(
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
     * Schedules a task to notify the "setup" future of failure and then
     * disconnect the client session.
     *
     * @param	throwable an exception that occurred while setting up the
     *		client session, or {@code null}
     */
    private void notifySetupFailureAndDisconnect(final Exception exception) {
	scheduleNonTransactionalTask(
	    new AbstractKernelRunnable("NotifySetupFailureAndDisconnect") {
		public void run() {
		    try {
                        setupFailure(exception);
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
	sessionService.getTaskScheduler().scheduleTask(task, identity);
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
	
	private volatile BigInteger id;;

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
	    id = sessionImpl.getId();
	}

	/**
	 * Returns the session ID for the created client session.
	 */
	BigInteger getId() {
	    return id;
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

		sessionService.checkContext().
		    addCommitAction(sessionRefId,
				    new LoginResultAction(true, null), true);

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
			LOGIN_REFUSED_REASON,
			LoginFailureException.FailureReason.REJECTED_LOGIN);
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
		sessionService.checkContext().
		    addCommitAction(
			sessionRefId,
			new LoginResultAction(false, loginFailureEx),
			true);

		sessionImpl.disconnect();
	    }
	}
    }

    /**
     * This future is an abstract implementation for the futures
     * passed to the {@code RequestCompletionHandler} used by the
     * {@code ProtocolListener} and {@code SessionProtocolHandler}
     * APIs.
     */
    private abstract static class AbstractCompletionFuture<T>
            implements Future<T>
    {
	/** The completion handler for the associated request. */
	private final RequestCompletionHandler<T>
	    completionHandler;

	/**
	 * Indicates whether the operation associated with this future
	 * is complete.
	 */
	private boolean done = false;

	/** Lock for accessing the {@code done} field. */
	private Object lock = new Object();
	
	/** An exception cause, or {@code null}. */
	private volatile Throwable exceptionCause = null;

	/** Constructs an instance. */
	protected AbstractCompletionFuture(
	    RequestCompletionHandler<T> completionHandler)
	{
	    checkNull("completionHandler", completionHandler);
	    this.completionHandler = completionHandler;
	}

	/**
	 * Returns the value associated with this future.
	 *
	 * @return	the value for this future
	 */
	protected abstract T getValue();
	    
	/**
	 * Returns the value associated with this future, or throws
	 * {@code ExecutionException} if there is a problem
	 * processing the operation associated with this future.
	 *
	 * @return	the value for this future
	 * @throws	ExecutionException if there is a problem processing
	 *		the operation associated with this future
	 */
	private T getValueInternal() throws ExecutionException {
	    if (exceptionCause != null) {
		throw new ExecutionException(exceptionCause);
	    } else {
		return getValue();
	    }
	}

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
	    return getValueInternal();
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
		return getValueInternal();
	    }
	}

	/** {@inheritDoc} */
	public boolean isCancelled() {
	    return false;
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

	/** {@inheritDoc} */
	public boolean isDone() {
	    synchronized (lock) {
		return done;
	    }
	}

	/**
	 * Indicates that the operation associated with this future is
	 * complete and notifies the associated completion
	 * handler. Subsequent invocations to {@link #isDone isDone}
	 * will return {@code true}.
	 */
	void done() {
	    synchronized (lock) {
		done = true;
		lock.notifyAll();
	    }	
	    completionHandler.completed(this);
	}
    }

    /**
     * This future is constructed with the {@code RequestCompletionHandler}
     * passed to one of the {@link SessionProtocolHandler} methods.
     */
    private static class RequestCompletionFuture
            extends AbstractCompletionFuture<Void>
    {
	/**
	 * Constructs an instance with the specified {@code completionHandler}.
	 *
	 * @param	completionHandler a completionHandler
	 */
	RequestCompletionFuture(
	    RequestCompletionHandler<Void> completionHandler)
	{
	    super(completionHandler);
	}

	/** {@inheritDoc} */
	protected Void getValue() {
	    return null;
	}
    }

    /**
     * This future is constructed with the {@link RequestCompletionHandler}
     * passed to one of the {@code ProtocolListener}'s methods: {@code
     * newLogin} or {@code relocatedSession}.
     */
    static class SetupCompletionFuture
	extends AbstractCompletionFuture<SessionProtocolHandler>
    {
	/** The session protocol handler. */
	private final SessionProtocolHandler protocolHandler;

	/**
	 * Constructs an instance with the specified {@code protocolHandler}
	 * and {@code completionHandler}.
	 *
	 * @param	protocolHandler a session protocol handler
	 * @param	completionHandler a completionHandler
	 */
	SetupCompletionFuture(
	    SessionProtocolHandler protocolHandler,
	    RequestCompletionHandler<SessionProtocolHandler> completionHandler)
        {
	    super(completionHandler);
	    this.protocolHandler = protocolHandler;
	}

	/** {@inheritDoc} */
	protected SessionProtocolHandler getValue() {
	    return protocolHandler;
	}
    }

    /* -- Implement Commit Actions -- */

    /**
     * An action to report the result of a login.
     */
    private class LoginResultAction implements Action {
	/** The login result. */
	private final boolean loginSuccess;

	/** The login exception. */
	private final LoginFailureException loginException;
	
	/**
	 * Records the login result in this context, so that the specified
	 * client {@code session} can be notified when this context
	 * commits.  If {@code success} is {@code false}, the specified
	 * {@code exception} will be used as the cause of the {@code
	 * ExecutionException} in the {@code Future} passed to the {@link
	 * RequestCompletionHandler} for the login request, and no
	 * subsequent session messages will be forwarded to the session,
	 * even if they have been enqueued during the current transaction.
	 * If success is {@code true}, then the {@code Future} passed to
	 * the {@code RequestCompletionHandler} for the login request will
	 * contain this {@link SessionProtocolHandler}.
	 *
	 * <p>When the transaction commits, the session's associated {@code
	 * ClientSessionHandler} is notified of the login result, and if
	 * {@code success} is {@code true}, all enqueued messages will be
	 * delivered to the client session.
	 *
	 * @param	session	a client session
	 * @param	success if {@code true}, login was successful
	 * @param	exception a login failure exception, or {@code null}
	 *		(only valid if {@code success} is {@code false}
	 * @throws 	TransactionException if there is a problem with the
	 *		current transaction
	 */
	LoginResultAction(boolean success, LoginFailureException ex) {
	    loginSuccess = success;
	    loginException = ex;
	}

	public boolean flush() {
	    if (!isConnected()) {
		return false;
	    } else if (loginSuccess) {
		setupSuccess();
		return true;
	    } else {
		setupFailure(loginException);
		return false;
	    }
	}
    }

    /**
     * An action to send a message.
     */
    class SendMessageAction implements Action {

	private final SendEvent sendEvent;

	/**
	 * Constructs and instance with the specified {@code sendEvent}.
	 *
	 * @param sendEvent a send event containing a message and delivery
	 *        guarantee
	 */
	SendMessageAction(SendEvent sendEvent) {
	    this.sendEvent = sendEvent;
	}

	/** {@inheritDoc} */
	public boolean flush() {
	    if (!isConnected()) {
		return false;
	    }
	    
	    try {
		protocol.sessionMessage(
		    ByteBuffer.wrap(sendEvent.message),
		    sendEvent.delivery);
	    } catch (Exception e) {
		logger.logThrow(Level.WARNING, e,
				"sessionMessage throws");
	    }
	    return true;
	}
    }

    /**
     * An action to move the associated client session from this node to
     * another node.
     */
    class MoveAction implements Action, SimpleCompletionHandler {

	/** The new node. */
	private final Node newNode;

	/** The client session server. */
	private final ClientSessionServer server;

	/** The relocation key. */
	private byte[] relocationKey;

	private boolean completed = false;

	/**
	 * Constructs an instance with the specified {@code newNode}.
	 *
	 * @param newNode the new node for the session
	 */
	MoveAction(Node newNode) {
	    this.newNode = newNode;
	    this.server =
		sessionService.getClientSessionServer(newNode.getId());
	}

	/** {@inheritDoc} */
	public boolean flush() {
	    if (!isConnected()) {
		return false;
	    }
	    
	    try {
		/*
		 * Notify new node that session is being relocated there and
		 * obtain relocation key.
		 */
		byte[] relocationKey =
		    server.relocatingSession(
 			identity, sessionRefId.toByteArray(),
			sessionService.getLocalNodeId());
		synchronized (this) {
		    this.relocationKey = relocationKey;
		}
		
		/*
		 * Notify client to relocate its session to the new node
		 * specifying the relocation key.
		 */
		relocateCompletionHandler = this;

		sessionService.notifyPrepareToRelocate(
		    sessionRefId, newNode.getId());
		
	    } catch (Exception e) {
		// If there is a problem contacting the destination node,
		// then disconnect the client session immediately.
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e,
			"relocating client session:{0} throws", this);
		}
		handleDisconnect(false, true);
	    }
	    return false;
	}

	/** {@inheritDoc} */
	public void completed() {
	    synchronized (this) {
		assert relocationKey != null;
		if (completed) {
		    return;
		}
		completed = true;
	    }
	    
	    try {
		Set<ProtocolDescriptor> descriptors =
		    sessionService.getProtocolDescriptors(newNode.getId());
		byte[] relocationKey;
		synchronized (this) {
		    relocationKey = this.relocationKey;
		}
		protocol.relocate(
			newNode, descriptors, ByteBuffer.wrap(relocationKey));

		/*
		 * Schedule a task to close the client session if it is not
		 * closed in a timely fashion.  This will also clean up local
		 * client session state if not done so already.
		 *
		 * TBD: is this taken care of by the protocol layer
		 * already because it monitors disconnecting clients?
		 */
		sessionService.getTaskScheduler().scheduleTask(
		    new AbstractKernelRunnable("CloseMovedClientSession") {
			public void run() {
			    handleDisconnect(false, true);
			} },
		    identity,
		    System.currentTimeMillis() + 1000L);
		
	    } catch (IOException e) {
		// If there is a problem contacting the client,
		// then disconnect the client session immediately.
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e,
			"relocating client session:{0} throws", this);
		}
		handleDisconnect(false, true);
	    }
	}
    }

    /**
     * An action to disconnect the client session.
     */
    class DisconnectAction implements Action {
	/** {@inheritDoc} */
	public boolean flush() {
	    handleDisconnect(false, true);
	    return false;
	}
    }

}
