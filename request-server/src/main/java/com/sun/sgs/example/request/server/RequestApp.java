/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.sgs.example.request.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Defines an {@code AppListener} that responds to channel and name binding
 * requests from the client. <p>
 *
 * Clients can request to join, leave, or send messages on channels, which are
 * identified by name.  Clients can also set or get string values associated
 * with name bindings.  Getting a name binding results in the value being sent
 * back to the client in a separate message.  Multiple arguments in a request
 * should be separated by spaces.  Clients can make multiple requests that will
 * be processed within a single task by including the requests in a single
 * message, separated by newlines. <p>
 *
 * Here are the supported requests:
 * <ul>
 * <li> JoinChannel <i>channelName</i>
 * <li> LeaveChannel <i>channelName</i>
 * <li> SendChannel <i>channelName</i> <i>message</i>
 * <li> GetItem <i>itemName</i>
 * <li> SetItem <i>itemName</i> <i>value</i>
 * </ul> <p>
 *
 * This class supports the following properties:
 * <ul>
 * <li> {@code com.sun.sgs.example.request.server.report} - The number of
 *	seconds between logging performance data, defaults to {@code 20}
 * </ul>
 *
 * This application uses the {@link Logger} named {@code
 * com.sun.sgs.example.request.server.RequestApp} to log at the following
 * levels:
 * <ul>
 * <li> {@link Level#INFO Level.INFO} - Performance data
 * <li> {@link Level#CONFIG Level.CONFIG} - Initialize the application
 * <li> {@link Level#FINE Level.FINE} - Login, disconnect, or failure during
 *	processing received message
 * <li> {@link Level#FINER Level.FINER} - Receive a message
 * <li> {@link Level#FINEST Level.FINEST} - Join, leave, or send a message on a
 *	channel, send a message to a session
 * </ul>
 */
public class RequestApp implements AppListener, Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** The logger for this class. */
    private static final Logger logger = Logger.getLogger(
	RequestApp.class.getName());

    /**
     * The request for joining a channel.  Argument is the channel name, which
     * should not contain spaces.
     */
    private static final String JOIN_CHANNEL = "JoinChannel ";

    /**
     * The request for leaving a channel.  Argument is the channel name, which
     * should not contain spaces.
     */
    private static final String LEAVE_CHANNEL = "LeaveChannel ";

    /**
     * The request for sending a message on a channel.  Arguments are the
     * channel name, which should not contain spaces, followed by the message.
     */
    private static final String SEND_CHANNEL = "SendChannel ";

    /**
     * The request for getting the value of an item.  Argument is the item
     * name, which should not contain spaces.  Sends a message back containing
     * the value.
     */
    private static final String GET_ITEM = "GetItem ";

    /**
     * The request for setting the value of an item.  Arguments are the item
     * name, which should not contain spaces, followed by the new value.
     */
    private static final String SET_ITEM = "SetItem ";

    /** The request for logging a comment.  Argument is the comment to log. */
    private static final String COMMENT = "Comment ";

    /** The number of seconds between logging performance data. */
    private static final int REPORT =
	Integer.getInteger("com.sun.sgs.example.request.server.report", 20);

    /* -- Operation counters -- */

    static volatile int receivedMessage;
    static volatile int joinChannel;
    static volatile int leaveChannel;
    static volatile int sendChannel;
    static volatile int getItem;
    static volatile int setItem;

    /* Log operations */
    static {
	new ReportThread().start();
    }

    /** Creates an instance of this class. */
    public RequestApp() { }

    /* -- Implement AppListener -- */

    /** {@inheritDoc} */
    public void initialize(Properties props) {
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Initializing RequestApp props:" + props);
	}
    }

    /** {@inheritDoc} */
    public ClientSessionListener loggedIn(ClientSession session) {
	return new SessionListener(session);
    }

    /* -- Other methods -- */

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

    /* -- Nested classes -- */

    /** Implements ClientSessionListener. */
    private static class SessionListener
	implements ClientSessionListener, ManagedObject, Serializable
    {
	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** A reference to the client session. */
	private final ManagedReference<ClientSession> session;

	/** Creates an instance for the specified client session. */
	SessionListener(ClientSession session) {
	    this.session =
		AppContext.getDataManager().createReference(session);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   sessionId(this.session) +
			   ": Logged in user " + session.getName());
	    }
	}

	/* -- Implement ClientSessionListener -- */

	/** {@inheritDoc} */
	public void receivedMessage(ByteBuffer message) {
	    receivedMessage++;
	    String requests = bufferToString(message);
	    for (String request : requests.split("\n")) {
		request = request.trim();
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(Level.FINER,
			       sessionId(session) +
			       ": Received request: " + request);
		}
		try {
		    if (request.startsWith(JOIN_CHANNEL)) {
			joinChannel(request.substring(JOIN_CHANNEL.length()));
		    } else if (request.startsWith(LEAVE_CHANNEL)) {
			leaveChannel(
			    request.substring(
				LEAVE_CHANNEL.length()));
		    } else if (request.startsWith(SEND_CHANNEL)) {
			sendChannel(request.substring(SEND_CHANNEL.length()));
		    } else if (request.startsWith(GET_ITEM)) {
			getItem(request.substring(GET_ITEM.length()));
		    } else if (request.startsWith(SET_ITEM)) {
			setItem(request.substring(SET_ITEM.length()));
		    } else if (request.startsWith(COMMENT)) {
			logger.log(Level.INFO, "{0}", request);
		    } else {
			throw new RuntimeException("Unknown operation");
		    }
		} catch (Exception e) {
		    if (logger.isLoggable(Level.FINER)) {
			logger.log(
			    Level.FINER,
			    sessionId(session) +
			    ": Request '" + request + "' failed:\n" + e);
		    }
		}
	    }
	}

	/** {@inheritDoc} */
	public void disconnected(boolean graceful) {
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   sessionId(session) +
			   ": Disconnected graceful:" + graceful);
	    }
	}

	/* -- Other methods -- */

	/** Joins the specified channel. */
	private void joinChannel(String channelName) {
	    joinChannel++;
	    Channel channel = getChannel(channelName);
	    channel.join(session.get());
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   sessionId(session) +
			   ": Joined channel " + channelName);
	    }
	}

	/** Leaves the specified channel. */
	private void leaveChannel(String channelName) {
	    leaveChannel++;
	    Channel channel = getChannel(channelName);
	    channel.leave(session.get());
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   sessionId(session) +
			   ": Left channel " + channelName);
	    }
	}

	/**
	 * Sends a message to a channel, using the channel name and message
	 * specified in the arguments.
	 */
	private void sendChannel(String args) {
	    sendChannel++;
	    int space = args.indexOf(' ');
	    String channelName = (space > 0) ? args.substring(0, space) : args;
	    String message = (space > 0) ? args.substring(space + 1) : "";
	    Channel channel = getChannel(channelName);
            // Send along as if the client had sent it via a ClientChannel
	    channel.send(
                session.get(),
		stringToBuffer(
		    "Message on channel " + channelName + ": " + message));
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   sessionId(session) +
			   ": Sent to channel " + channelName + ": " +
			   message);
	    }
	}

	/** Gets the value of the specified item and sends it to the client. */
	private void getItem(String itemName) {
	    getItem++;
	    String binding = "Item-" + itemName;
	    String value;
	    try {
		ManagedString item = (ManagedString)
		    AppContext.getDataManager().getBinding(binding);
		value = item.getString();
	    } catch (NameNotBoundException e) {
		value = "[Not found]";
	    }
	    send(GET_ITEM + itemName + ": " + value);
	}

	/**
	 * Sets the value of an item, using the item name and value specified
	 * in the arguments.
	 */
	private void setItem(String args) {
	    setItem++;
	    int space = args.indexOf(' ');
	    String itemName = (space > 0) ? args.substring(0, space) : "";
	    String value = (space > 0) ? args.substring(space + 1) : args;
	    DataManager dataManager = AppContext.getDataManager();
	    String binding = "Item-" + itemName;
	    try {
		ManagedString item =
		    (ManagedString) dataManager.getBinding(binding);
		item.setString(value);
	    } catch (NameNotBoundException e) {
		dataManager.setBinding(binding, new ManagedString(value));
	    }
	}

	/**
	 * Returns the channel with the specified name, creating the channel
	 * and storing it in a name binding as needed.
	 */
	private Channel getChannel(String channelName) {
            ChannelManager channelManager = AppContext.getChannelManager();
            try {
                return channelManager.getChannel(channelName);
            } catch (NameNotBoundException e ) {
                return channelManager.createChannel(
                        channelName, null, Delivery.ORDERED_UNRELIABLE);
            }
	}

	/** Sends the specified message to the client. */
	void send(String message) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   sessionId(session) +
			   ": Sent to session: " + message);
	    }
	    session.get().send(stringToBuffer(message));
	}

	/** Returns an identifier for the session. */ 
	static String sessionId(ManagedReference<ClientSession> session) {
	    return "Session-" + session.getId();
	}
    }

    /** A managed object that holds a string. */
    private static class ManagedString implements ManagedObject, Serializable {

	/** The version of the serialized form. */
	private static final long serialVersionUID = 1;

	/** The string. */
	private String string;

	/** Creates an instance with the specified string. */
	ManagedString(String string) {
	    this.string = string;
	}

	/** Returns the string. */
	String getString() {
	    return string;
	}

	/** Sets the string. */
	void setString(String string) {
	    AppContext.getDataManager().markForUpdate(this);
	    this.string = string;
	}
    }

    /** A thread that logs operations. */
    private static class ReportThread extends Thread {
	ReportThread() { }
	public void run() {
	    long until = System.currentTimeMillis() + (REPORT * 1000);
	    /* Round to a multiple of the report interval */
	    until -= until % (REPORT * 1000);
	    while (true) {
		long now = System.currentTimeMillis();
		if (now < until) {
		    try {
			Thread.sleep(until - now);
		    } catch (InterruptedException e) {
		    }
		    continue;
		}
		if (logger.isLoggable(Level.INFO)) {
		    logger.log(Level.INFO,
			       "rcv/sec=" + (receivedMessage / REPORT) +
			       " join/sec=" + (joinChannel / REPORT) +
			       " leave/sec=" + (leaveChannel / REPORT) +
			       " send/sec=" + (sendChannel / REPORT) +
			       " get/sec=" + (getItem / REPORT) +
			       " set/sec=" + (setItem / REPORT));
		}
		receivedMessage = 0;
		joinChannel = 0;
		leaveChannel = 0;
		sendChannel = 0;
		getItem = 0;
		setItem = 0;
		until += (REPORT * 1000);
	    }
	}
    }
}
