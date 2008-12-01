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
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Monitors a specific port for incoming connections.
 */
public class ShutdownHandler implements Runnable {
    
    private static final Logger logger = 
            Logger.getLogger(ShutdownHandler.class.getName());
    private static final String SHUTDOWN = "SHUTDOWN";
    
    private Process p;
    private ServerSocket listen;
    private Set<SocketListener> currentListeners;
    
    /**
     * Constructs a new {@code ShutdownHandlerListener} that will
     * wait for incoming connections on the given port
     * 
     * @param p the child process to destroy upon shutdown
     * @param port the port number to listen on
     */
    public ShutdownHandler(int port) 
            throws IOException {
        this.currentListeners = new HashSet<SocketListener>();
        try {
            listen = new ServerSocket(port);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to listen on port : " + port, e);
            throw e;
        }
    }
    
    /**
     * Set the {@code Process} that this handler will destroy upon
     * receiving a {@link ShutdownHandler#SHUTDOWN} command.
     * 
     * @param p the {@code Process} to destroy
     */
    public synchronized void setProcess(Process p) {
        this.p = p;
    }
    
    /**
     * This method should be called upon receiving a
     * {@link ShutdownHandler#SHUTDOWN} command from a connected {@code Socket}.
     * This method destroys the {@code Process} set via the 
     * {@link #setProcess(java.lang.Process)} method, closes the incoming
     * {@code ServerSocket} that is listening for incoming shutdown connections,
     * and closes any already connected {@code Socket} objects.
     */
    private synchronized void shutdown() {
        //destroy the SGS process
        if (p != null) {
            ShutdownHandler.close(p.getOutputStream());
            ShutdownHandler.close(p.getInputStream());
            ShutdownHandler.close(p.getErrorStream());
            p.destroy();
        }
        
        //shutdown the incoming server socket
        ShutdownHandler.close(listen);
        
        //shutdown any other connected sessions
        for(SocketListener connection : currentListeners) {
            connection.close();
        }
    }

    /**
     * Opens a {@code ServerSocket} on this {@code ShutdownHandler}'s port and
     * waits for incoming connections.
     * 
     * @throws IllegalStateException if no {@code Process} has been set via
     *         the {@link #setProcess(java.lang.Process)} method.
     */
    @Override
    public void run() {
        synchronized(this) {
            if(p == null) {
                throw new IllegalStateException("ShutdownHandler cannot be " +
                        "started before Process is set");
            }
        }
        
        while(true) {
            try {
                Socket incoming = listen.accept();
                SocketListener handle = new SocketListener(incoming);
                currentListeners.add(handle);
                new Thread(handle).start();
            } catch (IOException e) {
                ShutdownHandler.close(listen);
            }
        }
    }
    
    private class SocketListener implements Runnable {
        private Socket socket;

        public SocketListener(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            BufferedReader in = null;
            try {
                in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String inputLine = "";
                while ((inputLine = in.readLine()) != null) {
                    if (inputLine.equals(SHUTDOWN)) {
                        shutdown();
                        break;
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, 
                           "Unable to read from incoming socket", e);
            } finally {
                currentListeners.remove(this);
                ShutdownHandler.close(in);
                ShutdownHandler.close(socket);
            }
        }
        
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
