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
 * --
 */

package com.sun.sgs.impl.service.nodemap.affinity;

import java.io.Serializable;

/**
 * Abstract affinity group.
 */
public abstract class AbstractAffinityGroup implements AffinityGroup, Serializable {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;
    
    /** The identity of the affinity group. */
    protected final long id;
    
    /** The generation of this affinity set. */
    protected final long generation;

    /**
     * Constructs a new affinity group with the given ID and generation number.
     *
     * @param id the affinity group identity
     * @param generation the generation number of this group
     */
    public AbstractAffinityGroup(long id, long generation) {
        this.id = id;
        this.generation = generation;
    }

    /** {@inheritDoc} */
    public long getId() {
        return id;
    }

    /** {@inheritDoc} */
    public long getGeneration() {
        return generation;
    }
}
