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

package com.sun.sgs.impl.service.data.store.cache;

/**
 * A {@code CompletionHandler} that reports node failure if an operation fails.
 * This class is part of the implementation of {@link CachingDataStore}.
 */
abstract class FailingCompletionHandler implements CompletionHandler {

    /** The caching data store. */
    private final CachingDataStore store;

    /**
     * Creates an instance of this class.
     *
     * @param	store the caching data store
     */
    FailingCompletionHandler(CachingDataStore store) {
	this.store = store;
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation reports the failure to the caching data store.
     */
    @Override
    public void failed(Throwable exception) {
	store.reportFailure(exception);
    }
}
