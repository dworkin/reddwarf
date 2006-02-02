package com.sun.gi.utils.nio;

public interface NIOSocketManagerListener {

    /**
     * Notify this listener that a new incoming connection
     * has been accepted successfully.
     *
     * @param connection the newly-accepted NIOConnection object
     */
    public void newConnection(NIOConnection connection);

    /**
     * Notify this listener that the connection attempt
     * was successful.
     *
     * @param connection the newly-connected NIOConnection object
     */
    public void connected(NIOConnection connection);

    /**
     * Notify this listener that the connection attempt failed
     *
     * @param connection the NIOConnection object that has failed
     *			 to connect
     */
    public void connectionFailed(NIOConnection connection);
}
