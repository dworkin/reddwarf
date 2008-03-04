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

package com.sun.sgs.impl.service.session;

import java.nio.ByteBuffer;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.sharedutil.HexDumper;

/**
 * A request to write a message.
 */
class WriteRequest {
    private final ByteBuffer buf;
    private final Delivery delivery;

    /**
     * Creates a new request to write the given message with the
     * given delivery requirement.
     * 
     * @param message a message
     * @param delivery a delivery requirement
     */
    public WriteRequest(ByteBuffer message, Delivery delivery) {
        this.buf = message;
        this.delivery = delivery;
    }

    /**
     * Returns a new read-only buffer containing the message.
     * 
     * @return the message
     */
    public ByteBuffer getMessage() {
        return buf.asReadOnlyBuffer();
    }
    
    /**
     * Returns the delivery.
     * 
     * @return the delivery
     */
    public Delivery getDelivery() {
        return delivery;
    }

    /**
     * Returns the size of the request.
     * 
     * @return the size
     */
    public int getSize() {
        return buf.capacity();
    }

    /**
     * Completion hook.  The default implementation does nothing.
     */
    public void done() {
        // empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getClass().getName() + "," + delivery.name() + "," +
            HexDumper.format(buf, 0x50);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (! (obj instanceof WriteRequest))
            return false;
        WriteRequest o = (WriteRequest) obj;
        return getDelivery().equals(o.getDelivery()) &&
               getMessage().equals(o.getMessage());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return buf.hashCode();
    }
}
