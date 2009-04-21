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

package com.sun.sgs.impl.nio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.sgs.nio.channels.ThreadPoolFactory;

/**
 * Factory for obtaining the default {@code ThreadPoolFactory}.
 */
class DefaultThreadPoolFactory implements ThreadPoolFactory {

    /** A lazily-initialized singleton holder. */
    static final class LazyInstanceHolder {
        /** This class should not be instantiated. */
        private LazyInstanceHolder() { }
        
        /** The lazily-initialized singleton instance. */
        static DefaultThreadPoolFactory instance =
            new DefaultThreadPoolFactory();
    }

    /**
     * Returns the default {@code ThreadPoolFactory}.
     * 
     * @return the default {@code ThreadPoolFactory}
     */
    static DefaultThreadPoolFactory create() {
        return LazyInstanceHolder.instance;
    }

    /**
     * {@inheritDoc}
     */
    public ExecutorService newThreadPool() {
        return Executors.newCachedThreadPool(
            Executors.privilegedThreadFactory());
    }
}
