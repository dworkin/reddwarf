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

package com.sun.sgs.protocol.simple;

/**
 * SGS Protocol constants.
 * <p>
 * A protocol message is constructed as follows:
 * <ul>
 * <li> (unsigned short) payload length, not including this field
 * <li> (byte) operation code
 * <li> optional content, depending on the operation code.
 * </ul>
 * <p>
 * A {@code ByteArray} is encoded as follows:
 * <ul>
 * <li> (byte[]) the bytes in the array
 * </ul>
 * <b>Note:</b> Messages may need to include explicit array length fields
 * if they include more than one ByteArray.
 * <p>
 * A {@code String} is encoded as follows:
 * <ul>
 * <li> (unsigned short) number of bytes of modified UTF-8 encoded String
 * <li> (byte[]) String encoded in modified UTF-8 as described
 * in {@link java.io.DataInput}
 * </ul>
 */
public interface SimpleSgsProtocol {
    
    /**
     * The maximum length of a protocol message:
     * {@value #MAX_MESSAGE_LENGTH} bytes.
     */
    // TODO This has been bumped up temporarily to support the hack
    // example.  Set this limit back down to 8032 once fragmentation
    // is implemented in the example utilities. -JM
    //
//  final int MAX_MESSAGE_LENGTH = 8032;
    final int MAX_MESSAGE_LENGTH = 64032;

    /**
     * The maximum payload length:
     * {@value #MAX_PAYLOAD_LENGTH} bytes.
     */
    // TODO This has been bumped up temporarily to support the hack
    // example.  Set this limit back down to 8000 once fragmentation
    // is implemented in the example utilities. -JM
    // 
//  final int MAX_PAYLOAD_LENGTH = 8000;
    final int MAX_PAYLOAD_LENGTH = 64000;

    /** The version number, currently {@code 0x03}. */
    final byte VERSION = 0x03;

    /**
     * Login request from a client to a server.
     * <br>
     * Opcode: {@code 0x10}
     * <br>
     * Payload:
     * <ul>
     * <li>(byte)   protocol version
     * <li>(String) name
     * <li>(String) password
     * </ul>
     */
    final byte LOGIN_REQUEST = 0x10;

    /**
     * Login success.  Server response to a client's {@link #LOGIN_REQUEST}.
     * <br>
     * Opcode: {@code 0x11}
     * <br>
     * Payload:
     * <ul>
     * <li> (ByteArray) reconnectionKey
     * </ul>
     */
    final byte LOGIN_SUCCESS = 0x11;

    /**
     * Login failure.  Server response to a client's {@link #LOGIN_REQUEST}.
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
     * Login redirect.  Server response to a client's {@link #LOGIN_REQUEST}.
     * <br>
     * Opcode: {@code 0x13}
     * <br>
     * Payload:
     * <ul>
     * <li> (String) hostname
     * <li> (int) port
     * </ul>
     */
    final byte LOGIN_REDIRECT = 0x13;

    /**
     * Reconnection request.  Client requesting reconnect to a server.
     * <br>
     * Opcode: {@code 0x20}
     * <br>
     * Payload:
     * <ul>
     * <li> (byte)      protocol version
     * <li> (ByteArray) reconnectionKey
     * </ul>
     */
    final byte RECONNECT_REQUEST = 0x20;

    /**
     * Reconnect success.  Server response to a client's
     * {@link #RECONNECT_REQUEST}.
     * <br>
     * Opcode: {@code 0x21}
     * <br>
     * Payload:
     * <ul>
     * <li> (ByteArray) reconnectionKey
     * </ul>
     */
    final byte RECONNECT_SUCCESS = 0x21;

    /**
     * Reconnect failure.  Server response to a client's
     * {@link #RECONNECT_REQUEST}.
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
     * Maximum length is {@value #MAX_PAYLOAD_LENGTH} bytes.
     * Larger messages require fragmentation and reassembly above
     * this protocol layer.
     * <br>
     * Opcode: {@code 0x30}
     * <br>
     * Payload:
     * <ul>
     * <li> (ByteArray) message
     * </ul>
     */
    final byte SESSION_MESSAGE = 0x30;


    /**
     * Logout request from a client to a server.
     * <br>
     * Opcode: {@code 0x40}
     * <br>
     * No payload.
     */
    final byte LOGOUT_REQUEST = 0x40;

    /**
     * Logout success.  Server response to a client's {@link #LOGOUT_REQUEST}.
     * <br>
     * Opcode: {@code 0x41}
     * <br>
     * No payload.
     */
    final byte LOGOUT_SUCCESS = 0x41;

}
