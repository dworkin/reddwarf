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
