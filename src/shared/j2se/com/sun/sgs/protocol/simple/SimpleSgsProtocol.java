/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.protocol.simple;

/**
 * SGS Protocol constants.
 * <p>
 * A protocol message is constructed as follows:
 * <ul>
 * <li> (int) payload length, not including this int
 * <li> (byte) version number
 * <li> (byte) service id
 * <li> (byte) operation code
 * <li> optional content, depending on the operation code.
 * </ul>
 * <p>
 * A {@code ByteArray} is encoded as follows:
 * <ul>
 * <li> (unsigned short) number of bytes in the array
 * <li> (byte[]) the bytes in the array
 * </ul>
 * <p>
 * A {@code String} is encoded as follows:
 * <ul>
 * <li> (unsigned short) number of bytes of modified UTF-8 encoded String
 * <li> (byte[]) String encoded in modified UTF-8 as described
 * in {@link java.io.DataInput}
 * </ul>
  * <p>
 * A {@code CompactId} is encoded as follows:
 * <ul>
 * <p>The first byte of the ID's external form contains a length field
 * of variable size.  If the first two bits of the length byte are not
 * #b11, then the size of the ID is indicated as follows:
 *
 * <ul>
 * <li>#b00: 14 bit ID (2 bytes total)</li>
 * <li>#b01: 30 bit ID (4 bytes total)</li>
 * <li>#b10: 62 bit ID (8 bytes total)</li>
 * </ul>
 *
 * <p>If the first byte has the following format:
 * <ul><li>1100<i>nnnn</i></li></ul> <p>then, the ID is contained in
 * the next {@code 8 + nnnn} bytes.
 * </ul>
 */
public interface SimpleSgsProtocol {
    
    /**
     * The maximum length of any protocol message field defined as a
     * {@code String} or {@code byte[]}: {@value #MAX_MESSAGE_LENGTH} bytes.
     */
    final int MAX_MESSAGE_LENGTH = 65535;

    /** The version number, currently {@code 0x02}. */
    final byte VERSION = 0x02;

    /** The Application service ID, {@code 0x01}. */
    final byte APPLICATION_SERVICE = 0x01;

    /** The Channel service ID, {@code 0x02}. */
    final byte CHANNEL_SERVICE = 0x02;

    /**
     * Login request from a client to a server.
     * <br>
     * ServiceId: {@code 0x01} (Application)
     * <br>
     * Opcode: {@code 0x10}
     * <br>
     * Payload:
     * <ul>
     * <li>(String) name
     * <li>(String) password
     * </ul>
     */
    final byte LOGIN_REQUEST = 0x10;

    /**
     * Login success.  Server response to a client's {@link #LOGIN_REQUEST}.
     * <br>
     * ServiceId: {@code 0x01} (Application)
     * <br>
     * Opcode: {@code 0x11}
     * <br>
     * Payload:
     * <ul>
     * <li> (CompactId) sessionId
     * <li> (CompactId) reconnectionKey
     * </ul>
     */
    final byte LOGIN_SUCCESS = 0x11;

    /**
     * Login failure.  Server response to a client's {@link #LOGIN_REQUEST}.
     * <br>
     * ServiceId: {@code 0x01} (Application)
     * <br>
     * Opcode: {@code 0x12}
     * <br>
     * Payload:
     * <ul>
     * <li> (String) reason
     * </ul>
     */
    final byte LOGIN_FAILURE = 0x12;

    /**
     * Reconnection request.  Client requesting reconnect to a server.
     * <br>
     * ServiceId: {@code 0x01} (Application)
     * <br>
     * Opcode: {@code 0x20}
     * <br>
     * Payload:
     * <ul>
     * <li> (CompactId) reconnectionKey
     * </ul>
     */
    final byte RECONNECT_REQUEST = 0x20;

    /**
     * Reconnect success.  Server response to a client's {@link #RECONNECT_REQUEST}.
     * <br>
     * ServiceId: {@code 0x01} (Application)
     * <br>
     * Opcode: {@code 0x21}
     * <br>
     * Payload:
     * <ul>
     * <li> (CompactId) reconnectionKey
     * </ul>
     */
    final byte RECONNECT_SUCCESS = 0x21;

    /**
     * Reconnect failure.  Server response to a client's {@link #RECONNECT_REQUEST}.
     * <br>
     * ServiceId: {@code 0x01} (Application)
     * <br>
     * Opcode: {@code 0x22}
     * <br>
     * Payload:
     * <ul>
     * <li> (String) reason
     * </ul>
     */
    final byte RECONNECT_FAILURE = 0x22;

    /**
     * Session message.  May be sent by the client or the server.
     * Maximum length is 64 KB minus one byte.
     * Larger messages require fragmentation and reassembly above
     * this protocol layer.
     * <br>
     * ServiceId: {@code 0x01} (Application)
     * <br>
     * Opcode: {@code 0x30}
     * <br>
     * Payload:
     * <ul>
     * <li> (long) sequence number
     * <li> (ByteArray) message
     * </ul>
     */
    final byte SESSION_MESSAGE = 0x30;


    /**
     * Logout request from a client to a server.
     * <br>
     * ServiceId: {@code 0x01} (Application)
     * <br>
     * Opcode: {@code 0x40}
     * <br>
     * No payload.
     */
    final byte LOGOUT_REQUEST = 0x40;

    /**
     * Logout success.  Server response to a client's {@link #LOGOUT_REQUEST}.
     * <br>
     * ServiceId: {@code 0x01} (Application)
     * <br>
     * Opcode: {@code 0x41}
     * <br>
     * No payload.
     */
    final byte LOGOUT_SUCCESS = 0x41;

    /**
     * Channel join.  Server notifying a client that it has joined a channel.
     * <br>
     * ServiceId: {@code 0x02} (Channel)
     * <br>
     * Opcode: {@code 0x50}
     * <br>
     * Payload:
     * <ul>
     * <li> (String) channel name
     * <li> (CompactId) channel ID
     * </ul>
     */
    final byte CHANNEL_JOIN = 0x50;

    /**
     * Channel leave.  Server notifying a client that it has left a channel.
     * <br>
     * ServiceId: {@code 0x02} (Channel)
     * <br>
     * Opcode: {@code 0x52}
     * <br>
     * Payload:
     * <ul>
     * <li> (CompactId) channel ID
     * </ul>
     */
    final byte CHANNEL_LEAVE = 0x52;
    
    /**
     * Channel send request from a client to a server.
     * <br>
     * ServiceId: {@code 0x02} (Channel)
     * <br>
     * Opcode: {@code 0x53}
     * <br>
     * Payload:
     * <ul>
     * <li> (CompactId) channel ID
     * <li> (long) sequence number
     * <li> (short) number of recipients (0 = all)
     * <li> If number of recipients &gt; 0, for each recipient:
     * <ul>
     * <li> (CompactId) sessionId
     * </ul>
     * <li> (ByteArray) message
     * </ul>
     */
    final byte CHANNEL_SEND_REQUEST = 0x53;

    /**
     * Channel message (sent from server to recipient on channel).
     * <br>
     * ServiceId: {@code 0x02} (Channel)
     * <br>
     * Opcode: {@code 0x54}
     * <br>
     * Payload:
     * <ul>
     * <li> (CompactId) channel ID
     * <li> (long) sequence number
     * <li> (CompactId) sender's sessionId
     *		(canonical CompactId of zero if sent by server)
     * <li> (ByteArray) message
     * </ul>
     */
    final byte CHANNEL_MESSAGE = 0x54;

}
