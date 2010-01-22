/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
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
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * Sets up server side SSLEngine and has methods to read and write SSL/TLS
 * encrypted data to and from the network.
 */
public class SSLChannel {

    /** The logger for this class */
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(SSLChannel.class.getName()));

    /** The SSLEngine */
    private SSLEngine sslEngine = null;

    /** The SSLSession */
    private SSLSession sslSession = null;

    /** The SSL handshake status */
    private HandshakeStatus hsStatus = null;

    /** Indicates whether the initial handshake has been completed */
    private boolean initialHSComplete = false;

    /** An object used for synchronizing on SSLEngine.unwrap() */
    private final Object inLock = new Object();

    /** An object used for synchronizing on SSLEngine.wrap() */
    private final Object outLock = new Object();

    /** The inbound network ByteBuffer */
    private ByteBuffer inNetBB = null;

    /** The outbound network ByteBuffer */
    private ByteBuffer outNetBB = null;

    /** An empty ByteBuffer for consuming inbound handshake data */
    private ByteBuffer inhsData = null;

    /** An empty ByteBuffer for producing outbound handshake data */
    private ByteBuffer outhsData = null;

    /** The application ByteBuffer size */
    private int appBBsize = 4096;

    /** The network ByteBuffer size */
    private int netBBsize = 4096;

    /** The underlying socketchannel */
    private final SocketChannel sc;

    /**
     * Constructs an instance of this class and calls {@code startSSLEngine()}.
     *
     * @param sc the socketchannel
     */
    public SSLChannel(SocketChannel sc) {
        this.sc = sc;
        startSSLEngine();
    }

    /**
     * Sets the SSLEngine and to server mode and sets the ByteBuffer sizes.
     */
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

    /**
     * Performs an SSL handshake.
     *
     * @throws SSLException if an SSL error occurs
     * @throws IOException if an I/O error occurs
     */
    void doHandshake() throws SSLException, IOException {

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
                        }

                        if (inhsData.remaining() < appBBsize) {
                            inhsData = ByteBuffer.allocate(
                                                    inhsData.capacity() * 2);
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
                                        throw new IOException("Not " +
                                                "handshaking during initial " +
                                                "handshake");

                                    case NEED_TASK:
                                        hsStatus = doTasks();
                                    break;

                                    case FINISHED:
                                        initialHSComplete = true;
                                    break;
                                }
                            break needIO;

                            case BUFFER_UNDERFLOW:
                                netBBsize = sslSession.getPacketBufferSize();
                                if (netBBsize > inNetBB.capacity()) {
                                    inNetBB = ByteBuffer.allocate(netBBsize);
                                }
                            break needIO;

                            case BUFFER_OVERFLOW:
                                appBBsize =
                                        sslSession.getApplicationBufferSize();
                            break needIO;

                            default: //CLOSED:
                                throw new IOException("Received " +
                                        result.getStatus() +
                                        " during initial handshaking");
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

                    if (outNetBB.hasRemaining()) {
                        if (!tryFlush(outNetBB)) {
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
                            throw new IOException("Received" +
                                    result.getStatus() +
                                " during initial handshaking");
                    }
                break;

                case FINISHED: // HANDSHAKE COMPLETE
                    initialHSComplete = true;
                    logger.log(Level.FINE, "SSL handshake completed");
                break;

                default: // NOT_HANDSHAKING/NEED_TASK
                    throw new RuntimeException("Invalid Handshaking State" +
                            hsStatus);
            }
        }
    }

    /**
     * Does all outstanding handshake tasks in a separate thread.
     *
     * @return {@code sslEngine.getHandshakeStatus()}
     */
    private HandshakeStatus doTasks() {
        Executor exec = Executors.newSingleThreadExecutor();
        Runnable task;

        while ((task = sslEngine.getDelegatedTask()) != null) {
            exec.execute(task);
            task.run();
        }

        return sslEngine.getHandshakeStatus();
    }

    /**
     * Reads the {@link socketChannel} for more information, then unwraps the
     * application data.
     * <p>
     * Each call to this method will perform at most one underlying read.
     * 
     * @param dst destination ByteBuffer
     * @return the number of bytes read from the inbound network ByteBuffer
     * @throws SSLException if an SSL error occurs
     * @throws IOException if an I/O error occurs
     */
    public int read(ByteBuffer dst) throws SSLException, IOException {
	SSLEngineResult result;

	if (!initialHSComplete) {
            doHandshake();
	}

        inNetBB.clear();
        dst.clear();

	if (sc.read(inNetBB) == -1) {
	    sslEngine.closeInbound();
	    return -1;
	}

	do {
            if (dst.remaining() < appBBsize) {
                dst = ByteBuffer.allocate(dst.capacity() * 2);
            }
	    inNetBB.flip();
            synchronized(inLock) {
                result = sslEngine.unwrap(inNetBB, dst);
                inLock.notifyAll();
            }
	    inNetBB.compact();

            switch (result.getStatus()) {

                case BUFFER_OVERFLOW:
                    appBBsize = sslSession.getApplicationBufferSize();
		break;

                case BUFFER_UNDERFLOW:
                    if (netBBsize > inNetBB.capacity()) {
                        inNetBB = ByteBuffer.allocate(netBBsize);
                    }
                break;

                case OK:
                    if (result.getHandshakeStatus() ==
                            HandshakeStatus.NEED_TASK) {
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

    /**
     * Reads the {@link socketChannel} for more information, then unwraps the
     * application data.
     * <p>
     * Each call to this method will perform at most one underlying read.
     *
     * @param dsts array of destination ByteBuffers
     * @param offset offset within the first buffer of the buffer array
     * @param length maximum number of buffers to be accessed
     * @return the number of bytes read from the inbound network ByteBuffer
     * @throws IOException if an I/O error occurs
     */
    public long read(ByteBuffer[] dsts, int offset, int length)
            throws SSLException, IOException {
	SSLEngineResult result;
        long dataRead = 0;

	if (!initialHSComplete) {
            doHandshake();
	}

        inNetBB.clear();
        for(int i = 0; i <= length - 1; i++) {
            dsts[i].clear();
        }

	if (sc.read(inNetBB) == -1) {
	    sslEngine.closeInbound();
	    return -1;
	}

	do {
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

            switch (result.getStatus()) {

                case BUFFER_OVERFLOW:
                    appBBsize = sslSession.getApplicationBufferSize();
		break;

                case BUFFER_UNDERFLOW:
                    if (netBBsize > inNetBB.capacity()) {
                        inNetBB = ByteBuffer.allocate(netBBsize);
                    }
                break;

                case OK:
                    if (result.getHandshakeStatus() ==
                                                HandshakeStatus.NEED_TASK) {
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

    /**
     * Ensures SSL handshake is complete before writing TLS/SSL encrypted data
     * to the network.
     *
     * @param src the source ByteBuffer
     * @return the result of (@code doWrite(src)}
     * @throws IOException if an I/O error occurs
     */
    public int write(ByteBuffer src) throws IOException {

	if (!initialHSComplete) {
            doHandshake();
	}

	return doWrite(src);
    }

    /**
     * Ensures SSL handshake is complete before writing TLS/SSL encrypted data
     * to the network.
     *
     * @param srcs the array of source ByteBuffers
     * @param offset offset within the first buffer of the Bytebuffer array
     * @param length maximum number of buffers to be accessed
     * @return the result of (@code doWrite(src, offset, length)}
     * @throws IOException if an I/O error occurs
     */
    public long write(ByteBuffer[] srcs, int offset, int length)
            throws IOException {

	if (!initialHSComplete) {
            doHandshake();
	}

	return doWrite(srcs, offset, length);
    }

    /**
     * Writes outbound data to the underlying {@link SocketChannel}.
     *
     * @param src the source ByteBuffer
     * @return the number of bytes actually consumed from the buffer
     * @throws SSLException if an SSL error occurs
     * @throws IOException if an I/O error occurs
     */
    private int doWrite(ByteBuffer src) throws SSLException, IOException {
        SSLEngineResult result;
	int retValue = 0;

	if (outNetBB.hasRemaining() && !tryFlush(outNetBB)) {
	    return retValue;
	}

	outNetBB.clear();

        synchronized(outLock) {
            result = sslEngine.wrap(src, outNetBB);
            outLock.notifyAll();
        }
	retValue = result.bytesConsumed();

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

	if (outNetBB.hasRemaining()) {
	    tryFlush(outNetBB);
	}

	return retValue;
    }

    /**
     * Writes outbound data to the underlying {@link SocketChannel}.
     *
     * @param srcs the array of source ByteBuffers
     * @param offset offset within the first buffer of the Bytebuffer array
     * @param length maximum number of buffers to be accessed
     * @return the number of bytes actually consumed from the buffer
     * @throws SSLException if an SSL error occurs
     * @throws IOException if an I/O error occurs
     */
    private long doWrite(ByteBuffer[] srcs, int offset, int length)
                throws SSLException, IOException {
        SSLEngineResult result;
	int retValue = 0;

	if (outNetBB.hasRemaining() && !tryFlush(outNetBB)) {
	    return retValue;
	}

	outNetBB.clear();

        synchronized(outLock) {
            result = sslEngine.wrap(srcs, offset, length, outNetBB);
            outLock.notifyAll();
        }
	retValue = result.bytesConsumed();

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

	if (outNetBB.hasRemaining()) {
	    tryFlush(outNetBB);
	}

	return retValue;
    }

    /**
     * Flushes the given output Bytebuffer to the network.
     *
     * @param bb the source ByteBuffer
     * @return {@code true} if the ByteBuffer is empty
     * @throws IOException if an I/O error occurs
     */
    boolean tryFlush(ByteBuffer bb) throws IOException {
        sc.write(bb);

        return !bb.hasRemaining();
    }

    /**
     * Begins the shutdown process. Closes the outbound SSLEngine; by RFC 2616,
     * the close_notify message can be a "fire and forget" message.
     *
     * @return {@code true} if the shutdown messsages have been sent
     * @throws SSLException if an SSL error occurs
     * @throws IOException if an I/O error occurs
     */
    public boolean shutdown() throws SSLException, IOException {
        SSLEngineResult result;

	sslEngine.closeOutbound();

	if (outNetBB.hasRemaining() && tryFlush(outNetBB)) {
	    return false;
	}

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
