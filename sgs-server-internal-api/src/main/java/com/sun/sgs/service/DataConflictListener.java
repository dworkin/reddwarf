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

package com.sun.sgs.service;

import java.math.BigInteger;

/**
 * A listener that can be registered to be notified when a data conflict is
 * detected between nodes.  Invocations to the {@link #nodeConflictDetected
 * nodeConflictDetected} method are made outside of a transaction.
 *
 * @see	DataService#addDataConflictListener DataService.addDataConflictListener
 */
public interface DataConflictListener {

  /**
   * Notifies this listener that another node has made a conflicting access to
   * an object or name binding on this node.  The access is identified by
   * either a {@link BigInteger} that represents the object ID of an object, or
   * by a {@link String} that represents a bound name. <p>
   *
   * The string used to identify name bindings will not match the value
   * specified when creating the name binding in the {@link DataService}
   * because value needs to represent both application and service
   * bindings. <p>
   *
   * Callers of this method are permitted to make calls to multiple listeners
   * from a single thread, so implementations of this method should make sure
   * to return in a timely manner.
   *
   * @param	accessId the identifier for the data accessed
   * @param	nodeId the identifier for the remote node performing the access
   * @param	forUpdate {@code true} if the conflicting  access was for
   *		update, else {@code false} if it was for read
   */
  void nodeConflictDetected(Object accessId, long nodeId, boolean forUpdate);
}
