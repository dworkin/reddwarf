package com.sun.sgs.io.mina;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.apache.mina.common.IoFuture;

import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.io.IOConnector;

/**
 * This is a socket-based implementation of an {@code IOConnector} using
 * the Apache Mina framework for the underlying transport.  It uses an
 * {@link org.apache.mina.transport.socket.nio.SocketConnector} to initiate
 * connections on remote hosts.  This implementation is thread-safe.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public class SocketConnector implements IOConnector {
    
    private org.apache.mina.transport.socket.nio.SocketConnector connector;

    public SocketConnector() {
        connector = new org.apache.mina.transport.socket.nio.SocketConnector();
    }
    
    /**
     * {@inheritDoc}
     */
    public IOHandle connect(InetAddress address, int port, IOHandler handler) {
        InetSocketAddress socketAddress = new InetSocketAddress(address, port);
        
        SocketHandle handle = new SocketHandle();
        handle.setIOHandler(handler);
        IoFuture future = connector.connect(socketAddress, new SocketHandler(handler));
        future.addListener(handle);
        
        // avoid a race condition b/w the time of getting a reference to the
        // IoFuture and signing the handle up as a listener.
        if (future.isReady()) {
            handle.operationComplete(future);
        }
        
        return handle;
    }


    public void shutdown() {
    }

}
