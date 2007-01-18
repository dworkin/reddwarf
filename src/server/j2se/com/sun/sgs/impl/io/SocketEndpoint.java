package com.sun.sgs.impl.io;

import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.filter.executor.ExecutorExecutor;
import org.apache.mina.transport.socket.nio.DatagramAcceptor;
import org.apache.mina.transport.socket.nio.DatagramConnector;

import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.Endpoint;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOConnector;

/**
 * An implementation of {@link Endpoint} that wraps a {@link SocketAddress}.
 * 
 * @author  Sten Anderson
 * @since   1.0
 */
public class SocketEndpoint implements Endpoint<SocketAddress> {
    private static final LoggerWrapper logger = new LoggerWrapper(Logger
            .getLogger(SocketEndpoint.class.getName()));

    /** The socket address this Endpoint encapsulates */
    private SocketAddress address;

    /** The transport type this Endpoint encapsulates */
    private TransportType transportType;

    /** The {@code Executor} used by this Endpoint's connector or acceptor */
    private Executor executor;

    /** A Mina configuration parameter whose value may affect performance. */
    private int numProcessors;

    /**
     * Constructs a {@code SocketEndpoint} with the given
     * {@link TransportType}. This is the simplest way to create a
     * {@code SocketEndpoint}. The returned endpoint will use 
     * new daemon threads as necessary for processing events.
     *
     * @param address the socket address to encapsulate
     * @param type the type of transport
     */
    public SocketEndpoint(SocketAddress address, TransportType type) {
        this(address, type, new DaemonExecutor());
    }

    /**
     * Constructs a {@code SocketEndpoint} with the given TransportType
     * using the given {@link Executor} for thread management.
     * 
     * @param address the socket address to encapsulate
     * @param type the type of transport
     * @param executor An {@code Executor} specifying the threading policy
     */
    public SocketEndpoint(SocketAddress address, TransportType type,
            Executor executor) {
        this(address, type, executor, 1);
    }

    /**
     * Constructs a {@code SocketEndpoint} with the given TransportType
     * using the given {@link Executor} for thread management.
     * The {@code numProcessors} parameter refers to the number of
     * MINA {@code SocketIOProcessors} to initially create.
     * <p>
     * (Note: A {@code SocketIOProcessor} is a MINA implementation detail that
     * controls the internal processing of the IO. It is exposed here to allow
     * clients the option to configure this value, which may aid
     * performance tuning).
     * 
     * @param address the socket address to encapsulate
     * @param type the type of transport
     * @param executor An {@code Executor} specifying the threading policy
     * @param numProcessors the number of processors available on this system
     */
    public SocketEndpoint(SocketAddress address, TransportType type,
            Executor executor, int numProcessors)
    {
        if (address == null || type == null || executor == null) {
            throw new NullPointerException("null arg");
        }
        if (numProcessors <= 0) {
            throw new IllegalArgumentException("numProcessors must be >= 1");
        }
        this.address = address;
        this.transportType = type;
        this.executor = executor;
        this.numProcessors = numProcessors;
    }

    /**
     * {@inheritDoc}
     */
    public IOConnector<SocketAddress> createConnector() {
        IoConnector minaConnector;
        ExecutorExecutor adapter = new ExecutorExecutor(executor);
        if (transportType.equals(TransportType.RELIABLE)) {
            minaConnector = new org.apache.mina.transport.socket.nio.SocketConnector(
                    numProcessors, adapter);
        } else {
            minaConnector = new DatagramConnector(adapter);
        }
        SocketConnector connector = new SocketConnector(this, minaConnector);
        logger.log(Level.FINE, "returning {0}", connector);
        return connector;
    }

    /**
     * {@inheritDoc}
     */
    public IOAcceptor<SocketAddress> createAcceptor() {
        IoAcceptor minaAcceptor;
        ExecutorExecutor adapter = new ExecutorExecutor(executor);
        if (transportType.equals(TransportType.RELIABLE)) {
            minaAcceptor = new org.apache.mina.transport.socket.nio.SocketAcceptor(
                    numProcessors, adapter);
        } else {
            minaAcceptor = new DatagramAcceptor(adapter);
        }
        SocketAcceptor acceptor = new SocketAcceptor(this, minaAcceptor);
        logger.log(Level.FINE, "returning {0}", acceptor);
        return acceptor;
    }

    /**
     * {@inheritDoc}
     */
    public SocketAddress getAddress() {
        return address;
    }

    /**
     * Return the {@link TransportType} encapsulated by this
     * {@code SocketEndpoint}.
     * 
     * @return the {@code TransportType} encapsulated by this
     *         {@code SocketEndpoint}.
     */
    public TransportType getTransportType() {
        return transportType;
    }

    /**
     * Return the {@link Executor} encapsulated by this {@code SocketEndpoint}.
     * 
     * @return the {@link Executor} encapsulated by this {@code SocketEndpoint}.
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Return the number of processors used by this {@code SocketEndpoint}.
     * 
     * @return the number of processors used by this {@code SocketEndpoint}.
     */
    public int getNumProcessors() {
        return numProcessors;
    }
}
