/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
