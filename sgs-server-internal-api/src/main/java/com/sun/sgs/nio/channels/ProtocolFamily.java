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
 * Represents a family of communication protocols.
 * <p>
 * [[Note: JSR-203 creates this interface in {@code java.net}]]
 */
public interface ProtocolFamily {

    /**
     * Returns the name of the protocol family.
     * 
     * @return the name of the protocol family
     */
    String name();
}
