/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
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
import com.sun.sgs.impl.util.AbstractCompletionFuture;
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
import com.sun.sgs.protocol.SessionRelocationProtocol;
import com.sun.sgs.service.ClientSessionStatusListener;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.SimpleCompletionHandler;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.Future;
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
    final Identity identity;

    /** The login status. */
    private volatile boolean loggedIn;

    /** The lock for accessing the following fields: {@code state},
     * {@code disconnectHandled}, {@code relocatePrepareCompletionHandler},
     * and {@code shutdown}.
     */
    private final Object lock = new Object();

    /** The connection state. */
    private State state = State.CONNECTED;

    /** Indicates whether session disconnection has been handled. */
    private boolean disconnectHandled = false;

    /** If non-null, contains the completion handler for
     * preparing this session to relocate to a new node. */
    private MoveAction relocatePrepareCompletionHandler = null;

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
	
	// TBD: should this be allowed to disconnect no matter what?
	if (!readyForRequests(future)) {
	    return;
	}
	scheduleHandleDisconnect(false, true);
	
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
    boolean isConnected() {

	State currentState = getCurrentState();
	return
	    currentState == State.CONNECTED ||
	    currentState == State.LOGIN_HANDLED;
    }

    /**
     * Returns {@code true} if this session's protocol handler supports
     * relocation (i.e., implements the {@link SessionRelocationProtocol}
     * interface; otherwise returns {@code false}.
     */
    boolean supportsRelocation() {
	return protocol instanceof SessionRelocationProtocol;
    }

    /**
     * Returns {@code true} if this client session has begun preparing to
     * relocate, or has relocated to another node.
     */
     boolean isRelocating() {
	 synchronized (lock) {
	     return relocatePrepareCompletionHandler != null;
	 }
    }

    /**
     * Returns {@code true} if this client session is disconnecting from
     * the local node AND terminating the associated client session.  The
     * client session is considered to be disconnecting iff the following
     * conditions are true:
     *
     * 1) this handler has been marked for disconnection
     * 2) the session is not relocating, OR, the session is relocating
     * and its relocation has not completed.
     */
    private boolean isTerminating() {
	synchronized (lock) {
	    return !isConnected() &&
		(relocatePrepareCompletionHandler == null ||
		 !relocatePrepareCompletionHandler.isCompleted());
	}
    }

    /**
     * Indicates that all parties are done with relocation preparation, and
     * notifies the client that it should suspend messages (before
     * notifying the client to relocate to another node).
     */    
    void setRelocatePreparationComplete() {
	synchronized (lock) {
	    if (relocatePrepareCompletionHandler != null) {
		relocatePrepareCompletionHandler.suspend();
	    }
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
	} else if (relocatePrepareCompletionHandler != null &&
		   relocatePrepareCompletionHandler.isCompleted()) {
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
     * Returns the protocol for the associated client session, or {@code
     * null} if the session is relocating.
     *
     * @return	a protocol, or {@code null} if the session is relocating
     */
    SessionProtocol getSessionProtocol() {
	return isRelocating() ? null : protocol;
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
     * Handles disconnecting the associated client session (if not already
     * handled) by doing the following: <ol>
     *
     * <li> notifies the client session service to clean up the client
     *      session's handler and login information,
     *
     * <li> notifies the node mapping service to deativate the client's
     *	    identity if the identity is no longer active on this node,
     *
     * <li> if {@code closeConnection} is {@code true}, closes this
     *      session's connection,
     *
     * <li> if the session is terminating (not relocating), schedules a
     *      transactional task to invoke, on this session's {@code
     *      ClientSessionListener}, the {@code disconnected} callback
     *      with {@code graceful} as its argument and then clean up the
     *      session's persistent data, and also schedules a task to notify
     *      the identity that its corresponding session has logged out.
     * </ol>
     *
     * <p>Note:if {@code graceful} is {@code true}, then {@code
     * closeConnection} must be {@code false} so that the client's {@code
     * SessionProtocol} can send a notification of logout success to the
     * client.  The client may not receive such a notification if the
     * connection is disconnected immediately.
     *
     * <p>In the cases of login redirection, session relocation, and
     * graceful logout, it is the responsibility of the client's {@code
     * SessionProtocol} to close the client's connection in a timely manner
     * after notifying the client.
     *
     * @param	graceful if {@code true}, indicates that disconnection is
     *		due to a (graceful) logout request
     * @param	closeConnection if {@code true}, close this session's
     *		connection immediately
     */
    void handleDisconnect(final boolean graceful, boolean closeConnection) {

	synchronized (lock) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST, "handleDisconnect handler:{0} " +
			   "disconnectHandled:{1}", this, disconnectHandled);
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
	    sessionService.removeHandler(sessionRefId, !isTerminating());
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

	if (sessionRefId != null && isTerminating()) {
	    scheduleTask(
	      new AbstractKernelRunnable("NotifyListenerAndRemoveSession") {
	        public void run() {
		    ClientSessionImpl sessionImpl = 
			ClientSessionImpl.getSession(dataService, sessionRefId);
		    sessionImpl.notifyListenerAndRemoveSession(
			dataService, graceful, true);
		}
	    });
	    // TBD: Due to the scheduler's behavior, this notification
	    // may happen out of order with respect to the
	    // 'notifyLoggedIn' callback.  Also, this notification may
	    // also happen even though 'notifyLoggedIn' was not invoked.
	    // Are these behaviors okay?  -- ann (3/19/07)
	    scheduleTask(new AbstractKernelRunnable("NotifyLoggedOut") {
		    public void run() {
			identity.notifyLoggedOut();
		    } });
	}
    }

    /**
     * Schedules a non-transactional task to handle disconnecting the
     * associated client session.
     *
     * @param	graceful if {@code true}, indicates that disconnection is
     *		due to a (graceful) logout request
     * @param	closeConnection if {@code true}, close this session's
     *		connection immediately
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
    private void closeConnection() {
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
	synchronized (lock) {
	    state = State.DISCONNECTED;
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
	
        /*
         * Get node assignment.
         */
        long assignedNodeId = -1L;
        try {
            assignedNodeId = sessionService.nodeMapService.assignNode(
                                    ClientSessionHandler.class, identity);
	} catch (Exception e) {
	    logger.logThrow(
	        Level.WARNING, e,
		"getting node assignment for identity:{0} throws", identity);
	}
	    
	if (assignedNodeId < 0) {
	    logger.log(Level.WARNING,
		       "getting node assignment for identity:{0} failed",
                       identity);
	    notifySetupFailureAndDisconnect(
		loggingIn ?
		new LoginFailureException(
		    LOGIN_REFUSED_REASON,
		    LoginFailureException.FailureReason.SERVER_UNAVAILABLE) :
		new RelocateFailureException(
		    RELOCATE_REFUSED_REASON,
		    RelocateFailureException.FailureReason.SERVER_UNAVAILABLE));
	    return;
        }

	if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "identity:{0} assigned to node:{1}",
		       identity, assignedNodeId);
	}

	if (assignedNodeId == sessionService.getLocalNodeId()) {
	    /*
	     * Handle this login (or relocation) request locally.  First,
	     * validate that the user is allowed to log in (or relocate).
	     */
	    if (!sessionService.validateUserLogin(
		    identity, ClientSessionHandler.this, loggingIn))
	    {
		// This client session is not allowed to proceed.
		if (logger.isLoggable(Level.FINE)) {
		    logger.log(
			Level.FINE,
			"{0} rejected to node:{1} identity:{2}",
			(loggingIn ? "User login" : "Session relocation"),
			sessionService.getLocalNodeId(), identity);
		}
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
	     * If logging in, store the client session in the data store.
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
	     * Inform the session service that this handler is available.  If
	     * logging in, schedule a task to perform client login (which calls
	     * the AppListener.loggedIn method), otherwise set the "relocating"
	     * flag in the client session's state to false to indicate that
	     * relocation is complete.
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
		    identity, sessionService.getLocalNodeId(), assignedNodeId);
	    }

	    final long nodeId = assignedNodeId;
	    scheduleNonTransactionalTask(
	        new AbstractKernelRunnable("SendLoginRedirectMessage") {
		    public void run() {
			try {
                            try {
                                loginRedirect(nodeId);
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
     * specified {@code nodeId}, and sets local state indicating that the
     * login request has been handled.
     *
     * @param	node a nodeId
     */
    private void loginRedirect(long nodeId) {
	synchronized (lock) {
	    checkConnectedState();
	    Set<ProtocolDescriptor> descriptors =
		sessionService.getProtocolDescriptors(nodeId);
	    setupCompletionFuture.setException(
 		new LoginRedirectException(nodeId, descriptors));
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
     * @param	exception an exception that occurred while setting up the
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
			        Level.FINEST,
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

	/** {@inheritDoc} */
	public void setException(Throwable throwable) {
 	    super.setException(throwable);
	}

	public void done() {
	    super.done();
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

	/** {@inheritDoc} */
	public void setException(Throwable throwable) {
	    super.setException(throwable);
	}

	/** {@inheritDoc} */
	public void done() {
	    super.done();
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
	 * @param	success if {@code true}, login was successful
	 * @param	ex a login failure exception, or {@code null}
	 *		(only valid if {@code success} is {@code false}
	 * @throws 	TransactionException if there is a problem with the
	 *		current transaction
	 */
	LoginResultAction(boolean success, LoginFailureException ex) {
	    loginSuccess = success;
	    loginException = ex;
	}

	/** {@inheritDoc} */
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
     * An action to send a message.  This commit action is created by the
     * associated session's {@code ClientSessionImpl} when processing a
     * request to send a message to the client.<p>
     *
     * When this action is executed, it notifies the session's {@link
     * SessionProtocol}  to send the message obtained from the
     * {@link SendEvent} specified during construction.
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
     * An action to start the process of moving the associated client
     * session from this node to another node (the {@code newNode}
     * specified during construction).  This commit action is created by
     * the associated session's {@code ClientSessionImpl} when processing a
     * request to move the client to a new node. <p>
     *
     * When this action is executed, it sends a {@link
     * ClientSessionServer#relocatingSession relocatingSession}
     * notification to the new node's {@code ClientSessionServer} to obtain
     * a relocation key for the client session.  Once the relocation key is
     * obtained, it notifies the client session service to notify all
     * registered {@link ClientSessionStatusListener}s (i.e., the
     * {@code ChannelService}) to prepare to relocate the client session.<p>
     *
     * When all {@code ClientSessionStatusListener}s have completed
     * preparing the client session to relocate, this instance's
     * {@link MoveAction#suspend suspend} method is invoked which
     * notifies the associated session's {@code SessionProtocol} to
     * suspend sending messages to the server.<p>
     *
     * When the suspend operation's {@code SuspendCompletionHandler} is
     * notified as {@code completed}, that completion handler notifies
     * this action's {@code completed} method, which, in turn notifies
     * the associated session's {@link SessionProtocol} to {@code
     * relocate} the client's connection, supplying the new node's
     * information and the relocation key.
     */
    class MoveAction implements Action, SimpleCompletionHandler {

	/** The new node. */
	private final Node newNode;

	/** The client session server. */
	private final ClientSessionServer server;

	/** The relocation key. */
	private byte[] relocationKey;

	private boolean isCompleted = false;

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
	    byte[] key;
	    try {
		/*
		 * Notify new node that session is being relocated there and
		 * obtain relocation key.
		 */
		key = server.relocatingSession(
 			  identity, sessionRefId.toByteArray(),
			  sessionService.getLocalNodeId());
		
	    } catch (Exception e) {
		// If there is a problem contacting the destination node or
		// obtaining the relocation key, disconnect the client
		// session immediately.
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e,
			"relocating client session:{0} throws", this);
		}
		handleDisconnect(false, true);
		return false;
	    }
	    
	    synchronized (this) {
		this.relocationKey = key;
	    }
		
	    /*
	     * Notify client to relocate its session to the new node
	     * specifying the relocation key.
	     */
	    synchronized (lock) {
		relocatePrepareCompletionHandler = this;
	    }
	    
	    sessionService.notifyPrepareToRelocate(
		sessionRefId, newNode.getId());
	    // TBD: why is this return value false?
	    return false;
	}

	/**
	 * Returns {@code true} if relocation preparation is completed.
	 * @return {@code true} if relocation preparation is completed
	 */
	public synchronized boolean isCompleted() {
	    return isCompleted;
	}

	/**
	 * Suspends messages to the client before sending the 'relocate'
	 * notification.   This method is invoked after the session has
	 * been prepared for relocation.  When message suspension is
	 * complete, this instance's {@code completed} method is invoked
	 * to notify the client to relocate.
	 */
	public void suspend() {
	    try {
		if (!supportsRelocation()) {
		    logger.log(
			Level.WARNING,
			"Disconnecting a non-relocatable session:{0} " +
			"that was erroneously prepared to relocate", identity);
		    handleDisconnect(false, true);
		    return;
		}
		((SessionRelocationProtocol) protocol).suspend(
		    new SuspendCompletionHandler());
		
	    } catch (Exception e) {
		if (logger.isLoggable(Level.WARNING)) {
		    logger.logThrow(
			Level.WARNING, e,
			"suspending messages to client session:{0} throws",
			this); 
		}
	    }
	}

	/** {@inheritDoc} <p>
	 *
	 * This method is invoked after the session has been prepared for
	 * relocation and messages have been suspended from the client
	 * (i.e., invoked from the {@code SuspendCompletionHandler.completed}
	 * method).
	 */
	public void completed() {
	    synchronized (this) {
		assert relocationKey != null;
		if (isCompleted) {
		    return;
		}
		isCompleted = true;
	    }
	    
	    if (!supportsRelocation()) {
		logger.log(
		    Level.WARNING,
			"Disconnecting a non-relocatable session:{0} " +
		    "that was erroneously prepared to relocate", identity);
		handleDisconnect(false, true);
		return;
	    }
	    
	    final Set<ProtocolDescriptor> descriptors =
		sessionService.getProtocolDescriptors(newNode.getId());
	    final byte[] key;
	    synchronized (this) {
		key = this.relocationKey;
	    }

	    // Add client 'relocate' notification to the task queue to
	    // ensure that all previous requests sent before the client
	    // was suspended are processed before relocation.
	    taskQueue.addTask(
		new AbstractKernelRunnable("NotifySessionRelocate") {
		    public void run() {
			try {
			    ((SessionRelocationProtocol) protocol).relocate(
 				descriptors, ByteBuffer.wrap(key),
				new RelocateCompletionHandler());
			} catch (Exception e) {
			    if (logger.isLoggable(Level.WARNING)) {
				logger.logThrow(
				    Level.WARNING, e,
				    "relocating client session:{0} throws",
				    this);
			    }
			    // If there is a problem with relocation, the
			    // client session will be cleaned up by one
			    // of the "monitors" (on the old or new
			    // node) keeping track of this session's
			    // relocation, so there is no need to do it
			    // here.
			}
		    } }, identity);
	}
    }

    /**
     * An action to disconnect the client session.  This commit action is
     * created by the associated session's {@code ClientSessionImpl} when
     * processing a request to disconnect the client session.
     */
    class DisconnectAction implements Action {
	/** {@inheritDoc} */
	public boolean flush() {
	    handleDisconnect(false, true);
	    return false;
	}
    }

    /**
     * A completion handler for notifying the client to suspend messages.
     * When messages suspension is completed, this handler notifies {@code
     * MoveAction} that suspension relocation preparation is complete so that
     * it can send a 'relocate' notification to the client.
     */
    private class SuspendCompletionHandler
	implements RequestCompletionHandler<Void>
    {
	private boolean isCompleted = false;

	/** {@inheritDoc} */
	public synchronized void completed(Future<Void> result) {
	    synchronized (this) {
		isCompleted = true;
	    }

	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   "suspend completed, identity:{0} localNodeId:{1}",
			   identity, sessionService.getLocalNodeId());
	    }
	    if (relocatePrepareCompletionHandler != null) {
		relocatePrepareCompletionHandler.completed();
	    }
	}

	synchronized boolean isCompleted() {
	    return isCompleted;
	}
    }
    
    /**
     * A completion handler for notifying the client to relocate.
     */
    private class RelocateCompletionHandler
	implements RequestCompletionHandler<Void>
    {
	private boolean isCompleted = false;

	/** {@inheritDoc} */
	public synchronized void completed(Future<Void> result) {
	    // TBD: need to check result for Exception and disconnect if an
	    // exception is thrown.
	    isCompleted = true;
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   "relocate completed, identity:{0} localNodeId:{1}",
			   identity, sessionService.getLocalNodeId());
	    }
	}

	synchronized boolean isCompleted() {
	    return isCompleted;
	}
    }
}
