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

package com.sun.sgs.protocol;

import com.sun.sgs.app.Delivery;

/**
 * An exception that indicates a delivery requirement is not supported.
 *
 * @see SessionProtocol#channelMessage
 */
public class DeliveryNotSupportedException extends RuntimeException {

    /** The serial version for this class. */
    private static final long serialVersionUID = 1L;

    /** The delivery requirement. */
    private final Delivery delivery;
    
    /**
     * Constructs and instance with the specified detail {@code message}
     * and unsupported {@code delivery} requirement.
     *
     * @param	message a detail message, or {@code null}
     * @param	delivery an unsupported delivery requirement
     */
    public DeliveryNotSupportedException(String message, Delivery delivery) {
	super(message);
	if (delivery == null) {
	    throw new NullPointerException("null delivery");
	}
	this.delivery = delivery;
    }
    
    /**
     * Returns the delivery requirement that is not supported (specified
     * during construction).
     *
     * @return a delivery requirement
     */
    public Delivery getDelivery() {
	return delivery;
    }
}
