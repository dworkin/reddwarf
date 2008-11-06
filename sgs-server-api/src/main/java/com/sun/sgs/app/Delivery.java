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

package com.sun.sgs.app;

/**
 * Representation for message delivery requirements.  A channel is
 * created with a delivery requirement.  See the {@link
 * ChannelManager#createChannel ChannelManager.createChannel} method
 * for details.
 *
 * <p>Messages are guaranteed to be delivered <i>at most once</i>.
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
}
