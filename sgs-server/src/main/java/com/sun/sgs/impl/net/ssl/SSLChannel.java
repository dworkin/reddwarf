/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.net.ssl;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;

/*
 * Sets up Server side SSLEngine and has methods to read and write
 * data to and from the network
 */

public class SSLChannel {

    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(SSLChannel.class.getName()));

    // The SSLEngine
    private SSLEngine sslEngine = null;

    // The SSLSession
    private SSLSession sslSession = null;

    // The SSL handshake status
    private HandshakeStatus hsStatus = null;

    // Indicates whether the initial handshake has been completed
    private boolean initialHSComplete = false;

    // The SSL cipher suites enabled on this SSLEngine
    //private final String[] enabledCipherSuites = {};

    // An object used for synchronizing on SSLEngine.unwrap()
    private final Object inLock = new Object();

    // An object used for synchronizing on SSLEngine.wrap()
    private final Object outLock = new Object();

    // The inbound network ByteBuffer
    private ByteBuffer inNetBB = null;

    // The outbound network ByteBuffer
    private ByteBuffer outNetBB = null;

    // An empty ByteBuffer for consuming inbound handshake data
    private ByteBuffer inhsData = null;

    // An empty ByteBuffer for producing outbound handshake data
    private ByteBuffer outhsData = null;

    // The application ByteBuffer size
    private int appBBsize = 4096;

    // The network ByteBuffer size
    private int netBBsize = 4096;

    // The underlying socketchannel
    private final SocketChannel sc;

    // Constructs an instance of this class
    public SSLChannel(SocketChannel sc) {
        this.sc = sc;
        startSSLEngine();
    }

    // Creates the SSLEngine and set to server mode
    // and sets the ByteBuffer sizes
    private void startSSLEngine() {
        
        sslEngine = SSLEngineFactory.getSSLEngine();
        sslEngine.setUseClientMode(false);
        sslSession = sslEngine.getSession();

        netBBsize = sslSession.getPacketBufferSize();
        inNetBB = ByteBuffer.allocate(netBBsize);
        outNetBB = ByteBuffer.allocate(netBBsize);
        outNetBB.position(0);
        outNetBB.limit(0);

        appBBsize = sslSession.getApplicationBufferSize();
        inhsData = ByteBuffer.allocate(appBBsize);
        outhsData = ByteBuffer.allocate(appBBsize);

        hsStatus = HandshakeStatus.NEED_UNWRAP;
    }

    // Performs an SSL handshake
    void doHandshake() throws IOException, SSLException {

        SSLEngineResult result;

        sslEngine.beginHandshake();
        hsStatus = sslEngine.getHandshakeStatus();
        
        while (!initialHSComplete) {

            switch (hsStatus) {

                case NEED_UNWRAP:

                    needIO:
                    while (hsStatus == HandshakeStatus.NEED_UNWRAP) {
                        if (sc.read(inNetBB) == -1) {
                            sslEngine.closeInbound();
                            //return initialHSComplete;
                        }

                        // expected room for unwrap
                        if (inhsData.remaining() < appBBsize) {
                            inhsData = ByteBuffer.allocate(inhsData.capacity() * 2);
                        }
                        inNetBB.flip();
                        synchronized(inLock) {
                            result = sslEngine.unwrap(inNetBB, inhsData);
                            inLock.notifyAll();
                        }
                        inNetBB.compact();

                        hsStatus = result.getHandshakeStatus();

                        switch (result.getStatus()) {

                            case OK:
                                switch (hsStatus) {
                                    case NOT_HANDSHAKING:
                                        throw new IOException(
                                            "Not handshaking during initial handshake");

                                    case NEED_TASK:
                                        hsStatus = doTasks();
                                    break;

                                    case FINISHED:
                                        initialHSComplete = true;
                                    break;
                                }
                            break needIO;

                            case BUFFER_UNDERFLOW:
                                // Resize buffer if needed.
                                netBBsize = sslSession.getPacketBufferSize();
                                if (netBBsize > inNetBB.capacity()) {
                                    inNetBB = ByteBuffer.allocate(netBBsize);
                                }

                                // Need to go reread the Channel for more data.

                            break needIO;

                            case BUFFER_OVERFLOW:
                                // Reset the application buffer size.
                                appBBsize = sslSession.getApplicationBufferSize();
                            break needIO;

                            default: //CLOSED:
                                throw new IOException("Received " + result.getStatus() +
                                    "during initial handshaking");
                        }
                    }  // "needIO" block.
                break;
                
                case NEED_WRAP:
                    outNetBB.clear();
                    synchronized(outLock) {
                        result = sslEngine.wrap(outhsData, outNetBB);
                        outLock.notifyAll();
                    }
                    outNetBB.flip();

                    hsStatus = result.getHandshakeStatus();

                    // Flush out the outgoing buffer, if there's anything left in it.
                    if (outNetBB.hasRemaining()) {

                        if (!tryFlush(outNetBB)) {  // writes to the socket channel
                            initialHSComplete = false;
                        }
                    }

                    switch (result.getStatus()) {
                        case OK:
                            if (hsStatus == HandshakeStatus.NEED_TASK) {
                                hsStatus = doTasks();
                            }
                        break;

                        default: // BUFFER_OVERFLOW/BUFFER_UNDERFLOW/CLOSED:
                            throw new IOException("Received" + result.getStatus() +
                                "during initial handshaking");
                    }
                break;

                case FINISHED:
                    // HANDSHAKE COMPLETE
                    initialHSComplete = true;
                    logger.log(Level.FINE, "SSL handshake completed");
                break;

                default: // NOT_HANDSHAKING/NEED_TASK
                    throw new RuntimeException("Invalid Handshaking State" +
                            hsStatus);
            } // switch

        } // while
        
    } // end doHandshake

    // Do all outstanding handshake tasks in a separate thread
    private HandshakeStatus doTasks() {
        Executor exec = Executors.newSingleThreadExecutor();
        Runnable task;

        while ((task = sslEngine.getDelegatedTask()) != null) {
            //exec.execute(task);
            task.run();
        }

        return sslEngine.getHandshakeStatus();
    }

    /*
     * Read the channel for more information, then unwrap the
     * application data we get.
     *
     * Each call to this method will perform at most one underlying read().
     */
    public int read(ByteBuffer dst) throws IOException, SSLException {
	SSLEngineResult result;

	if (!initialHSComplete) {
	    //throw new IllegalStateException();
            doHandshake();
	}

        // clear the inward byte buffers
        inNetBB.clear();
        dst.clear();

	if (sc.read(inNetBB) == -1) {
	    sslEngine.closeInbound();  // probably throws exception
	    return -1;
	}

	do {
            // expected room for unwrap
            if (dst.remaining() < appBBsize) {
                dst = ByteBuffer.allocate(dst.capacity() * 2);
            }
	    inNetBB.flip();
            synchronized(inLock) {
                result = sslEngine.unwrap(inNetBB, dst);
                inLock.notifyAll();
            }
	    inNetBB.compact();

            /*
	    * Could check here for a SSL handshake renegotiation.
            */

	    switch (result.getStatus()) {

                case BUFFER_OVERFLOW:
                    // Reset the application buffer size.
                    appBBsize = sslSession.getApplicationBufferSize();
		break;

                case BUFFER_UNDERFLOW:
                    // Resize buffer if needed.
                    if (netBBsize > inNetBB.capacity()) {
                        inNetBB = ByteBuffer.allocate(netBBsize);
                    }

                break; // break, next read will support larger buffer.

                case OK:
                    if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                        doTasks();
                    }
		break;

                default:
                    throw new IOException("sslEngine error during data read: " +
                        result.getStatus());
             }
	} while ((inNetBB.position() != 0) &&
                result.getStatus() != Status.BUFFER_UNDERFLOW);

	return (dst.position());
    }

    public long read(ByteBuffer[] dsts, int offset, int length)
            throws IOException, SSLException {
	SSLEngineResult result;
        long dataRead = 0;

	if (!initialHSComplete) {
	    //throw new IllegalStateException();
            doHandshake();
	}

        // clear the inward byte buffers
        inNetBB.clear();
        for(int i = 0; i <= length - 1; i++) {
            dsts[i].clear();
        }

	if (sc.read(inNetBB) == -1) {
	    sslEngine.closeInbound();  // probably throws exception
	    return -1;
	}

	do {
            // expected room for unwrap
            for(int count = 0; count <= length - 1; count++) {
                if (dsts[count].remaining() < appBBsize) {
                    dsts[count] =
                            ByteBuffer.allocate(dsts[count].capacity() * 2);
                }
            }

	    inNetBB.flip();
            synchronized(inLock) {
                result = sslEngine.unwrap(inNetBB, dsts, offset, length);
                inLock.notifyAll();
            }
	    inNetBB.compact();

            /*
	    * Could check here for a SSL handshake renegotiation.
            */
            
	    switch (result.getStatus()) {

                case BUFFER_OVERFLOW:
                    // Reset the application buffer size.
                    appBBsize = sslSession.getApplicationBufferSize();
		break;

                case BUFFER_UNDERFLOW:
                    // Resize buffer if needed.
                    if (netBBsize > inNetBB.capacity()) {
                        inNetBB = ByteBuffer.allocate(netBBsize);
                    }

                break; // break, next read will support larger buffer.

                case OK:
                    if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                        doTasks();
                    }
		break;

                default:
                    throw new IOException("sslEngine error during data read: " +
                        result.getStatus());
             }
	} while ((inNetBB.position() != 0) &&
                result.getStatus() != Status.BUFFER_UNDERFLOW);

        for(int j = 0; j <= length - 1; j++) {
            dataRead += (long)dsts[j].position();
        }
        
	return dataRead;
    }

    /*
     * Try to write out as much as possible from the src buffer.
     */
    public int write(ByteBuffer src) throws IOException {

	if (!initialHSComplete) {
	    // throw new IllegalStateException();
            doHandshake();
	}

	return doWrite(src);
    }

    public long write(ByteBuffer[] srcs, int offset, int length)
            throws IOException {

	if (!initialHSComplete) {
	    //throw new IllegalStateException();
            doHandshake();
	}

	return doWrite(srcs, offset, length);
    }

    /*
     * Try to flush out any existing outbound data, then try to wrap
     * anything new contained in the src buffer.
     * <P>
     * Return the number of bytes actually consumed from the buffer,
     * but the data may actually be still sitting in the output buffer,
     * waiting to be flushed.
     */
    private int doWrite(ByteBuffer src) throws IOException {
        SSLEngineResult result;
	int retValue = 0;

	if (outNetBB.hasRemaining() && !tryFlush(outNetBB)) {
	    return retValue;
	}

	/*
	 * The data buffer is empty, we can reuse the entire buffer.
	 */
	outNetBB.clear();

        synchronized(outLock) {
            result = sslEngine.wrap(src, outNetBB);
            outLock.notifyAll();
        }
	retValue = result.bytesConsumed();
        //retValue = result.bytesProduced();

	outNetBB.flip();

	switch (result.getStatus()) {

            case OK:
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    doTasks();
                }
	    break;

            default:
                throw new IOException("sslEngine error during data write: " +
                    result.getStatus());
	}

	/*
	 * Try to flush the data, regardless of whether or not
	 * it's been selected.  Odds of a write buffer being full
	 * is less than a read buffer being empty.
	 */
	if (outNetBB.hasRemaining()) {
	    tryFlush(outNetBB);
	}

	return retValue;
    }

    private long doWrite(ByteBuffer[] srcs, int offset, int length)
                throws IOException {
        SSLEngineResult result;
	int retValue = 0;

	if (outNetBB.hasRemaining() && !tryFlush(outNetBB)) {
	    return retValue;
	}

	/*
	 * The data buffer is empty, we can reuse the entire buffer.
	 */
	outNetBB.clear();

        synchronized(outLock) {
            result = sslEngine.wrap(srcs, offset, length, outNetBB);
            outLock.notifyAll();
        }
	retValue = result.bytesConsumed();
        //retValue = result.bytesProduced();

	outNetBB.flip();

	switch (result.getStatus()) {

            case OK:
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    doTasks();
                }
	    break;

            default:
                throw new IOException("sslEngine error during data write: " +
                    result.getStatus());
	}

	/*
	 * Try to flush the data, regardless of whether or not
	 * it's been selected.  Odds of a write buffer being full
	 * is less than a read buffer being empty.
	 */
	if (outNetBB.hasRemaining()) {
	    tryFlush(outNetBB);
	}

	return retValue;
    }

    // Flushes the given output Bytebuffer to the network
    boolean tryFlush(ByteBuffer bb) throws IOException {
        sc.write(bb);

        return !bb.hasRemaining();
    }

    /*
     * Begin the shutdown process.
     * <P>
     * Close out the SSLEngine if not already done so, then
     * wrap our outgoing close_notify message and try to send it on.
     * <P>
     * Return true when we're done passing the shutdown messsages.
     */
    public boolean shutdown() throws IOException {
        SSLEngineResult result;

	sslEngine.closeOutbound();

	if (outNetBB.hasRemaining() && tryFlush(outNetBB)) {
	    return false;
	}

	/*
	 * By RFC 2616, we can "fire and forget" our close_notify
	 * message.
	 */
	outNetBB.clear();
        synchronized(outLock) {
            result = sslEngine.wrap(outhsData, outNetBB);
            outLock.notifyAll();
        }
	if (result.getStatus() != Status.CLOSED) {
	    throw new SSLException("Improper close state");
	}
	outNetBB.flip();

	if (outNetBB.hasRemaining()) {
	    tryFlush(outNetBB);
	}
        
	return (!outNetBB.hasRemaining() &&
		(result.getHandshakeStatus() != HandshakeStatus.NEED_WRAP));
    }

}
