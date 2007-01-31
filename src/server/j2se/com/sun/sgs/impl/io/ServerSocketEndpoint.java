package com.sun.sgs.impl.io;

import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.filter.executor.ExecutorExecutor;
import org.apache.mina.transport.socket.nio.DatagramAcceptor;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.ServerEndpoint;

/**
 * An implementation of {@link ServerEndpoint} that wraps a
 * local {@link SocketAddress}.
 */
public class ServerSocketEndpoint extends AbstractSocketEndpoint
        implements ServerEndpoint<SocketAddress>
{
    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(
                ServerSocketEndpoint.class.getName()));

    /**
     * Constructs a {@code ServerSocketEndpoint} with the given
     * {@link TransportType}. This is the simplest way to create a
     * {@code ServerSocketEndpoint}. The returned endpoint will use 
     * new daemon threads as necessary for processing events.
     *
     * @param address the socket address to encapsulate
     * @param type the type of transport
     */
    public ServerSocketEndpoint(SocketAddress address, TransportType type) {
        this(address, type, new DaemonExecutor());
    }

    /**
     * Constructs a {@code ServerSocketEndpoint} with the given TransportType
     * using the given {@link Executor} for thread management.
     * 
     * @param address the socket address to encapsulate
     * @param type the type of transport
     * @param executor an {@code Executor} specifying the threading policy
     */
    public ServerSocketEndpoint(SocketAddress address, TransportType type,
            Executor executor) {
        this(address, type, executor, 1);
    }

    /**
     * Constructs a {@code ServerSocketEndpoint} with the given TransportType
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
    public ServerSocketEndpoint(SocketAddress address, TransportType type,
            Executor executor, int numProcessors)
    {
        super(address, type, executor, numProcessors);
    }

    /**
     * {@inheritDoc}
     */
    public Acceptor<SocketAddress> createAcceptor() {
        IoAcceptor minaAcceptor;
        ExecutorExecutor adapter = new ExecutorExecutor(executor);
        if (transportType.equals(TransportType.RELIABLE)) {
            minaAcceptor =
                new org.apache.mina.transport.socket.nio.SocketAcceptor(
                    numProcessors, adapter);
        } else {
            minaAcceptor = new DatagramAcceptor(adapter);
        }
        SocketAcceptor acceptor = new SocketAcceptor(this, minaAcceptor);
        logger.log(Level.FINE, "returning {0}", acceptor);
        return acceptor;
    }
}
