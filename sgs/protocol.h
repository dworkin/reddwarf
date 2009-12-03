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
#define SGS_MSG_VERSION '\005'

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
         * Suspend messages notification. Server to client notification.
         * <br>
         * Opcode: {@code 0x14} <br>
         * Payload: (none) <p>
         *
         * This message notifies a client to suspend sending messages to the
         * server until it receives further instruction (such as {@link
         * #RELOCATE_NOTIFICATION} or {@link #RESUME_MESSAGES}). The client
         * should send the acknowledgment {@link #SUSPEND_MESSAGES_COMPLETE} to
         * the server when it has suspended sending messages.  After the server
         * sends a {@code SUSPEND_MESSAGES} notification to the client, the
         * server may decide to drop messages from the client if it does not
         * receive the {@link #SUSPEND_MESSAGES_COMPLETE} acknowledgment in a
         * timely fashion. <p>
         *
         * This opcode was introduced in protocol version {@code 0x05}.
         */
        SGS_OPCODE_SUSPEND_MESSAGES = 0x14,

        /**
         * Acknowledgment of {@link #SUSPEND_MESSAGES} notification. Client to
         * server notification.
         * <br>
         * Opcode: {@code 0x15} <br>
         * Payload: (none) <p>
         *
         * This message notifies the server that the client has received the
         * {@link #SUSPEND_MESSAGES} notification.  Any messages received by the
         * server after this notification will be dropped, unless the server
         * has instructed the client to either resume messages or relocate its
         * client session to another node. <p>
         *
         * This opcode was introduced in protocol version {@code 0x05}.
         */
        SGS_OPCODE_SUSPEND_MESSAGES_COMPLETE = 0x15,

        /**
         * Resume messages notification. Server to client notification.
         * <br>
         * Opcode: {@code 0x16} <br>
         * Payload: (none) <p>
         *
         * This message notifies the client that it can resume sending messages
         * to the server. <p>
         *
         * This opcode was introduced in protocol version {@code 0x05}.
         */
        SGS_OPCODE_RESUME_MESSAGES = 0x16,

        /**
         * Relocate session notification. Server to client notification.
         * <br>
         * Opcode: {@code 0x17} <br>
         * Payload:
         * <ul>
         * <li> (String) hostname
         * <li> (int) port
         * <li> (ByteArray) relocationKey
         * </ul>
         *
         * This message notifies a client to relocate its session on the
         * current node to a new node. The client receiving this request should
         * shut down the connection to the original node and establish a
         * connection to the node indicated by the {@code hostname} and {@code
         * port} in the payload. The client should then attempt to reestablish
         * the client session with the server (without logging in) using the
         * {@code relocationKey} specified in the payload. <p>
         *
         * This opcode was introduced in protocol version {@code 0x05}.
         */
        SGS_OPCODE_RELOCATE_NOTIFICATION = 0x17,

        /**
         * Relocation request. Client requesting relocation to a server. <br>
         * Opcode: {@code 0x18} <br>
         * Payload:
         * <ul>
         * <li> (byte) protocol version
         * <li> (ByteArray) relocationKey
         * </ul>
         *
         * This message requests that the client's existing client session be
         * relocated to (and re-established with) the server. The {@code
         * relocationKey} must match the one that the client received in the
         * previous {@link #RELOCATE_NOTIFICATION} message.  If relocation is
         * successful, the server acknowledges the request with a {@link
         * #RELOCATE_SUCCESS} message containing a {@code reconnectionKey} for
         * reconnecting to the server. If relocation is not successful, a
         * {@link #RELOCATE_FAILURE} message is sent to the client.  If the
         * client receives a {@code RELOCATE_FAILURE} message, the client
         * should disconnect from the server. <p>
         *
         * This opcode was introduced in protocol version {@code 0x05}.
         */
        SGS_OPCODE_RELOCATE_REQUEST = 0x18,

        /**
         * Relocation success. Server response to a client's {@link
         * #RELOCATE_REQUEST}.
         * <br>
         * Opcode: {@code 0x19} <br>
         * Payload:
         * <ul>
         * <li> (ByteArray) reconnectionKey
         * </ul>
         * The {@code reconnectionKey} is an opaque reference that can be held by
         * the client for use in case the client is disconnected and wishes to
         * reconnect to the server with the same identity using a
         * {@link #RECONNECT_REQUEST}. <p>
         *
         * This opcode was introduced in protocol version {@code 0x05}.
         */
        SGS_OPCODE_RELOCATE_SUCCESS = 0x19,

        /**
         * Relocate failure. Server response to a client's {@link
         * #RELOCATE_REQUEST}.
         * <br>
         * Opcode: {@code 0x1a} <br>
         * Payload:
         * <ul>
         * <li> (String) reason
         * </ul>
         * This message indicates that the server rejects the {@link
         * #RELOCATE_REQUEST} for some reason, for example
         * <ul>
         * <li> session not relocating to the server
         * <li> relocation key mismatch
         * <li> a user with the same identity is already logged in
         * </ul> <p>
         *
         * This opcode was introduced in protocol version {@code 0x05}.
         */
        SGS_OPCODE_RELOCATE_FAILURE = 0x1a,

        /**
         * Reconnection request. Client requesting reconnect to a server. <br>
         * Opcode: {@code 0x20} <br>
         * Payload:
         * <ul>
         * <li> (byte) protocol version
         * <li> (ByteArray) reconnectionKey
         * </ul>
         * This message requests that the client be reconnected to an existing
         * client session with the server. The {@code reconnectionKey} must match
         * the one that the client received in the previous {@link #LOGIN_SUCCESS}
         * or {@link #RECONNECT_SUCCESS} message (if reconnection was performed
         * subsequent to login). If reconnection is successful, the server
         * acknowledges the request with a {@link #RECONNECT_SUCCESS} message
         * containing a new {@code reconnectionKey}. If reconnection is not
         * successful, a {@link #RECONNECT_FAILURE} message is sent to the client.
         * If the client receives a {@code RECONNECT_FAILURE} message, the client
         * should disconnect from the server.
         */
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
