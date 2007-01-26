package com.sun.sgs.impl.io;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;

import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.io.IOAcceptor;
import com.sun.sgs.io.IOConnector;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * An adapter between an Apache {@link IoHandler MINA IoHandler} and the SGS
 * IO framework {@link IOHandler}. SocketHandlers exist one per handle on
 * the client {@link IOConnector} side, and exist one per {@link IOAcceptor}
 * on the server side.
 */
class SocketHandler extends IoHandlerAdapter {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SocketHandler.class.getName()));

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to {@link IOHandler#connected}.
     */
    @Override
    public void sessionOpened(IoSession session) throws Exception
    {
        SocketHandle handle = (SocketHandle) session.getAttachment();
        logger.log(Level.FINE, "opened session {0}", session);
        IOHandler handler = handle.getIOHandler();
        handler.connected(handle);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to {@link IOHandler#disconnected}.
     */
    @Override
    public void sessionClosed(IoSession session) throws Exception
    {
        SocketHandle handle = (SocketHandle) session.getAttachment();
        logger.log(Level.FINE, "disconnect on {0}", handle);
        IOHandler handler = handle.getIOHandler();
        handler.disconnected(handle);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to {@link IOHandler#exceptionThrown}.
     */
    @Override
    public void exceptionCaught(IoSession session, Throwable exception)
        throws Exception
    {
        SocketHandle handle = (SocketHandle) session.getAttachment();
        logger.logThrow(Level.FINEST, exception, "exception on {0}", handle);
        if (handle == null) {
            return;
        }

        IOHandler handler = handle.getIOHandler();
        handler.exceptionThrown(handle, exception);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Obtains the {@link CompleteMessageFilter} for the associated
     * {@link IOHandle}, and forwards incoming data to the filter's
     * {@link CompleteMessageFilter#filterReceive filterReceive} method.
     */
    @Override
    public void messageReceived(IoSession session, Object message)
        throws Exception
    {
        SocketHandle handle = (SocketHandle) session.getAttachment();

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "recv on {0}: {1}", handle, message);
        }

        ByteBuffer minaBuffer = (ByteBuffer) message;

        byte[] array = new byte[minaBuffer.remaining()];
        minaBuffer.get(array);

        handle.getFilter().filterReceive(handle, array);
    }
}
