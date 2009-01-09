/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.protocol.multi;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.protocol.simple.AsynchronousMessageChannel;
import com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.protocol.ProtocolListener;
import com.sun.sgs.protocol.SessionProtocolHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

/**
 * Implements the protocol specified in {@code SimpleSgsProtocol} over two
 * communication channels.  The implementation uses a wrapper channel,
 * {@link AsynchronousMessageChannel}, that reads and writes complete
 * messages by framing messages with a 2-byte message length, and masking
 * (and re-issuing) partial I/O operations.  Also enforces a fixed buffer
 * size when reading.
 */
class MultiSgsProtocolImpl extends SimpleSgsProtocolImpl {
    
    /** The logger for this class. */
    private static final LoggerWrapper logger = new LoggerWrapper(
	Logger.getLogger(MultiSgsProtocolImpl.class.getName()));

    /**
     * Flags on whether to use the primary (reliable) connection or
     * to use the secondary connection.
     */
    private final boolean[] usePrimary = new boolean[Delivery.values().length];
    
    /** Secondary comm channel. */
    private SecondaryChannel secondaryChannel = null;
    
    /**
     * Creates a new instance of this class.
     *
     * @param	listener a protocol listener
     * @param	acceptor the {@code SimpleSgsProtocol} acceptor
     * @param	byteChannel a byte channel for the underlying connection
     * @param	readBufferSize the read buffer size
     */
    public MultiSgsProtocolImpl(ProtocolListener listener,
                                MultiSgsProtocolAcceptor acceptor,
                                AsynchronousByteChannel byteChannel,
                                int readBufferSize)
    {
	super(listener, acceptor, byteChannel, readBufferSize, logger);
        deliverySet.add(Delivery.RELIABLE);
        for (Delivery delivery : Delivery.values()) {
            usePrimary[delivery.ordinal()] = true;
	}
	
	/*
	 * TBD: It might be a good idea to implement high- and low-water marks
	 * for the buffers, so they don't go into hysteresis when they get
	 * full. -JM
	 */
	scheduleReadOnReadHandler();
    }

    /* -- Package access methods -- */
    
    /**
     * Attach the secondary connection.
     *
     * @param	channel a secondary channel
     * @param	supportedDelivery an array of supported delivery requirements
     * @return	the sesssion protocol handler for this instance
     */
    SessionProtocolHandler attach(SecondaryChannel channel,
                                  Delivery[] supportedDelivery)
    {
        if (protocolHandler != null) {
        
            // Set the usePrimary flag to false for any delivery that the
            // secondary connection can support. Skipping reliable, since
            // the primary does that.
            //
            for (Delivery delivery : supportedDelivery) {
                if (delivery != Delivery.RELIABLE) {
                    deliverySet.add(delivery);
                    usePrimary[delivery.ordinal()] = false;
                }
            }
            secondaryChannel = channel;
            return protocolHandler;
        } else {
            return null;
	}
    }
    
    /* -- Override SimpleSgsProtocolImpl protected methods -- */

    /***
     * Writes the specified buffer, satisfying the specified delivery
     * requirement.
     *
     * <p>If reliable delivery is specified or there is no secondary
     * channel that satisfies the delivery requirement, this
     * implementation invokes the superclass's {@code writeBuffer}
     * method with the specified buffer and delivery requirement.
     * Otherwise, this implementation uses the secondary channel to
     * write the data.
     *
     * @param	buf a byte buffer containing a protocol message
     * @param	delivery a delivery requirement
     */
    @Override
    protected void writeBuffer(ByteBuffer buf, Delivery delivery) {
        if (usePrimary[delivery.ordinal()]) {
	    super.writeBuffer(buf, delivery);
        } else {
            secondaryChannel.writeOrEnqueueIfLoginNotHandled(buf);
        }
    }

    /**
     * Notifies the associated client that the previous login attempt was
     * successful, then notifies the associated acceptor of the login.
     */
    @Override
    protected void loginSuccess() {
	super.loginSuccess();
        ((MultiSgsProtocolAcceptor) acceptor).
	    successfulLogin(reconnectKey, this);
    }
    
    /* -- Override Channel methods -- */
    
    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
	super.close();
	if (secondaryChannel != null) {
	    secondaryChannel.close();
        }
    }
}
