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

import java.math.BigInteger;

/**
 * A listener that can be registered to be notified when a data conflict is
 * detected between nodes.
 *
 * @see	DataService#addDataConflictListener DataService.addDataConflictListener
 */
public interface DataConflictListener {

  /**
   * Notifies this listener that another node has made a conflicting access to
   * an object or name binding on this node.  The access is identified by
   * either a {@link BigInteger} that represents the object ID of an object, or
   * by a {@link String} that represents a bound name.  This method will be
   * called outside of a transaction. <p>

   * Note that the strings used to identify name bindings will typically differ
   * from the names specified when the bindings were created in the {@link
   * DataService} because the identifiers need to represent both application
   * and service bindings.  Implementations may chose to add different prefixes
   * to the original names, or use some other encoding, to produce unique
   * identifiers for both types of bindings. <p>
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
