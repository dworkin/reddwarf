/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
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
 * --
 */

package com.sun.sgs.test.client.simple;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.Properties;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

/**
 * A basic test harness for the Client API.
 */
public class ClientTest implements SimpleClientListener
{
    private SimpleClient client;

    /**
     * Construct the client harness.
     */
    public ClientTest() {
        client = new SimpleClient(this);
    }

    private void start() {
        Properties props = new Properties();
        props.put("host", "");
        props.put("port", "10002");

        try {
            client.login(props);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Run the ClientTest.
     *
     * @param args command-line arguments
     */
    public final static void main(String[] args) {
        ClientTest client = new ClientTest();
        synchronized (client) {
            client.start();
            try {
                client.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void disconnected(boolean graceful, String reason) {
        System.out.println("disconnected graceful: " + graceful);
        System.exit(0);
    }

    /**
     * {@inheritDoc}
     */
    public PasswordAuthentication getPasswordAuthentication() {
        PasswordAuthentication auth = new PasswordAuthentication("guest",
                new char[] { 'g', 'u', 'e', 's', 't' });
        return auth;
    }

    /**
     * {@inheritDoc}
     */
    public void loggedIn() {
        System.out.println("Logged In");
        try {
            ByteBuffer message = ByteBuffer.wrap("Join Channel".getBytes());
            client.send(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void loginFailed(String reason) {
        System.out.println("Log in Failed " + reason);
    }

    /**
     * {@inheritDoc}
     */
    public void reconnected() {
        // TODO
    }

    /**
     * {@inheritDoc}
     */
    public void reconnecting() {
        // TODO
    }

    /**
     * {@inheritDoc}
     */
    public ClientChannelListener joinedChannel(ClientChannel channel) {
	return null;
    }

   /**
    * {@inheritDoc}
    */
    public void receivedMessage(ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        System.out.println("Received general server message size "
                + bytes.length + " "
                + new String(bytes));
    }
}
