package com.sun.sgs.impl.io;

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoSession;

import com.sun.sgs.io.AcceptedHandleListener;
import com.sun.sgs.io.IOHandle;

/**
 * This class functions as an adapter between the Mina {@link IoHandler}
 * callbacks for new, incoming connections, and the higher level framework's 
 * {@link AcceptedHandleListener}.  As new connections come in, the 
 * {@code AcceptedHandleListener.newHandle} method is called.
 * <p>
 * All other callbacks are forwarded to the {@code IOHandler} associated with 
 * the {@code IOHandle} from the given {@code IoSession}.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class AcceptedHandleAdapter implements IoHandler {
    
    private AcceptedHandleListener listener;
    
    /**
     * Constructs a new {@code AcceptedHandleAdapter} with an 
     * {@code AcceptedHandleListener} that will be notified as new 
     * connections arrive.
     * 
     * @param listener  the listener to be notified of incoming connections.
     */
    public AcceptedHandleAdapter(AcceptedHandleListener listener) {
        this.listener = listener; 
    }
    
    // Mina IoHandler callbacks 

    /**
     * As new {@code IoSession}s come in, set up a {@code SocketHandle} and 
     * notify the associated {@code AcceptedHandleListener}.
     */
    public void sessionCreated(IoSession session) throws Exception {
        SocketHandle handle = new SocketHandle();
        handle.setSession(session);
        listener.newHandle(handle);
    }

    /**
     * Fired by Mina when an {@code IoSession} disconnects.
     * 
     * @param session           the session that disconnected.
     */
    public void sessionClosed(IoSession session) throws Exception {
        SocketHandle handle = (SocketHandle) session.getAttachment();
        handle.getIOHandler().disconnected(handle);
    }

    /**
     * This call-back is fired when there is an exception somewhere in Mina's
     * framework.  The exception is forwarded onto the {@code IOHandler} 
     * associated with the session. 
     * 
     * @param session           the session where the exception occurred
     * @param throwable         the exception
     */
    public void exceptionCaught(IoSession session, Throwable throwable)
            throws Exception {

        SocketHandle handle = (SocketHandle) session.getAttachment();
        handle.getIOHandler().exceptionThrown(throwable, handle);
    }

    /**
     * Fired by Mina when a new message is received on a session.  The message
     * is translated into a {@code java.nio.ByteBuffer} and sent along on the
     * associated {@code IOHandler.messageReceived} callback.
     */
    public void messageReceived(IoSession session, Object message) throws Exception {
        org.apache.mina.common.ByteBuffer minaBuffer = 
                                (org.apache.mina.common.ByteBuffer) message;

        SocketHandle handle = (SocketHandle) session.getAttachment();
        java.nio.ByteBuffer nioBuffer = 
                        java.nio.ByteBuffer.allocate(minaBuffer.remaining());
        nioBuffer.put(minaBuffer.buf());
        nioBuffer.flip();
        
        handle.getIOHandler().messageReceived(nioBuffer, handle);
        
    }

    // not used
    public void messageSent(IoSession arg0, Object arg1) throws Exception {}
    
    // not used
    public void sessionIdle(IoSession arg0, IdleStatus arg1) throws Exception {}
    
    // not used
    public void sessionOpened(IoSession arg0) throws Exception {}
    
}
