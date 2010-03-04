/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap.affinity.graph;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An edge with an associated weight.
 */
public class WeightedEdge {
    /** The weight. */
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
        weight = new AtomicLong(value);
    }

    /**
     * Returns the weight of this edge.  Edge weights represent the number
     * of times both vertices that this edge connects have accessed the
     * same object.  For example, if both vertices access object1 two times,
     * the returned weight is {@code 2}.  If they both access a different
     * object, object2, once, the weight will now be {@code 3}.
     *
     * @return the weight of this edge
     */
    public long getWeight() {
        return weight.get();
    }
    
    /**
     * Increments the edge weight.
     */
    public void incrementWeight() {
        weight.incrementAndGet();
    }

    /**
     * Adds the given {@code value} to the edge weight.
     * @param value the value to add to the weight
     */
    public void addWeight(long value) {
        weight.addAndGet(value);
    }

    /** {@inheritDoc} */
    public String toString() {
        return "E:" + weight;
    }
}
