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
 * All other callbacks are forwarded to the associated {@code IOHandle} 
 * from the given {@code IoSession}.
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
        System.out.println("AcceptedHandleAdapter sessionCreated");
        SocketHandle handle = new SocketHandle();
        handle.setSession(session);
        listener.newHandle(handle);
    }

    public void sessionOpened(IoSession arg0) throws Exception {

    }

    public void sessionClosed(IoSession session) throws Exception {
        SocketHandle handle = (SocketHandle) session.getAttachment();
        handle.getIOHandler().disconnected(handle);
    }

    public void sessionIdle(IoSession arg0, IdleStatus arg1) throws Exception {

    }

    public void exceptionCaught(IoSession session, Throwable throwable)
            throws Exception {

        SocketHandle handle = (SocketHandle) session.getAttachment();
        handle.getIOHandler().exceptionThrown(throwable, handle);
    }

    public void messageReceived(IoSession session, Object message) throws Exception {
        org.apache.mina.common.ByteBuffer buffer = 
                                (org.apache.mina.common.ByteBuffer) message;

        SocketHandle handle = (SocketHandle) session.getAttachment();
        handle.getIOHandler().messageReceived(buffer.buf(), handle);
        
    }

    public void messageSent(IoSession arg0, Object arg1) throws Exception {

    }
    
}
