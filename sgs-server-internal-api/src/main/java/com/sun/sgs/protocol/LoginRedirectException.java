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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.protocol;

import com.sun.sgs.service.Node;
import java.util.Set;

/**
 * An exception that indicates a login should be redirected to the node
 * returned by this exception's {@link #getNode getNode} method.
 */
public class LoginRedirectException extends Exception {

    /** The serial version for this class. */
    private static final long serialVersionUID = 1L;

    /** The node. */
    private final Node node;

    /** The protocol descriptors for the {@code node}. */
    private final Set<ProtocolDescriptor> descriptors;

    /**
     * Constructs and instance with the specified {@code node} and
     * protocol {@code descriptors}.
     *
     * @param	node a node
     * @param	descriptors a collection of protocol descriptors
     *		supported by the specified {@code node}, or {@code null}
     */
    public LoginRedirectException(
	Node node, Set<ProtocolDescriptor> descriptors)
    {
	this(node, descriptors, null);
    }
    
    /**
     * Constructs and instance with the specified {@code node}, {@code
     * descriptors} and detail {@code message}.
     *
     * @param	node a node
     * @param	descriptors a collection of protocol descriptors
     *		supported by the specified {@code node}, or {@code null}
     * @param	message a detail message, or {@code null}
     */
    public LoginRedirectException(
	Node node, Set<ProtocolDescriptor> descriptors,
	String message)
    {
	this(node, descriptors, message, null);
    }
    
    /**
     * Constructs and instance with the specified {@code node}, detail
     * {@code message}, and {@code cause}.
     *
     * @param	node a node
     * @param	descriptors a collection of protocol descriptors
     *		supported by the specified {@code node}, or {@code null}
     * @param	message a detail message, or {@code null}
     * @param	cause the cause of this exception, or {@code null}
     */
    public LoginRedirectException(
	Node node, Set<ProtocolDescriptor> descriptors,
	String message, Throwable cause)
    {
	super(message, cause);
	if (node == null) {
	    throw new NullPointerException("null node");
	} else if (descriptors == null) {
            throw new NullPointerException("null descriptors");
        }
	this.node = node;
	this.descriptors = descriptors;
    }

    /**
     * Returns the node to which the login should be redirected.
     *
     * @return	the node to which the login should be redirected
     */
    public Node getNode() {
	return node;
    }
    
    /**
     * Returns a collection of protocol descriptors supported by
     * the node returned by {@link #getNode getNode}.
     *
     * @return	a {@code Set} of protocol descriptors
     */
    public Set<ProtocolDescriptor> getProtocolDescriptors() {
	return descriptors;
    }
}
