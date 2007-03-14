/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.ResourceCoordinator;

import java.io.IOException;
import java.io.OutputStream;

import java.net.ServerSocket;
import java.net.Socket;

import java.util.HashSet;


/**
 * A very simple utility class used to report on a socket. This allows
 * any number of clients to connect and listen for text encoded in the
 * platform's default character set encoding. Note that this is
 * intended only as a means for reporting in testing and development
 * systems. In deployment, a more robust mechanism should be used.
 */
public class NetworkReporter {

    // the set of connected clients
    private HashSet<Socket> listeners;

    /**
     * Creates an instance of <code>NetworkReporter</code>.
     *
     * @param port the port on which to listen for client connections
     * @param resourceCoordinator the <code>ResourceCoordinator</code> used
     *                            to start the server listener task
     *
     * @throws IOException if the server socket cannot be created
     */
    NetworkReporter(int port, ResourceCoordinator resourceCoordinator)
        throws IOException
    {
        listeners = new HashSet<Socket>();
        resourceCoordinator.
            startTask(new NetworkReporterRunnable(new ServerSocket(port)),
                      null);
    }

    /**
     * Reports the provided message to all connected clients. If the
     * message cannot be sent to a given client, then that client is
     * dropped from the set of connected clients.
     *
     * @param message the message to send
     */
    public synchronized void report(String message) {
        for (Socket socket : listeners) {
            try {
                OutputStream stream = socket.getOutputStream();
                stream.write(message.getBytes());
                stream.flush();
            } catch (IOException ioe) {
                listeners.remove(socket);
            }
        }
    }

    /**
     * A private class used to run the long-lived server task. It simply
     * listens for connecting clients, and adds them to the set of connected
     * clients. If accepting a client fails, then the server socket is closed.
     */
    private class NetworkReporterRunnable implements Runnable {
        private final ServerSocket serverSocket;
        NetworkReporterRunnable(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }
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
                } catch (IOException ioe) {}
            }
        }
    }

}
