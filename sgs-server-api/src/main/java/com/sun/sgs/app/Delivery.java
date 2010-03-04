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
 * Representation for message delivery guarantees.  A channel is
 * created with a delivery guarantee.  See the {@link
 * ChannelManager#createChannel ChannelManager.createChannel} method
 * for details.
 *
 * <p>With all delivery guarantees, messages are guaranteed to be delivered
 * <i>at most once</i>. 
 */
public enum Delivery {

    /**
     * Unreliable delivery: Message delivery is not guaranteed. No
     * message order is preserved
     */
    UNRELIABLE,

    /**
     * Ordered unreliable delivery: Message delivery is not
     * guaranteed.  Messages that are delivered preserve the sender's
     * order.
     */
    ORDERED_UNRELIABLE,
	
    /**
     * Unordered reliable delivery: Message delivery is guaranteed
     * unless there is a node or network failure.  No message order is
     * preserved.
     */
    UNORDERED_RELIABLE,
	
    /**
     * Reliable delivery: Message delivery is guaranteed unless
     * there is a node or network failure.  Messages that are
     * delivered preserve the sender's order.
     */
    RELIABLE;

    /**
     * Returns {@code true} if this delivery guarantee meets the minimum
     * requirements of the specified {@code delivery} guarantee, otherwise
     * returns {@code false}.
     *
     * @param	delivery a delivery guarantee
     * @return	{@code true} if this delivery guarantee meets the minimum
     *		requirements of the specified {@code delivery} guarantee
     */
    public boolean supportsDelivery(Delivery delivery) {
	if (delivery == null) {
	    throw new NullPointerException("null delivery");
	}
	return
	    this == delivery ||
	    this == RELIABLE ||
	    delivery == UNRELIABLE;
    }
}
