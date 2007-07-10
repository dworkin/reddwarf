/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

/*
 * The file declares constants relevant to the SGS network wire protocol.  It is
 * essentially a straight C-port of
 * com.sun.sgs.protocol.simple.SimpleSgsProtocol.
 */

/**
 * SGS Protocol constants.
 * <p>
 * A protocol message is constructed as follows:
 * <ul>
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
 * in {@link DataInput}
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

#ifndef SGS_WIRE_PROTOCOL_H
#define SGS_WIRE_PROTOCOL_H

/**
 * The maximum length of any protocol message field defined as a
 * String or byte[]: 65535 bytes
 */
#define SGS_MSG_MAX_LENGTH 65535

/**
 * This is the size of the static portion of a message (i.e. with a message
 * payload of 0 bytes).
 */
#define SGS_MSG_INIT_LEN 7

/* The version number */
#define SGS_MSG_VERSION 1

typedef enum sgs_service_id {
    /* Application service ID */
    SGS_APPLICATION_SERVICE = 0x01,
  
    /* Channel serivce ID */
    SGS_CHANNEL_SERVICE = 0x02,
} sgs_service_id;

typedef enum sgs_opcode {
    /**
     * Login request.
     * <ul>
     * <li> (String) name
     * <li> (String) password
     * </ul>
     */
    SGS_OPCODE_LOGIN_REQUEST = 0x10,

    /**
     * Login success (login request acknowledgment).
     * <ul>
     * <li> (CompactId) sessionId
     * <li> (CompactId) reconnectionKey
     * </ul>
     */
    SGS_OPCODE_LOGIN_SUCCESS = 0x11,

    /**
     * Login failure (login request acknowledgment).
     * <ul>
     * <li> (String) reason
     * </ul>
     */
    SGS_OPCODE_LOGIN_FAILURE = 0x12,

    /**
     * Reconnection request.
     * <ul>
     * <li> (CompactId) reconnectionKey
     * </ul>
     */
    SGS_OPCODE_RECONNECT_REQUEST = 0x20,

    /**
     * Reconnect success (reconnection request acknowledgment).
     * <ul>
     * <li> (CompactId) reconnectionKey
     * </ul>
     */
    SGS_OPCODE_RECONNECT_SUCCESS = 0x21,

    /**
     * Reconnect failure (reconnection request acknowledgment).
     * <ul>
     * <li> (String) reason
     * </ul>
     */
    SGS_OPCODE_RECONNECT_FAILURE = 0x22,

    /**
     * Session message.  Maximum length is 64 KB minus one byte.
     * Larger messages require fragmentation and reassembly above
     * this protocol layer.
     *
     * <ul>
     * <li> (long) sequence number
     * <li> (ByteArray) message
     * </ul>
     */
    SGS_OPCODE_SESSION_MESSAGE = 0x30,

    /**
     * Logout request.
     */
    SGS_OPCODE_LOGOUT_REQUEST = 0x40,

    /**
     * Logout success (logout request acknowledgment).
     */
    SGS_OPCODE_LOGOUT_SUCCESS = 0x41,

    /**
     * Channel join.
     * <ul>
     * <li> (String) channel name
     * <li> (CompactId) channel ID
     * </ul>
     */
    SGS_OPCODE_CHANNEL_JOIN = 0x50,

    /**
     * Channel leave.
     * <ul>
     * <li> (CompactId) channel ID
     * </ul>
     */
    SGS_OPCODE_CHANNEL_LEAVE = 0x52,
    
    /**
     * Channel send request.
     * <ul>
     * <li> (CompactId) channel ID
     * <li> (long) sequence number
     * <li> (short) number of recipients (0 = all)
     * <li> If number of recipients &gt, 0, for each recipient:
     * <ul>
     * <li> (CompactId) sessionId
     * </ul>
     * <li> (ByteArray) message
     * </ul>
     */
    SGS_OPCODE_CHANNEL_SEND_REQUEST = 0x53,

    /**
     * Channel message (to recipient on channel).
     * <ul>
     * <li> (CompactId) channel ID
     * <li> (long) sequence number
     * <li> (CompactId) sender's sessionId
     *(canonical CompactId of zero if sent by server)
     * <li> (ByteArray) message
     * </ul>
     */
    SGS_OPCODE_CHANNEL_MESSAGE = 0x54,
} sgs_opcode;

#endif  /** #ifndef SGS_WIRE_PROTOCOL_H */
