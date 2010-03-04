/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
