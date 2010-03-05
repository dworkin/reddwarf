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

import java.io.Serializable;

/**
 * A communication protocol descriptor. Classes that implement {@code
 * ProtocolDescriptor} must also implement {@link Serializable}, and must
 * <i>not</i> implement {@code ManagedObject} or contain any objects that
 * implement {@code ManagedObject}. An instance of {@code
 * ProtocolDescriptor} should also be immutable.
 */
public interface ProtocolDescriptor {
    
    /**
     * Returns {@code true} if the specified {@code descriptor} represents
     * a protocol supported by the protocol that this descriptor
     * represents, and returns {@code false} otherwise. The determination
     * of whether the given protocol is supported is protocol specific.
     *
     * @param	descriptor a protocol descriptor
     * @return {@code true} if the specified {@code descriptor} represents
     * 		a protocol supported by the protocol that  this descriptor
     *		represents, and {@code false} otherwise
     */
    boolean supportsProtocol(ProtocolDescriptor descriptor);
}
