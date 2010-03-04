/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
