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

package com.sun.sgs.impl.profile.util;

import java.io.IOException;
import java.io.OutputStream;

import java.net.ServerSocket;
import java.net.Socket;

import java.util.HashSet;
import java.util.Iterator;


/**
 * A very simple utility class used to report on a socket. This allows
 * any number of clients to connect and listen for text encoded in the
 * platform's default character set encoding. Note that this is
 * intended only as a means for reporting in testing and development
 * systems. In deployment, a more robust mechanism should be used.
 */
public final class NetworkReporter {

    // the set of connected clients
    private HashSet<Socket> listeners;
    private final ServerSocket serverSocket;
    private final Thread reporterThread;

    /**
     * Creates an instance of <code>NetworkReporter</code>.
     *
     * @param port the port on which to listen for client connections
     *
     * @throws IOException if the server socket cannot be created
     */
    public NetworkReporter(int port)
        throws IOException
    {
        listeners = new HashSet<Socket>();
        serverSocket = new ServerSocket(port);
        reporterThread = new Thread(new NetworkReporterRunnable());
        reporterThread.start();
    }

    /**
     * Reports the provided message to all connected clients. If the
     * message cannot be sent to a given client, then that client is
     * dropped from the set of connected clients.
     *
     * @param message the message to send
     */
    public synchronized void report(String message) {
        Iterator<Socket> it = listeners.iterator();
        while (it.hasNext()) {
            Socket socket = it.next();
            try {
                OutputStream stream = socket.getOutputStream();
                stream.write(message.getBytes());
                stream.flush();
            } catch (IOException ioe) {
                it.remove();
            }
        }
    }

    /**
     * Cleans up.
     */
    public void shutdown() {
        try {
            reporterThread.interrupt();
            serverSocket.close();
        } catch (IOException ioe) {
            // do nothing
        }
    }
    /**
     * A private class used to run the long-lived server task. It simply
     * listens for connecting clients, and adds them to the set of connected
     * clients. If accepting a client fails, then the server socket is closed.
     */
    private class NetworkReporterRunnable implements Runnable {
        NetworkReporterRunnable() { }
        public void run() {
            try {
                while (true) {
                    synchronized (this) {
                        listeners.add(serverSocket.accept());
                    }
                }
            } catch (IOException e) {
                try {
                    serverSocket.close();
                } catch (IOException ioe) { }
            }
        }
    }

}
