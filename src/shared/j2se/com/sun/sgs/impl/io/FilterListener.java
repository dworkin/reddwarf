/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.impl.io;

import org.apache.mina.common.ByteBuffer;

/**
 * Receives the messages resulting from processing by a
 * {@link CompleteMessageFilter}.
 */
interface FilterListener {
    /**
     * Notifies this listener that a complete, filtered message
     * has been received and should be dispatched to the final recipient.
     *
     * @param buf a {@code MINA ByteBuffer} containing the complete message
     */
    void filteredMessageReceived(ByteBuffer buf);

    /**
     * Notifies this listener that an outbound message has been filtered
     * (prepending the message length) and should be sent "raw" on the
     * underlying transport.
     *
     * @param buf a {@code MINA ByteBuffer} containing the message to send
     */
    void sendUnfiltered(ByteBuffer buf);
}
