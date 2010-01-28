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
 */

package com.sun.sgs.service.data;

import com.sun.sgs.app.ManagedReference;

/**
 * Utilities for the special needs of {@link SerializationHook}
 * implementations.
 */
public interface SerializationHookUtil {

    /**
     * Otherwise the same as {@link com.sun.sgs.app.DataManager#createReference},
     * but can be used while the managed objects are being serialized.
     */
    <T> ManagedReference<T> createReference(T object);
}
