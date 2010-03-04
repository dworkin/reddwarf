/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.nio.channels;

/**
 * Defines the standard family of communication protocols.
 * <p>
 * [[Note: JSR-203 creates this interface in {@code java.net}]]
 */
public enum StandardProtocolFamily implements ProtocolFamily {
    /** Internet Protocol Version 4 (IPv4). */
    INET,

    /** Internet Protocol Version 6 (IPv6). */
    INET6;
}
