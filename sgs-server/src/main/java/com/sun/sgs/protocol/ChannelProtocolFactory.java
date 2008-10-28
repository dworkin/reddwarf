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
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.service.Service;

/**
 * A factory for creating {@link ChannelProtocol} instances for sending
 * channel-related protocolmessages to and receiving channel protocol
 * messages from a client.  A {@code ChannelProtocolFactory} must have a
 * constructor that takes the following arguments:
 *
 * <ul>
 * <li>{@link java.util.Properties}</li>
 * <li>{@link com.sun.sgs.kernel.ComponentRegistry}</li>
 * <li>{@link com.sun.sgs.service.TransactionProxy}</li>
 * </ul>
 */
public interface ChannelProtocolFactory extends Service {

    /**
     * Creates a new channel protocol instance with an underlying byte
     * {@code channel}.  Incoming messages should be dispatched to the
     * specified protocol {@code handler} as appropriate.
     *
     * @param	channel a byte channel
     * @param	handler a protocol handler
     * @param	delivery a delivery requirement for channel messages
     */
    ChannelProtocol newChannelProtocol(AsynchronousByteChannel channel,
				       ChannelProtocolHandler handler,
				       Delivery delivery);
}
