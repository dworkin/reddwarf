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

package com.sun.sgs.impl.service.nodemap.affinity;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An edge with an associated weight.
 */
public class WeightedEdge {
    private final AtomicLong weight;

    /**
     * Constructs a weighted edge with an initial weight of {@code 1}.
     */
    public WeightedEdge() {
        this(1);
    }

    /**
     * Constructs a weighted edge with an initial weight of {@code value}.
     * @param value the initial weight
     */
    public WeightedEdge(long value) {
        super();
        weight = new AtomicLong(value);
    }

    /**
     * Returns the weight of this edge, which is the number of times both
     * vertices accessed the object since this edge was created.
     *
     * @return the weight of this edge
     */
    public long getWeight() {
        return weight.get();
    }
    
    /** {@inheritDoc} */
    public String toString() {
        return "E:" + weight;
    }
    
    /*  Package private methods */
    void incrementWeight() {
        weight.incrementAndGet();
    }
    
    void addWeight(long value) {
        weight.addAndGet(value);
    }
}
