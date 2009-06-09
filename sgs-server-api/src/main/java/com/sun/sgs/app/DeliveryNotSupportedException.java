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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.app;

/**
 * An exception that indicates a {@link Delivery delivery guarantee} is
 * not supported.
 */
public class DeliveryNotSupportedException extends RuntimeException {

    /** The serial version for this class. */
    private static final long serialVersionUID = 1L;

    /** The delivery guarantee. */
    private final Delivery delivery;
    
    /**
     * Constructs and instance with the specified detail {@code message}
     * and unsupported {@code delivery} guarantee.
     *
     * @param	message a detail message, or {@code null}
     * @param	delivery an unsupported delivery guarantee
     */
    public DeliveryNotSupportedException(String message, Delivery delivery) {
	super(message);
	if (delivery == null) {
	    throw new NullPointerException("null delivery");
	}
	this.delivery = delivery;
    }
    
    /**
     * Returns the delivery guarantee that is not supported (specified
     * during construction).
     *
     * @return a delivery guarantee
     */
    public Delivery getDelivery() {
	return delivery;
    }
}
