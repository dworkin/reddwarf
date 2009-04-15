/*
 * Copyright (c) 2007 - 2009, Sun Microsystems, Inc.
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
 */

/*
 * This file declares constants relevant to the SGS network wire protocol.
 */

/**
 * SGS Protocol constants.
 * <p>
 * A protocol message is constructed as follows:
 * <ul>
 * <li> (unsigend short) payload length, not including this field
 * <li> (byte) operation code
 * <li> optional content, depending on the operation code.
 * </ul>
 * <p>
 * A {@code ByteArray} is encoded as follows:
 * <ul>
 * <li> (byte[]) the bytes in the array
 * </ul>
 * <b>Note:</b> Messages may need to include explicit array length fields if 
 * the include more than one ByteArray. If so, the length should preceed the 
 * ByteArray. None of the messages defined as part of the base protocol contain 
 * multiple ByteArrays except for the channel message, and so all have a length 
 * that is either the same as the payload length or calculated from the 
 * payload length.
 * <p>
 * A {@code String} is encoded as follows:
 * <ul>
 * <li> (unsigned short) number of bytes of modified UTF-8 encoded String
 * <li> (byte[]) String encoded in modified UTF-8 as described
 * in {@link DataInput}
 * </ul>
 */

#ifndef SGS_PROTOCOL_H
#define SGS_PROTOCOL_H

#ifdef __cplusplus
extern "C" {
#endif

    /**
     * The maximum length of any protocol message field defined as a
     * String or byte[]: 65535 bytes. This includes the two bytes at the
     * beginning of the message, which give the length of the rest.
     */
#define SGS_MSG_MAX_LENGTH 65535

    /**
     * The maximum payload length, in bytes. The payload includes the opcode,
     * which is always the first byte.
     */
#define SGS_MAX_PAYLOAD_LENGTH 65533

    /**
     * This is the size of the static portion of a message (i.e. with a message
     * payload of 0 bytes).
     */
#define SGS_MSG_INIT_LEN (SGS_MSG_MAX_LENGTH - SGS_MAX_PAYLOAD_LENGTH)

    /* The version number */
#define SGS_MSG_VERSION '\004'

    /*The offset of the opcode in the messages*/
#define SGS_OPCODE_OFFSET 2
    /*The constant used to account for the 2 byte length field at the
     *beginning of every message buffer 
     */
#define SGS_MSG_LENGTH_OFFSET 2

    typedef enum sgs_opcode {
        /**
         * Login request from the client to the server.
         * <ul>
         * <li> (byte) protocol version
         * <li> (unsighed short) name array length
         * <li> (char[]) name
         * <li> (unsigned short) password array length
         * <li> (char[]) password
         * </ul>
         */
        SGS_OPCODE_LOGIN_REQUEST = 0x10,

        /**
         * Login success (login request acknowledgment). Server response 
         * to the client's login request
         * <ul>
         * <li> (byte[]) reconnectionKey
         * </ul>
         */
        SGS_OPCODE_LOGIN_SUCCESS = 0x11,

        /**
         * Login failure (login request acknowledgment). Server response 
         * to the client's login request.
         * <ul>
         * <li> (unsigned short) array length
         * <li> (uint8_t[]) reason (note- the array can probably be cast to a 
         * char[], but may not null-terminated)
         * </ul>
         */
        SGS_OPCODE_LOGIN_FAILURE = 0x12,

        /**
         * Login redirect. Server response to a client's login request
         * Payload:
         * <ul>
         * <li> (unsigned short) array length
         * <li> (uint8_t[]) hostname (note- the array can 
         * probably be cast to a char[], but is not null-terminated)
         * <li> (int) port
         */
        SGS_OPCODE_LOGIN_REDIRECT = 0x13,

        /**
         * Reconnection request. Client's request to the server
         * <ul>
         * <li> byte protocol version
         * <li> (byte[]) reconnectionKey
         * </ul>
         */
        SGS_OPCODE_RECONNECT_REQUEST = 0x20,

        /**
         * Reconnect success (reconnection request acknowledgment). 
         * Sent from the server to the client.
         * <ul>
         * <li> (unsigned short) array length
         * <li> (byte[]) reconnectionKey
         * </ul>
         */
        SGS_OPCODE_RECONNECT_SUCCESS = 0x21,

        /**
         * Reconnect failure (reconnection request acknowledgment).
         * <ul>
         * <li> (unisigned short) array length
         * <li> (uint8_t[]) reason (note-- the array can probably be cast to 
         * a char[], but is not null-terminated)
         * </ul>
         */
        SGS_OPCODE_RECONNECT_FAILURE = 0x22,

        /**
         * Session message. May be sent by the client or the server.
         * Maximum length is MAX_PAYLOAD_LENGTH, currently 65,532 bytes.
         * Larger messages require fragmentation and reassembly above
         * this protocol layer.
         *
         * <ul>
         * <li> (byte[]) message
         * </ul>
         */
        SGS_OPCODE_SESSION_MESSAGE = 0x30,

        /**
         * Logout request. Sent from a client to the server.
         * No payload.
         */
        SGS_OPCODE_LOGOUT_REQUEST = 0x40,

        /**
         * Logout success (logout request acknowledgment). 
         * Server response to a logout request.
         * No payload.
         */
        SGS_OPCODE_LOGOUT_SUCCESS = 0x41,

        /**
         * Channel join. Server notifying the client that the client 
         * has joined a channel.
         * <ul>
         * <li> (unsigned short) channel name length
         * <li> (uint8_t[]) channel name (note-- the array can probably be cast 
         * to a char[], but is not null-terminated)
         * <li> (byte[]) channel ID
         * </ul>
         */
        SGS_OPCODE_CHANNEL_JOIN = 0x50,

        /**
         * Channel leave. Server notifying a client that it ghas left a channel.
         * <ul>
         * <li> (byte[]) channel ID
         * </ul>
         */
        SGS_OPCODE_CHANNEL_LEAVE = 0x51,

        /**
         * Channel message. May be sent by the client or the server.
         * Maximum length is SGS_MAX_PAYLOAD_LENGTH bytes. Larger messages
         * require fragmentation and reassembly above the protocol layer<br>
         * Payload:
         * <ul>
         * <li> (unsigned short) channel ID size
         * <li> (byte[]) channel ID
         * <li> (byte[]) message
         * </ul>
         */
        SGS_OPCODE_CHANNEL_MESSAGE = 0x52
    } sgs_opcode;

#ifdef __cplusplus
}
#endif

#endif /* !SGS_PROTOCOL_H */
