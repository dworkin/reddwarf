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
