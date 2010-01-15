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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */
package com.sun.sgs.service;

/**
 * Thrown when a node ID used in the system is unknown.
 */
public class UnknownNodeException extends Exception {
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;
    
    /**
     * Constructs an instance of <code>UnknownNodeException</code> with 
     * the specified detail message.
     * 
     * @param message the detail message.
     */
    public UnknownNodeException(String message) {
        super(message);
    }
    
    /**
     * Creates an instance of this class with the specified detail message and
     * cause.
     *
     * @param	message the detail message or {@code null}
     * @param	cause the cause or {@code null}
     */
    public UnknownNodeException(String message, Throwable cause) {
	super(message, cause);
    }
}
