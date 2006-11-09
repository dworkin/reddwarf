package com.sun.sgs.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.mina.common.IoAcceptor;

import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOAcceptor;

/**
 * This is an Apache Mina implementation of an {@code IOAcceptor}.  This 
 * implementation uses a Mina {@link IoAcceptor} to accept incoming 
 * connections.  This implementation is thread-safe.
 * 
 * @author  Sten Anderson
 * @version 1.0
 */
public class SocketAcceptor implements IOAcceptor {
    
    private IoAcceptor acceptor;
    private List<SocketAddress> boundAddresses;
    
    /**
     * Constructs a {@code SocketAcceptor} with one separate thread for
     * processing IO.
     *
     */
    public SocketAcceptor() {
        this(1, Executors.newSingleThreadExecutor());
    }
    
    /**
     * Constructs a {@code SocketAcceptor} using the given {@link Executor}
     * for thread management.
     * 
     * @param executor          An {@code Executor} for controlling thread usage.
     */
    public SocketAcceptor(Executor executor) {
        this(1, executor);
    }
    
    /**
     * Constructs a {@code SocketAcceptor} using the given {@link Executor}
     * for thread management.  The {@code numProcessors} parameter refers to
     * the number of {@code SocketIOProcessors} to initially create.  
     * A {@code SocketIOProcessor} is a Mina internal implementation detail
     * that controls the internal processing of the IO.  It is exposed here
     * to allow clients the option to configure this value.  
     * 
     * @param numProcessors             the number of processors for the 
     *                                  underlying Mina acceptor to create
     * 
     * @param executor                  An {@code Executor} for controlling
     *                                  thread usage.                          
     */
    public SocketAcceptor(int numProcessors, Executor executor) {
        acceptor = new org.apache.mina.transport.socket.nio.SocketAcceptor(
                                numProcessors, new ExecutorAdapter(executor));
        boundAddresses = new LinkedList<SocketAddress>();
    }

    /**
     * {@inheritDoc}
     */
    public void listen(SocketAddress address, AcceptedHandleListener listener) 
                                                        throws IOException {
        acceptor.bind(address, new AcceptedHandleAdapter(listener));
        synchronized (boundAddresses) {
            boundAddresses.add(address);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public void unbind(SocketAddress address) {
        synchronized(boundAddresses) {
            if (boundAddresses.contains(address)) {
                acceptor.unbind(address);
                boundAddresses.remove(address);
            }
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public Collection<SocketAddress> listAddresses() {
        return Collections.unmodifiableCollection(boundAddresses);
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        acceptor.unbindAll();
        synchronized (boundAddresses) {
            boundAddresses.clear();
        }
    }

}
