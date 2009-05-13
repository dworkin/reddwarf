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

package com.sun.sgs.example.request.client;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.Random;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;

/**
 *
 */
public abstract class AbstractRequestClient implements SimpleClientListener {
    /** The prefix for properties. */
    private static final String PREFIX =
	"request.client";
    
    /** The application host name. */
    protected static final String HOST =
	System.getProperty(PREFIX + ".host", "localhost");

    /** The application port. */
    protected static final int PORT =
	Integer.getInteger(PREFIX + ".port", 11469);
    
    /** The number of clients run by main. */
    protected static final int CLIENTS =
	Integer.getInteger(PREFIX + ".clients", 1000);
    
    /** The number of seconds between logging performance data. */
    protected static final int REPORT =
	Integer.getInteger(PREFIX + ".report", 10);
    
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
     * The request for a ping message.  There are no arguments associated
     * with this message.
     */
    private static final String PING = "Ping";

    
    /** A random number generator used to random behavior. */
    private static final Random random = new Random();
    
    private static final long LOGIN_MAX_RETRY = 32000;
    
    protected static volatile long connectedClients;
    protected static volatile long loginRequest;
    protected static volatile long loginRequestFail;
    protected static volatile long loginSuccess;
    protected static volatile long loginFail;
    protected static volatile long joinSuccess;
    
    protected static volatile long loginTime;
    protected static volatile long joinTime;
    protected static volatile long pingTime;
    protected static volatile long pingSuccess;
    
    protected static volatile long send;
    protected static volatile long sendFailure;
    
    /** The client used to communicate with the server. */
    protected final SimpleClient simpleClient;
    
    /** The login properties. */
    protected final Properties props;
    
    /** The name of the user. */
    protected final String user = "User-" + random.nextInt(Integer.MAX_VALUE);
    
    private long loginStartTime;
    private Deque<Long> joinStartTimes;
    private Deque<Long> pingStartTimes;
    
    private Map<String, ClientChannel> channels;
    
    private boolean connected = false;
    private long retry = 1000;
    
    /**
     * Creates an instance, and starts a thread to login and perform actions.
     */
    public AbstractRequestClient() {
        props = new Properties();
        props.setProperty("host", HOST);
        props.setProperty("port", String.valueOf(PORT));
        simpleClient = new SimpleClient(this);
        joinStartTimes = new LinkedList<Long>();
        pingStartTimes = new LinkedList<Long>();
        channels = new HashMap<String, ClientChannel>();
	requestLogin();
    }
    
    /**
     * Called at object creation time to initiate the sequence finite
     * state machine that is the client.
     */
    public abstract void loginComplete();
    public abstract void channelJoinComplete();
    public abstract void receivePing();

    public PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(user, new char[0]);
    }
    
    private void requestLogin() {
        loginRequest++;
        loginStartTime = System.currentTimeMillis();
        try {
            simpleClient.login(props);
        } catch (IOException e) {
            loginRequestFail++;
            try {
                retry = Math.min(retry * 2, LOGIN_MAX_RETRY);
                Thread.sleep(retry);
            } catch (Exception ie) {}
            requestLogin();
        }
    }
    
    public void sendPing(String channel) {
        send++;
        pingStartTimes.add(System.currentTimeMillis());
        try {
            if(channel == null) {
                simpleClient.send(stringToBuffer(PING));
            } else {
                channels.get(channel).send(stringToBuffer(PING));
            }
        } catch (IOException ioe) {
            sendFailure++;
            pingStartTimes.removeLast();
        }
    }
    
    public void sendJoinChannel(String name) {
        send++;
        joinStartTimes.add(System.currentTimeMillis());
        try {
            simpleClient.send(stringToBuffer(JOIN_CHANNEL + name));
        } catch (IOException ioe) {
            sendFailure++;
            joinStartTimes.removeLast();
        }
    }
    
    public void sendLeaveChannel(String name) {
        send++;
        try {
            simpleClient.send(stringToBuffer(LEAVE_CHANNEL + name));
        } catch (IOException ioe) {
            sendFailure++;
        }
    }

    public void loggedIn() {
        connected = true;
        connectedClients++;
        loginSuccess++;
        loginTime += (System.currentTimeMillis() - loginStartTime);
        loginComplete();
    }

    public void loginFailed(String reason) {
        loginFail++;
    }
    
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        joinSuccess++;
        joinTime += (System.currentTimeMillis() - joinStartTimes.remove());
        channels.put(channel.getName(), channel);
        channelJoinComplete();
        return new RequestChannelListener();
    }

    public void disconnected(boolean graceful, String reason) {
        if(connected) {
            connected = false;
            connectedClients--;
        } else {
            loginFail++;
            try {
                retry = Math.min(retry * 2, LOGIN_MAX_RETRY);
                Thread.sleep(retry);
            } catch (Exception ie) {}
            requestLogin();
        }
    }

    public void receivedMessage(ByteBuffer message) {
        String response = bufferToString(message);
        if (response.startsWith(PING)) {
            pingSuccess++;
            pingTime += (System.currentTimeMillis() - pingStartTimes.remove());
            receivePing();
        } else {
            throw new RuntimeException("Unexpected message : " + response);
        }
    }

    public void reconnected() {
        // no op
    }

    public void reconnecting() {
        // no op
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
    
    private class RequestChannelListener implements ClientChannelListener  {
        
        public void leftChannel(ClientChannel channel) {
            channels.remove(channel);
        }

        public void receivedMessage(ClientChannel channel, ByteBuffer message) {
            AbstractRequestClient.this.receivedMessage(message);
        }
    }
    
    protected static class StatsThread implements Runnable {

        public void run() {
            long until = System.currentTimeMillis() + (REPORT * 1000);
            while (true) {
                long now = System.currentTimeMillis();
                if (now < until) {
                    try {
                        Thread.sleep(until - now);
                    } catch (InterruptedException e) {
                    }
                    continue;
                }
                
                System.out.println("Connected Clients     : " + connectedClients);
                System.out.println("Login Requests        : " + loginRequest);
                System.out.println(" Login Successes      : " + loginSuccess);
                System.out.println(" Login Failures       : " + loginFail);
                System.out.println(" Avg Login Latency    : " + (double)loginTime/(double)loginSuccess + "ms");
                System.out.println("Channel Joins         : " + joinSuccess);
                System.out.println(" Avg Join Latency     : " + (double)joinTime/(double)joinSuccess + "ms");
                System.out.println("Pings                 : " + pingSuccess);
                System.out.println(" Avg Ping Latency     : " + (double)pingTime/(double)pingSuccess + "ms");
                System.out.println("Message Sends         : " + send);
                System.out.println("Message Send Failures : " + sendFailure);

                until += (REPORT * 1000);
            }
        }
    }

}
