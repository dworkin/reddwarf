package com.sun.sgs.impl.io;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.mina.common.IoFuture;
import org.apache.mina.util.NewThreadExecutor;

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

    /**
     * Constructs a {@code SocketConnector} with one separate thread for
     * processing IO.
     *
     */
    public SocketConnector() {
        this(1, Executors.newSingleThreadExecutor());
    }
    
    /**
     * Constructs a {@code SocketConnector} using the given {@link Executor}
     * for thread management.
     * 
     * @param executor          An {@code Executor} for controlling thread usage.
     */
    public SocketConnector(Executor executor) {
        this(1, executor);
    }
    
    /**
     * Constructs a {@code SocketConnector} using the given {@link Executor}
     * for thread management.  The {@code numProcessors} parameter refers to
     * the number of {@code SocketIOProcessors} to initially create.  
     * A {@code SocketIOProcessor} is a Mina internal implementation detail
     * that controls the internal processing of the IO.  It is exposed here
     * to allow clients the option to configure this value.  
     * 
     * @param numProcessors             the number of processors for the 
     *                                  underlying Mina connector to create
     * 
     * @param executor                  An {@code Executor} for controlling
     *                                  thread usage.                          
     */
    public SocketConnector(int numProcessors, Executor executor) {
        connector = new org.apache.mina.transport.socket.nio.SocketConnector(
                                  numProcessors, new ExecutorAdapter(executor));
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
