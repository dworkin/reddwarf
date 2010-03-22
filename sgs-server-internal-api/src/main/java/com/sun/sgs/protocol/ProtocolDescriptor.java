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
