/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.nodemap;

/**
 *
 * Thrown if the {@link NodeAssignPolicy} could not choose a node for
 * assignment because no nodes were available to it.
 * <p>
 * Note that this exception is very specific;  perhaps we'll want a more
 * general exception to be thrown from {@link NodeAssignPolicy#chooseNode}. 
 */
public class NoNodesAvailableException extends Exception {
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * Creates an instance of this class with the specified detail
     * message.
     *
     * @param	message the detail message or <code>null</code>
     */
    public NoNodesAvailableException(String message) {
        super(message);
    }
    
    /**
     * Creates an instance of this class with the specified detail
     * message and cause.
     *
     * @param	message the detail message or <code>null</code>
     * @param	cause the cause or <code>null</code>
     */
    public NoNodesAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
