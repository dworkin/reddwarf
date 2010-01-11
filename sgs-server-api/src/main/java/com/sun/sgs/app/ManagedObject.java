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

package com.sun.sgs.app;

import java.io.Serializable;

/**
 * A marker interface implemented by shared, persistent objects managed by
 * {@link DataManager}.  Classes that implement {@code ManagedObject} must also
 * implement {@link Serializable}, as should any non-managed objects they refer
 * to.  Any instances of {@code ManagedObject} that a managed object refers to
 * directly, or indirectly through non-managed objects, need to be referred to
 * through instances of {@link ManagedReference}. <p>
 *
 * Classes that implement {@code ManagedObject} should not provide {@code
 * writeReplace} or {@code readRestore} methods to designate replacement
 * objects during serialization.  Object replacement would interfere with the
 * object identity maintained by the {@code DataManager}, and is not
 * permitted. <p>
 *
 * Classes that implement {@code ManagedObject} can provide {@code readObject}
 * and {@code writeObject} methods to customize their serialization behavior,
 * but the {@code writeObject} methods should not perform calls to methods that
 * require a current transaction.
 *
 * @see		DataManager
 * @see		ManagedReference
 * @see		Serializable
 */
public interface ManagedObject { }
