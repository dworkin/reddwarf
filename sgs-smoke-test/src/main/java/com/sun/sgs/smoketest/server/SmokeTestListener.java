/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
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
 *
 */

package com.sun.sgs.smoketest.server;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

/**
 * Client session listener for the SmokeTest application.
 * 
 * @author Justin
 * 
 */
public class SmokeTestListener implements Serializable,
        ClientSessionListener, ChannelListener, ManagedObject
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger = Logger
            .getLogger(SmokeTestListener.class.getName());

    /** The session this {@code ClientSessionListener} is listening to. */
    private final ManagedReference<ClientSession> sessionRef;

    /** The session name */
    private final String sessionName;
    
    /** The name of a channel that's created whenever a user is logged in. */
    private final String channelName;

    /**
     * This State enum defines the order in which events should occur. If an
     * event occurs out of order then the test will fail, and the failure state
     * will be logged.
     * 
     * NOTE: Any states following the DONE state are not yet implemented.
     */
    private enum State {
        INIT, LOGIN, SEND_SESSION_MSG, JOIN_CHANNEL, SEND_CHANNEL_MSG, 
        LEAVE_CHANNEL, LOGOUT, DONE, 
        RECONNECT, RECONNECTING, LOGIN_REDIRECT;

        /** Get the next state, but stop at DONE */
        public State next()
        {
            if (this == DONE)
                return DONE;
            return State.values()[this.ordinal() + 1];
        }

        /** Get the previous state, but stop at INIT*/
        public State previous(){
            if (this == INIT)
                return INIT;
            return State.values()[this.ordinal() - 1];
        }
    }

    /** The current state. */
    private State curState = State.INIT;

    /** Test messages */
    private static final String TEST_SESSION_MSG = " session msg1\\!";
    private static final String TEST_CHANNEL_MSG = " channel 2msg/!";
    private static final String LOGOUT_MSG = "logout";
    /* The number of tests that have failed*/
    private static int testsFailed = 0;

    /**
     * Session listener that handles this single session. A channel is created
     * for use by this session. To start the smoke test, the client must send
     * the string "start". Before the test is activated, this listener will
     * behave similar to the HelloChannels server.
     * 
     * If a client with the name 'discme' logs in, then it will be immediately
     * be issued a forced disconnect.
     * 
     * @param session
     */
    public SmokeTestListener(ClientSession session)
    {
        if (session == null) {
            throw new NullPointerException("null session");
        }

        DataManager dataMgr = AppContext.getDataManager();
        sessionRef = dataMgr.createReference(session);
        sessionName = session.getName();
        channelName = "CHAN_"+sessionName;

        ChannelManager channelMgr = AppContext.getChannelManager();
        channelMgr.createChannel(channelName, this, Delivery.RELIABLE);
        performNextStep();
        
        // forced disconnect
        if (sessionName.toLowerCase().equals("discme")) {
            AppContext.getDataManager().removeObject(getSession());
        }
    }

    /**
     * Increments the current state to the next state and performs the 
     * server-side actions that trigger the required behaviours in the client.
     */
    private void performNextStep()
    {
        if (curState == State.DONE)
            return;

        if (curState == State.INIT)
            testsFailed = 0;
        
        curState = curState.next();
        ClientSession session = getSession();

        switch (curState) {

        case LOGIN:
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, 
                        "\n\n[[Client \"{0}\" Smoke Test Beginning]]\n",
                        sessionName);
            }
            break;

        case SEND_SESSION_MSG:
            session.send(ByteBuffer.wrap(TEST_SESSION_MSG.getBytes()));
            break;

        case JOIN_CHANNEL:
            AppContext.getChannelManager().getChannel(channelName)
                    .join(session);
            break;

        case SEND_CHANNEL_MSG:
            AppContext.getChannelManager().getChannel(channelName)
                    .send(session,
                            ByteBuffer.wrap(TEST_CHANNEL_MSG.getBytes()));
            break;

        case LEAVE_CHANNEL:
            AppContext.getChannelManager().getChannel(channelName)
                    .leave(session);
            break;
            
        case LOGOUT:
            session.send(ByteBuffer.wrap(LOGOUT_MSG.getBytes()));
            break;

        case DONE:
            if (logger.isLoggable(Level.INFO)) {
                if (testsFailed == 0){
                    logger.log(Level.INFO,
                        "\n\n[[Client \"{0}\" Passed Smoke Test Successfully]]\n",
                        sessionName);
                } else {
                    logger.log(Level.INFO,
                            "\n\n[[Client \"{0}\" Failed " + testsFailed + " tests\n", sessionName);
                }
            }
            break;

        default:
            fail("Invalid step: " + curState);
        }
    }

    /** 
     * Converts a byte buffer into a string using UTF-8 encoding. 
     */
    private static String bufferToString(ByteBuffer buffer)
    {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.rewind();
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Validates whether the message returned by the client matches the
     * expected message.
     * 
     * @param message the message received from the client
     * @param expectedMsg the expected message
     */
    private void validateMsg(ByteBuffer message, String expectedMsg)
    {
        String msg = bufferToString(message);
        if (!msg.equals(expectedMsg)){
            fail("\n*** Expected: " + expectedMsg + 
                 "\n*** Received: " + msg + "\n");
        }
    }

    /**
     * Log the smoke test failure and disconnect the client. A failure could
     * be due, among other cases, to an incorrect message being sent by the 
     * client, or a message arriving out of order.
     * 
     * @param errorMsg
     */
    private void fail(String errorMsg)
    {
        logger.log(Level.WARNING, "\n\n[[Client " + sessionName
                + " Failed Smoke Test]]: {0}\n", errorMsg);
        testsFailed++;
    }

    /**
     * Returns the session for this listener.
     * 
     * @return the session for this listener
     */
    protected ClientSession getSession()
    {
        return sessionRef.get();
    }

    /* ClientListener methods */
    
    /**
     * Log disconnections.
     */
    @Override
    public void disconnected(boolean graceful)
    {
        switch (curState) {

        case LOGOUT:
            if (!graceful)
                fail("Ungraceful logout");
            else
                performNextStep();
            break;

        default:
            if (!sessionName.equals("discme")){
            fail("Unexpected logout");
            /* If the client has logged out, no more tests can
             * be done, so quit, making sure the results are
             * printed
             */
            curState = State.DONE;
            curState = curState.previous();
            performNextStep();
        }
        }
        String grace = graceful ? "graceful" : "forced";
        logger.log(Level.INFO, "User {0} has logged out {1}", new Object[] {
                sessionName, grace });
    }

    /**
     * Validate the message received from the client, and trigger successive
     * steps.
     */
    @Override
    synchronized public void receivedMessage(ByteBuffer message)
    {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Message from {0} in state " + curState
                    + ": " + bufferToString(message), sessionName);
        }
        
        if (curState == State.DONE)
            return;
        
        switch (curState) {

        case INIT:
            fail("Unexpected message during initialization");
            break;

        case LOGIN:
            validateMsg(message, "loggedIn:" + sessionName);
            break;

        case SEND_SESSION_MSG:
            validateMsg(message, "receivedMessage:" + TEST_SESSION_MSG);
            break;

        case JOIN_CHANNEL:
            validateMsg(message, "joinedChannel:" + channelName);
            break;

        case LEAVE_CHANNEL:
            validateMsg(message, "leftChannel:" + channelName);
            break;

        default:
            fail("Invalid state: " + curState);

        }

        performNextStep();
    }

    /* ChannelListener Methods */

    /**
     * Validate the channel message received from the client, and trigger
     * successive steps.
     */
    @Override
    synchronized public void receivedMessage(Channel channel,
            ClientSession session, ByteBuffer message)
    {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO,
                    "Channel message from {0} on channel {1} in state "
                            + curState + ": " + bufferToString(message),
                    new Object[] { session.getName(), channel.getName() });
        }

        switch (curState) {

        case JOIN_CHANNEL:
            validateMsg(message, "joinedChannel:" + channelName);
            break;

        case SEND_CHANNEL_MSG:
            validateMsg(message, "receivedChannelMessage:"
                    + channelName + " " + TEST_CHANNEL_MSG);
            break;

        default:
            fail("Invalid channel state: " + curState);

        }

        performNextStep();
    }
}
