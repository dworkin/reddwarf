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

package com.sun.sgs.test.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.io.AcceptorListener;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;

/**
 * A test harness for the server {@code Acceptor} code.
 */
public class ServerTest implements ConnectionListener {

    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final String DEFAULT_PORT = "5150";
    
    private Acceptor<SocketAddress> acceptor;
    private int numConnections;

    /** Default constructor */
    public ServerTest() { }

    /** Starts the IO {@code ServerTest} harness. */
    public void start() {
        String host = System.getProperty("host", DEFAULT_HOST);
        String portString = System.getProperty("port", DEFAULT_PORT);
        int port = Integer.valueOf(portString);
        try {
            acceptor = new ServerSocketEndpoint(
                    new InetSocketAddress(host, port),
                   TransportType.RELIABLE).createAcceptor();
            acceptor.listen(new AcceptorListener() {
                /**
                 * {@inheritDoc}
                 */
                public ConnectionListener newConnection() {
                    return ServerTest.this;
                }

                /**
                 * {@inheritDoc}
                 */
                public void disconnected() {
                    System.out.println("acceptor shutdown");
                }
            });
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        System.out.println("Listening on " +
                acceptor.getBoundEndpoint().getAddress());
    }

    /** {@inheritDoc} */
    public void connected(Connection conn) {
        synchronized (this) {
            numConnections++;
        }
        System.out.println("ServerTest: connected");
    }

    /** {@inheritDoc} */
    public void disconnected(Connection conn) {
        synchronized (this) {
            numConnections--;
            if (numConnections <= 0) {
                acceptor.shutdown();
            }
            this.notifyAll();
        }
    }

    /** {@inheritDoc} */
    public void exceptionThrown(Connection conn, Throwable exception) {
        System.out.print("ServerTest: exceptionThrown ");
        exception.printStackTrace();
    }

    /** {@inheritDoc} */
    public void bytesReceived(Connection conn, byte[] message) {
        byte[] buffer = new byte[message.length]; 
        System.arraycopy(message, 0, buffer, 0, message.length); 
        try {
            conn.sendBytes(buffer);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        
    }

    /**
     * Runs the IO server test.
     *
     * @param args the commandline arguments
     */
    public final static void main(String[] args) {
        ServerTest server = new ServerTest();
        synchronized(server) {
            server.start();
            try {
                server.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
