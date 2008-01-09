/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
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
 * SGS Protocol constants.
 *
 * A protocol message is constructed as follows:
 *
 * (int)  payload length, not including this int
 * (byte) version number
 * (byte) service id
 * (byte) operation code
 * optional content, depending on the operation code.
 * 
 * A ByteArray is encoded as follows:
 *
 *   (unsigned short) number of bytes in the array
 *   (byte[])         the bytes in the array
 *
 * A String is encoded as follows:
 * 
 *   (unsigned short) number of bytes of modified UTF-8 encoded String
 *   (byte[])         String encoded in modified UTF-8 as described
 *                    in java.io.DataInput
 *
 * A CompactId is encoded as follows:
 *
 * The first byte of the ID's external form contains a length field
 * of variable size.  If the first two bits of the length byte are not
 * #b11, then the size of the ID is indicated as follows:
 *
 *   #b00: 14 bit ID (2 bytes total)
 *   #b01: 30 bit ID (4 bytes total)
 *   #b10: 62 bit ID (8 bytes total)
 *
 * If the first byte has the following format:
 *   1100nnnn
 * then the ID is contained in the next (8 + nnnn) bytes.
 *
 */

#ifndef SGS_PROTOCOL_H
#define SGS_PROTOCOL_H

#ifdef __cplusplus
extern "C" {
#endif

/*
 * The maximum length of any protocol message field defined as a
 * String or byte[]: 65535 bytes
 */
#define SGS_MSG_MAX_LENGTH 65535

/*
 * The size of the static portion of a message (i.e. with a message
 * payload of 0 bytes).
 */
#define SGS_MSG_INIT_LEN 7

/* The version number, currently 0x02. */
#define SGS_MSG_VERSION 2

typedef enum sgs_service_id {
    /* The Application service ID, 0x01. */
    SGS_APPLICATION_SERVICE = 0x01,
  
    /* The Channel service ID, 0x02. */
    SGS_CHANNEL_SERVICE = 0x02,
} sgs_service_id;

typedef enum sgs_opcode {
    /*
     * Login request from a client to a server.
     *
     * ServiceId: 0x01 (Application)
     * Opcode: 0x10
     *
     * Payload:
     *    (String) name
     *    (String) password
     */
    SGS_OPCODE_LOGIN_REQUEST = 0x10,

    /*
     * Login success.  Server response to a client's LOGIN_REQUEST.
     *
     * ServiceId: 0x01 (Application)
     * Opcode: 0x11
     *
     * Payload:
     *     (CompactId) sessionId
     *     (CompactId) reconnectionKey
     */
    SGS_OPCODE_LOGIN_SUCCESS = 0x11,

    /*
     * Login failure.  Server response to a client's LOGIN_REQUEST.
     *
     * ServiceId: 0x01 (Application)
     * Opcode: 0x12
     *
     * Payload:
     *     (String) reason
     */
    SGS_OPCODE_LOGIN_FAILURE = 0x12,

    /*
     * Reconnection request.  Client requesting reconnect to a server.
     *
     * ServiceId: 0x01 (Application)
     * Opcode: 0x20
     *
     * Payload:
     *     (CompactId) reconnectionKey
     */
    SGS_OPCODE_RECONNECT_REQUEST = 0x20,

    /*
     * Reconnect success.  Server response to a client's RECONNECT_REQUEST.
     *
     * ServiceId: 0x01 (Application)
     * Opcode: 0x21
     *
     * Payload:
     *     (CompactId) reconnectionKey
     */
    SGS_OPCODE_RECONNECT_SUCCESS = 0x21,

    /*
     * Reconnect failure.  Server response to a client's RECONNECT_REQUEST.
     *
     * ServiceId: 0x01 (Application)
     * Opcode: 0x22
     *
     * Payload:
     *     (String) reason
     */
    SGS_OPCODE_RECONNECT_FAILURE = 0x22,

    /*
     * Session message.  May be sent by the client or the server.
     * Maximum length is 64 KB minus one byte.
     * Larger messages require fragmentation and reassembly above
     * this protocol layer.
     *
     * ServiceId: 0x01 (Application)
     * Opcode: 0x30
     *
     * Payload:
     *     (long) sequence number
     *     (ByteArray) message
     */
    SGS_OPCODE_SESSION_MESSAGE = 0x30,

    /*
     * Logout request from a client to a server.
     *
     * ServiceId: 0x01 (Application)
     * Opcode: 0x40
     *
     * No payload.
     */
    SGS_OPCODE_LOGOUT_REQUEST = 0x40,

    /*
     * Logout success.  Server response to a client's LOGOUT_REQUEST.
     *
     * ServiceId: 0x01 (Application)
     * Opcode: 0x41
     *
     * No payload.
     */
    SGS_OPCODE_LOGOUT_SUCCESS = 0x41,

    /*
     * Channel join.  Server notifying a client that it has joined a channel.
     *
     * ServiceId: 0x02 (Channel)
     * Opcode: 0x50
     *
     * Payload:
     *     (String) channel name
     *     (CompactId) channel ID
     */
    SGS_OPCODE_CHANNEL_JOIN = 0x50,

    /**
     * Channel leave.  Server notifying a client that it has left a channel.
     *
     * ServiceId: 0x02 (Channel)
     * Opcode: 0x52
     *
     * Payload:
     *     (CompactId) channel ID
     */
    SGS_OPCODE_CHANNEL_LEAVE = 0x52,
    
    /*
     * Channel send request from a client to a server.
     *
     * ServiceId: 0x02 (Channel)
     * Opcode: 0x53
     *
     * Payload:
     *     (CompactId) channel ID
     *     (long) sequence number
     *     (short) number of recipients (0 = all)
     *   If number of recipients > 0, for each recipient:
     *       (CompactId) sessionId
     *       (ByteArray) message
     */
    SGS_OPCODE_CHANNEL_SEND_REQUEST = 0x53,

    /*
     * Channel message (sent from server to recipient on channel).
     *
     * ServiceId: 0x02 (Channel)
     * Opcode: 0x54
     *
     * Payload:
     *     (CompactId) channel ID
     *     (long) sequence number
     *     (CompactId) sender's sessionId
     *		(canonical CompactId of zero if sent by server)
     *     (ByteArray) message
     */
    SGS_OPCODE_CHANNEL_MESSAGE = 0x54,
} sgs_opcode;

#ifdef __cplusplus
}
#endif

#endif /* !SGS_PROTOCOL_H */
