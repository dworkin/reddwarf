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

import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread factory that creates daemon threads with names that use a prefix
 * followed by a instance number.
 */
class NamedThreadFactory implements ThreadFactory {

    /** The prefix for each name. */
    private final String namePrefix;

    /** The next instance number. */
    private final AtomicInteger nextNum = new AtomicInteger(1);

    /**
     * Creates an instance of this class.
     *
     * @param	namePrefix the prefix to use for names
     */
    NamedThreadFactory(String namePrefix) {
	checkNull("namePrefix", namePrefix);
	this.namePrefix = namePrefix;
    }

    /* -- Implement ThreadFactory -- */

    /** {@inheritDoc} */
    @Override
    public Thread newThread(Runnable r) {
	Thread t = new Thread(r);
	t.setName(namePrefix + nextNum.getAndIncrement());
	t.setDaemon(true);
	return t;
    }
}
