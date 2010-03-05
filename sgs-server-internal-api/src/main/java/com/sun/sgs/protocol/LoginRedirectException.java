/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
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
 *
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
