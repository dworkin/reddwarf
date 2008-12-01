/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.protocol;

/**
 * A protocol.
 */
public interface Protocol {

    /**
     * Returns the descriptor for this protocol. Multiple calls to this
     * method may return the same object.
     * 
     * @return the descriptor for this protocol
     */
    ProtocolDescriptor getDescriptor();
    
    /**
     * Shutdown the protocol. The actions of this method are implementation
     * dependent, but typically involve closing open network connections,
     * releasing system resources, etc.. All shutdown activity is
     * synchronous with this call.
     */
    void shutdown();
}
