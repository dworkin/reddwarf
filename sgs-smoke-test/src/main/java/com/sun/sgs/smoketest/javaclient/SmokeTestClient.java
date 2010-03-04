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

package com.sun.sgs.smoketest.javaclient;


import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *An implementation of the client smoketest for the java client. This program,
 * run in conjunction with the smoketest server, will run a set of basic tests on all
 * of the protocol messages that pass between a client and a server.
 */
public class SmokeTestClient implements SimpleClientListener {

    private SimpleClient myclient;
    private boolean joinChannelPass, receiveMsgPass, logInPass,
            logOutPass;
    private SmokeTestChannelListener mychannel;
    private static String host = "localhost";
    private static String port = "1139";
    private static String loginName;
    private static Properties props;
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(SmokeTestClient.class.getName()));

    /**
     * Initialize the object. This will set all of the test flags to false (indicating
     * that they have not been run) and will create a simple client
     */
    public SmokeTestClient() {
        joinChannelPass = receiveMsgPass = logInPass = logOutPass = false;
        myclient = new SimpleClient(this);
    }

    /**
     * A simple entry point for the smoke test. This method will create a set
     * of properties (which can be over-ridden on the command line), create
     * an instance of a <code>SmokeTestClient</code> object, start the test,
     * and then wait until the program exits
     * @param args Command line arguments that allow setting the host and
     * port of the smokeTestServer. By default, it is assumed that the server is
     * running on the local host, listening on port 1139.
     */
    public final static void main(String[] args) {

        props = buildProperties();
        SmokeTestClient testClient = new SmokeTestClient();
        testClient.start();
        synchronized (testClient) {
            try {
                testClient.wait();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Starts the test. The first test is to attempt to login with the user
     * name "kickme"; this login attempt will be refused, which should
     * cause a call to the {@link #loginFailed] callback method. If the
     * login fails because of an exception in contacting the server, an
     * error message is logged and the program exits with a status of 1,
     * indicating test failure.
     */
    public void start() {
        loginName = "kickme";
        try {
            myclient.login(props);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Exception generated on initial login");
            System.exit(1);
        }
    }

    /**
     * Generate a password authentication object for login. There will be three
     * such objects generated during a (successful) test; each will test a different
     * part of the login protocol. In all cases, the login name is taken from the
     * global variable <code>loginName</code>, and the password is the same as
     * the login name.
     * @return a {@link PasswordAuthentication} authentication object with
     *   a login name and password identical to the contents of <code>loginName</code>
     */
    public PasswordAuthentication getPasswordAuthentication() {
        PasswordAuthentication auth = new PasswordAuthentication(loginName, loginName.toCharArray());
        return auth;
    }

    /**
     * An implementation of the function called when a client is seen by the server to
     * log in. The response for the test is to send a message consisting of "loggedIn:" along with
     * the name of the player who logged in. If this fails, a warning is logged. Calling this
     * will change the value of the flag logInPass from <cde>false</code> to <code>true</code>,
     * and this will be reflected  in the results that are logged at the end of the test.
     */
    public void loggedIn() {
        String msg = "loggedIn:" + loginName;
        logInPass = true;
        try {
            myclient.send(ByteBuffer.wrap(msg.getBytes()));
        } catch (IOException e) {
            logger.log(Level.WARNING, "error sending reply to logged in message");
        }

    }

    /**
     * An implementation of the <code>loginFailed</code> callback, which is called
     * if an attempt to login in over a client session fails for other than security reasons.
     * This is expected to happen if the user name is "kickme", in which case we log
     * that the login failure case has passed. Otherwise, there is a problem with the test,
     * and an error message is logged, and the program ends.
     * @param reason A {@link String} returned from the server giving the reason that
     * the login failed
     */
    public void loginFailed(String reason) {
        if (loginName.equals("kickme")) {
            logger.log(Level.INFO, "Login failure test passed");
        } else {
            logger.log(Level.WARNING, "Unexpected login failure with client name " + loginName);
            logger.log(Level.WARNING, "Failure reason reported " + reason);
            System.exit(1);
        }
        loginName = "discme";
        try {
            myclient.login(props);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Exception generated while logging in as " + loginName);
            System.exit(1);
        }
    }

    /**
     * An implementation of the <code>joinedChannel</code> callback, called when
     * this client is informed that it has joined a channel. For the test, the client needs
     * to send back a message on the joined channel of the form "joinedChannel:" followed
     * by the name of the channel. The method will create a channel listener for this
     * channel, and will set the joinChannelPass flag to true.
     * @param channel the channel joined
     * @return A {@link ClientChannelListern} object that will be called to deal with
     * channel messages.
     */
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        mychannel = new SmokeTestChannelListener(this.myclient);
        String toSend = "joinedChannel:" + channel.getName();
        try {
            channel.send(ByteBuffer.wrap(toSend.getBytes()));
        } catch (IOException e) {
            logger.log(Level.WARNING, "unable to send on newly joined channel" + channel.getName());
        }
        joinChannelPass = true;
        return mychannel;
    }

    /**
     * An implementation of the <code>receivedMessage</code> callback, called when
     * a message is received on the client session. The response depends on the contents
     * of the message. If the message is "logout", the logOutPass flag is set to true, and
     * the {@link SimpleClient#logout} method is called. Otherwise, a message of the
     * form "receivedMessage:" with the message appended is sent back to the server,
     * and the receivedMsgPass flag is set to true.
     * @param message A ByteBuffer containing the message sent by the server. This
     *  will be an array of characters in UTF-8 format
     */
    public void receivedMessage(ByteBuffer message) {
        String prefix = "receivedMessage:";
        String msg = bufferToString(message);

        if (msg.equals("logout")) {
            myclient.logout(false);
            logOutPass = true;
            return;
        }

        try {
            myclient.send(ByteBuffer.wrap((prefix + msg).getBytes()));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to respond to message");
        }
        receiveMsgPass = true;
    }

    /**
     * Stub for testing the reconnecing callback. Currently, the server does not implement
     * reconnection, so this is empty.
     */
    public void reconnecting() {
    }

    /**
     * Stub for testing the reconnected callback. Currently, the server does not implement
     * reconnection, so this is empty.
     */
    public void reconnected() {
    }

    /**
     * An implementation of the <code>disconnected</code> callback, called when the
     * server sends a disconnect message to the client.
     *
     * A disconnect call is expected when the user name is "discme"; in that case
     * the method logs that the disconnect test has been passed, and begins the
     * next step of the smoke test.
     *
     * Any other call indicates that the test is over (either because everything has
     * passed or because something has gone wrong); in that case the method calls
     * the {@link printResults} method and exits, with an exit status that indicates the
     * number of tests that have failed.
     *
     * @param graceful boolean indicating if the disconnect is graceful or forced
     * @param reason A string indicating the reason for the disconnect
     */
    public void disconnected(boolean graceful, String reason) {
        if (loginName.equals("discme")) {
            logger.log(Level.INFO, "Passed disconnection test");
            loginName = "smokeTest";
            try {
                myclient.login(props);
            } catch (IOException e) {
                logger.log(Level.WARNING, "unable to log in for main tests");
            }
        } else {
            System.exit(printResults());
        }
    }

    /**
     * Print out any test failures to the log, and calculate the number of failures that
     * have occurred. Return the number of failures.
     */
    private int printResults() {
        int failures = 0;

        if (!logInPass) {
            logger.log(Level.INFO, "Login test failed to call logged in callback");
            failures++;
        }
        if (!receiveMsgPass) {
            logger.log(Level.INFO, "Smoke test failed in receive message test");
            failures++;
        }
        if (!joinChannelPass) {
            logger.log(Level.INFO, "Smoke test failed in channel join test");
            failures++;
        }
        if (!logOutPass) {
            logger.log(Level.INFO, "Smoke test failed logout test");
            failures++;
        }
        if (!mychannel.getReceiveStatus()) {
            logger.log(Level.INFO, "Smoke test failed channel receive message test");
            failures++;
        }
        if (!mychannel.getChannelLeftStatus()) {
            logger.log(Level.INFO, "Smoke test failed leaving channel test");
            failures++;
        }
        if (failures == 0) {
            logger.log(Level.INFO, "Smoke tests passed");
        }
        return failures;
    }

    /**
     *  Parse any command line arguments to determine the
     * correct host and port for connecting to the smoke test
     * server. If there are no arguments, the default settings
     * will be used
     * @param args The arguments passed in on the command line
     * @return a property object that can be passed in to the
     * {@link SimpleClient.login} method of the {@link SimpleClient}.
     */
    private static Properties buildProperties() {
        if (System.getProperty("host") == null){
            System.setProperty("host", host);
        }
        if (System.getProperty("port") == null){
            System.setProperty("port", port);
        }
        return (System.getProperties());
    }

    /**
     * Print the usage message on the command line. Should
     * only be called if the program is invoked incorrectly. Will
     * print a standard usage message.
     */
    private static void printUse() {
        System.out.println("usage: java smokeTestClient " +
                "[host = hostname] [port = portnum]" +
                "[ -usage]");
    }

    /**
     * Converts a byte buffer into a string using UTF-8 encoding. Used
     * by both this class and the {@link SmokeTestChannelListener}
     */
    static String bufferToString(ByteBuffer buffer) {
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
     * Channel listener for the smoke test client
     */
   private class SmokeTestChannelListener implements ClientChannelListener {

    private SimpleClient client;
    private boolean receiveMsgPass, leftChannelPass;
   /* private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(SmokeTestChannelListener.class.getName()));*/

    public SmokeTestChannelListener(SimpleClient withClient) {
        client = withClient;
        receiveMsgPass = leftChannelPass = false;
    }

    /**
     * Callback for when a message is received over a channel. The callback will
     * construct a message made up of "receivedChannelMessage:", the name of the
     * channel, and the message content that was sent, and send this back on the
     * channel itself. It will also set the receiveMsgPass flag to true, showing that
     * this part of the test has been triggered.
     * @param channel The channel on which the message was received, and on
     *      which the reply will be sent
     * @param message the message sent over the channel
     */
    public void receivedMessage(ClientChannel channel, ByteBuffer message) {
        String sendMsg = "receivedChannelMessage:" + channel.getName() + " ";
        String msg = SmokeTestClient.bufferToString(message);
        sendMsg = sendMsg + msg;
        receiveMsgPass = true;
        try {
            channel.send(ByteBuffer.wrap(sendMsg.getBytes()));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to send response to received channel message on channel " + channel.getName());
        }
    }

    /**
     * Callback triggered when the client leaves a channel. The client will respond
     * by sending a message of the form "leftChannel:" with the name of the channel
     * back to the server, on the client session for this client. Calling this will set the
     * leftChannel flag to true, showing that this part of the smoke test has been
     * triggered.
     *
     * @param channel The channel that the client has left.
     */
    public void leftChannel(ClientChannel channel) {
        String msg = "leftChannel:" + channel.getName();
        leftChannelPass = true;
        try {
            client.send(ByteBuffer.wrap(msg.getBytes()));
        } catch (IOException e) {
            logger.log(Level.WARNING, "unable to send channel leave confirmation");
        }
    }

    /**
     * Returns a boolean indicating whether or not the {@link receivedMessage} callback
     * has been called.
     * @return true if the callback has been called; false otherwise.
     */
    boolean getReceiveStatus() {
        return receiveMsgPass;
    }

    /**
     * Returns a boolean indicating whether or not the {@link leftChannel} callback has
     * been called.
     * @return true if the callback has been called; false otherwise.
     */
    boolean getChannelLeftStatus() {
        return leftChannelPass;
    }
    }


}
