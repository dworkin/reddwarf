package com.sun.sgs.impl.io;

import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoConnector;
import org.apache.mina.transport.socket.nio.DatagramConnector;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.Endpoint;
import com.sun.sgs.io.Connector;

/**
 * An implementation of {@link Endpoint} that wraps a {@link SocketAddress}.
 */
public class SocketEndpoint extends AbstractSocketEndpoint
        implements Endpoint<SocketAddress>
{
    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketEndpoint.class.getName()));

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
     * @param executor an {@code Executor} specifying the threading policy
     */
    public SocketEndpoint(SocketAddress address,
                          TransportType type, Executor executor)
    {
        this(address, type, executor, 1);
    }

    /**
     * Constructs a {@code SocketEndpoint} with the given TransportType
     * using the given {@link Executor} for thread management.
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
    public SocketEndpoint(SocketAddress address, TransportType type,
            Executor executor, int numProcessors)
    {
        super(address, type, executor, numProcessors);
    }

    /**
     * {@inheritDoc}
     */
    public Connector<SocketAddress> createConnector() {
        IoConnector minaConnector;
        if (transportType.equals(TransportType.RELIABLE)) {
            minaConnector =
                new org.apache.mina.transport.socket.nio.SocketConnector(
                    numProcessors, executor);
        } else {
            minaConnector = new DatagramConnector(executor);
        }
        SocketConnector connector = new SocketConnector(this, minaConnector);
        logger.log(Level.FINE, "returning {0}", connector);
        return connector;
    }
}
