/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.impl.io;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A {@link ThreadFactory} that creates with {@code setDaemon(true)}.
 */
final class DaemonThreadFactory implements ThreadFactory {

    private final ThreadFactory defaultThreadFactory;

    /**
     * Default constructor.
     */
    public DaemonThreadFactory() {
        defaultThreadFactory = Executors.defaultThreadFactory();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation creates daemon threads.
     *
     * @see Thread#setDaemon
     */
    public Thread newThread(Runnable r) {
        Thread t = defaultThreadFactory.newThread(r);
        t.setDaemon(true);
        return t;
    }
}
