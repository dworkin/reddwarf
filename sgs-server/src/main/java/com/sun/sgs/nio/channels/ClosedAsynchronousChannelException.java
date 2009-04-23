/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.nio.channels;

/**
 * Unchecked exception thrown when an attempt is made to initiate an
 * asynchronous operation on an asynchronous channel that is closed.
 */
public class ClosedAsynchronousChannelException
    extends IllegalStateException
{
    /** The version of the serialized representation of this class. */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of this class.
     */
    public ClosedAsynchronousChannelException() {
        super();
    }
}
