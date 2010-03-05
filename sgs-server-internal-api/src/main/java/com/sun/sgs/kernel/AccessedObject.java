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

package com.sun.sgs.kernel;

import com.sun.sgs.kernel.AccessReporter.AccessType;


/**
 * An interface that provides details of a single object access. Two accessed
 * objects are identical if both were reported by an {@link AccessReporter}
 * obtained by registering an access source with a single {@link
 * AccessCoordinator} using the same source name, and the values returned by
 * {@link #getObjectId()} and {@link #getAccessType()} are equal.
 */
public interface AccessedObject {

    /**
     * Returns the identifier for the accessed object.
     *
     * @return the identifier for the accessed object
     */
    Object getObjectId();

    /**
     * Returns the type of access requested.
     *
     * @return the {@code AccessType}
     */
    AccessType getAccessType();

    /**
     * Returns the supplied description of the object, if any.
     *
     * @return the associated description, or {@code null}
     *
     * @see AccessReporter#setObjectDescription(Object,Object)
     */
    Object getDescription();

    /**
     * Returns the name of the source that reported this object access.
     *
     * @return the object's source
     */
    String getSource();

}
