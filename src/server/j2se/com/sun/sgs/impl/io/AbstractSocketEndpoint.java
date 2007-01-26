package com.sun.sgs.impl.io;

import java.net.SocketAddress;
import java.util.concurrent.Executor;

abstract class AbstractSocketEndpoint {

    /** The socket address this Endpoint encapsulates. */
    protected final SocketAddress address;

    /** The transport type this Endpoint encapsulates. */
    protected final TransportType transportType;

    /** The {@code Executor} used by this Endpoint's connector or acceptor. */
    protected final Executor executor;

    /** A MINA configuration parameter whose value may affect performance. */
    protected final int numProcessors;

    /**
     * Constructs an {@code AbstractSocketEndpoint} with the given
     * TransportType using the given {@link Executor} for thread management.
     * The {@code numProcessors} parameter refers to the number of
     * MINA {@code SocketIOProcessors} to initially create.
     * {@code numProcessors} must be greater than 0.
     * <p>
     * (Note: A {@code SocketIOProcessor} is a MINA implementation detail that
     * controls the internal processing of the IO. It is exposed here to allow
     * clients the option to configure this value, which may aid
     * performance tuning).
     *
     * @param address the socket address to encapsulate
     * @param type the type of transport
     * @param executor An {@code Executor} specifying the threading policy
     * @param numProcessors the number of processors available on this
     *        system.  This value must be greater than 0.
     *
     * @throws IllegalArgumentException if {@code numProcessors} is
     *         zero or negative.
     */
    protected AbstractSocketEndpoint(SocketAddress address,
            TransportType type, Executor executor, int numProcessors)
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
     * Returns the {@link SocketAddress} encapsulated by this
     * {@code SocketEndpoint}.
     *
     * @return the {@code SocketAddress} encapsulated by this
     *         {@code SocketEndpoint}
     */
    public SocketAddress getAddress() {
        return address;
    }

    /**
     * Returns the {@link TransportType} encapsulated by this
     * {@code SocketEndpoint}.
     *
     * @return the {@code TransportType} encapsulated by this
     *         {@code SocketEndpoint}
     */
    public TransportType getTransportType() {
        return transportType;
    }

    /**
     * Returns the {@link Executor} encapsulated by this {@code SocketEndpoint}.
     * 
     * @return the {@code Executor} encapsulated by this {@code SocketEndpoint}
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Returns the number of processors available to the MINA framework.
     * 
     * @return the number of processors available to the MINA framework
     */
    public int getNumProcessors() {
        return numProcessors;
    }

}
