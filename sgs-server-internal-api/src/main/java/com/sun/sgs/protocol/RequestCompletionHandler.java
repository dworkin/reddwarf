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

import java.util.concurrent.Future;

/**
 * A completion handler for a request carried out by a {@link
 * SessionProtocolHandler}.
 *
 * @param <V>	the type of the request's result
 */
public interface RequestCompletionHandler<V> {
    
    /**
     * Notifies this handler that the request associated with this
     * handler is complete with the specified {@code result}.
     *
     * @param	result a future containing the result of the
     * 		request 
     */
    void completed(Future<V> result);
}
