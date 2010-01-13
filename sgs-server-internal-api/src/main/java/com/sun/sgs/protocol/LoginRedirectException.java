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

package com.sun.sgs.protocol;

import java.util.Set;

/**
 * An exception that indicates a login should be redirected to the node
 * returned by this exception's {@link #getNodeId getNodeId} method.
 */
public class LoginRedirectException extends Exception {

    /** The serial version for this class. */
    private static final long serialVersionUID = 1L;

    /** The node ID. */
    private final long nodeId;

    /** The protocol descriptors for the {@code node}. */
    private final Set<ProtocolDescriptor> descriptors;

    /**
     * Constructs an instance with the specified {@code nodeId} and
     * protocol {@code descriptors}.
     *
     * @param	nodeId a node ID
     * @param	descriptors a collection of protocol descriptors
     *		supported by the specified {@code node}, or {@code null}
     */
    public LoginRedirectException(
	long nodeId, Set<ProtocolDescriptor> descriptors)
    {
	this(nodeId, descriptors, null);
    }

    /**
     * Constructs an instance with the specified {@code nodeId}, {@code
     * descriptors} and detail {@code message}.
     *
     * @param	nodeId a node ID
     * @param	descriptors a collection of protocol descriptors
     *		supported by the specified {@code node}, or {@code null}
     * @param	message a detail message, or {@code null}
     */
    public LoginRedirectException(
	long nodeId, Set<ProtocolDescriptor> descriptors,
	String message)
    {
	this(nodeId, descriptors, message, null);
    }

    /**
     * Constructs an instance with the specified {@code nodeId}, detail
     * {@code message}, and {@code cause}.
     *
     * @param	nodeId a node ID
     * @param	descriptors a collection of protocol descriptors
     *		supported by the specified {@code node}, or {@code null}
     * @param	message a detail message, or {@code null}
     * @param	cause the cause of this exception, or {@code null}
     */
    public LoginRedirectException(
	long nodeId, Set<ProtocolDescriptor> descriptors,
	String message, Throwable cause)
    {
	super(message, cause);
	if (nodeId < 0) {
	    throw new NullPointerException("bad node ID");
	} else if (descriptors == null) {
            throw new NullPointerException("null descriptors");
        }
	this.nodeId = nodeId;
	this.descriptors = descriptors;
    }

    /**
     * Returns the ID of the node to which the login should be redirected.
     *
     * @return	the ID of the node to which the login should be redirected
     */
    public long getNodeId() {
	return nodeId;
    }
    
    /**
     * Returns a collection of protocol descriptors supported by
     * the node whose ID is returned by {@link #getNodeId getNodeId}.
     *
     * @return	a {@code Set} of protocol descriptors
     */
    public Set<ProtocolDescriptor> getProtocolDescriptors() {
	return descriptors;
    }
}
