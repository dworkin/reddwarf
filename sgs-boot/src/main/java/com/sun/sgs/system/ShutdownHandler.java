/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.system;

import java.io.Closeable;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This class is a simple socket server that waits for incoming connections
 * on localhost on the given port number.  Any incoming connections spawn
 * a new thread and will initiate a clean shutdown of the Project Darkstar
 * server if a {@link BootEnviroment#SHUTDOWN_COMMAND} command is received.
 */
class ShutdownHandler implements Runnable {
    
    private static final Logger logger = 
            Logger.getLogger(ShutdownHandler.class.getName());
    
    private Process p;
    private final ServerSocket listen;
    private final Set<SocketListener> currentListeners;
    
    /**
     * Constructs a new {@code ShutdownHandler} that will
     * open a {@link ServerSocket} on localhost on the given port
     * 
     * @param p the child process to destroy upon shutdown
     * @param port the port number to listen on
     */
    public ShutdownHandler(int port) 
            throws IOException {
        this.currentListeners = new HashSet<SocketListener>();
        try {
            listen = new ServerSocket(port, -1, InetAddress.getByName(null));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to listen on port : " + port, e);
            throw e;
        }
        
        //add shutdown hook to handle shutting down the subprocess when
        //receiving a Ctrl-C
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                ShutdownHandler.this.shutdown();
            }
        });
    }
    
    /**
     * Set the {@code Process} that this handler will destroy upon
     * receiving a {@link BootEnvironment#SHUTDOWN_COMMAND} command.
     * 
     * @param p the {@code Process} to destroy
     */
    public synchronized void setProcess(Process p) {
        this.p = p;
    }
    
    /**
     * This method should be called upon receiving a
     * {@link BootEnvironment#SHUTDOWN_COMMAND} command from a connected 
     * {@code Socket}.
     * This method destroys the {@code Process} set via the 
     * {@link #setProcess(java.lang.Process)} method, closes the incoming
     * {@code ServerSocket} that is listening for incoming shutdown connections,
     * and closes any already connected {@code Socket} objects.
     */
    private synchronized void shutdown() {
        logger.log(Level.FINE, "Shutting down...");
        
        //destroy the SGS process
        if (p != null) {
            p.destroy();
            ShutdownHandler.close(p.getOutputStream());
            ShutdownHandler.close(p.getInputStream());
            ShutdownHandler.close(p.getErrorStream());
        }
        p = null;
        
        close();
    }
    
    /**
     * Close the incoming socket and any already connected sessions.
     */
    synchronized void close() {
        //shutdown the incoming server socket
        if (!listen.isClosed()) {
            ShutdownHandler.close(listen);
        }
        
        //shutdown any other connected sessions
        for (SocketListener connection : currentListeners) {
            connection.close();
        }
        currentListeners.clear();
    }

    /**
     * Waits for incoming connections on this {@code ShutdownHandler}'s 
     * open {@code ServerSocket} and spawns a new {@link SocketListener} thread
     * for each incoming connection.
     * 
     * @throws IllegalStateException if no {@code Process} has been set via
     *         the {@link #setProcess(java.lang.Process)} method.
     */
    @Override
    public void run() {
        synchronized (this) {
            if (p == null) {
                throw new IllegalStateException("ShutdownHandler cannot be " +
                        "started before Process is set");
            }
        }
        
        while (true) {
            try {
                Socket incoming = listen.accept();
                SocketListener handle = new SocketListener(incoming);
                currentListeners.add(handle);
                new Thread(handle).start();
            } catch (IOException e) {
                logger.log(Level.FINEST, 
                           "Shutdown socket server closed", e);
                ShutdownHandler.close(listen);
                break;
            }
        }
    }
    
    /**
     * A simple listener that monitors a connected {@code Socket} for a
     * {@link BootEnvironment#SHUTDOWN_COMMAND} command.
     */
    private class SocketListener implements Runnable {
        private Socket socket;

        /**
         * Creates a new {@code SocketListener} for the given {@code Socket}.
         * 
         * @param socket the connected {@code Socket}
         */
        public SocketListener(Socket socket) {
            this.socket = socket;
        }

        /**
         * Monitors the connected socket for a 
         * {@link BootEnvironment#SHUTDOWN_COMMAND} command.
         * If the command is received, this method closes the socket
         * server and initiates a complete shutdown of the Project Darkstar
         * server.
         */
        @Override
        public void run() {
            BufferedReader in = null;
            try {
                in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String inputLine = "";
                while ((inputLine = in.readLine()) != null) {
                    if (inputLine.equals(BootEnvironment.SHUTDOWN_COMMAND)) {
                        shutdown();
                        break;
                    }
                }
            } catch (IOException e) {
                logger.log(Level.FINEST, 
                           "Shutdown socket closed", e);
            } finally {
                currentListeners.remove(this);
                ShutdownHandler.close(in);
                ShutdownHandler.close(socket);
            }
        }
        
        /**
         * Closes this thread's open {@link Socket}.
         */
        public void close() {
            ShutdownHandler.close(socket);
        }
    }
    
    /**
     * Convenience method to force close a {@code Closeable} object, ignoring
     * any {@code Exception} that is thrown.
     * 
     * @param c object to close
     */
    private static void close(Closeable c) {
        try {
            c.close();
        } catch (IOException ignore) {
            logger.log(Level.FINEST, "Error closing object", ignore);
        }
    }
    
    /**
     * Convenience method to force close a {@code Socket} object, ignoring
     * any {@code Exception} that is thrown.
     * 
     * @param s socket to close
     */
    private static void close(Socket s) {
        try {
            s.close();
        } catch (IOException ignore) {
            logger.log(Level.FINEST, "Error closing object", ignore);
        }
    }
    
    /**
     * Convenience method to force close a {@code ServerSocket} object, ignoring
     * any {@code Exception} that is thrown.
     * 
     * @param s socket to close
     */
    private static void close(ServerSocket s) {
        try {
            s.close();
        } catch (IOException ignore) {
            logger.log(Level.FINEST, "Error closing object", ignore);
        }
    }

}
