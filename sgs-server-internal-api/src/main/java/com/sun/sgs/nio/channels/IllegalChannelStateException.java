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
 * Unchecked exception received when a channel operation is attempted that
 * has previously completed due to a timeout.
 * <p>
 * [[Not specified by JSR-203, but referenced in the JSR-203 Javadoc]]
 */
public class IllegalChannelStateException
    extends IllegalStateException
{
    /** The version of the serialized representation of this class. */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of this class.
     */
    public IllegalChannelStateException() {
        super();
    }
}
