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

import com.sun.sgs.auth.Identity;

/**
 * Vertices for the Label Propagation Algorithm graph.
 * Labels change as we iterate through the algorithm, and once it has
 * the algorithm has converged after several iterations, vertices
 * with the same label are in the same cluster.
 * <p>
 * We are using the identity's hash code for the label for faster
 * comparisions.  This has some risk of us clustering identities
 * that actually are not related, because hash codes are not guaranteed
 * to be unique.
 */
public final class LabelVertex extends AffinityVertex {
    private volatile int label;

    /**
     * Constructs a new vertex repesenting the given {@code id} and
     * initializes the label information to the hashcode of the {@code id}.
     * @param id the identity this vertex represents
     */
    public LabelVertex(Identity id) {
        super(id);
        initializeLabel();
    }

    /**
     * Sets the label to the initial value.
     */
    public void initializeLabel() {
        label = hashCode();
    }

    /**
     * {@inheritDoc}
     * <p>
     * We do no take the current label into account when calculating equals.
     */
    public boolean equals(Object o) {
        return super.equals(o);
    }
    /** {@inheritDoc} */
    public int hashCode() {
        return super.hashCode();
    }

    /** {@inheritDoc} */
    public String toString() {
        return "[" + getIdentity().toString() + ":" + label + "]";
    }

    // Package private methods, used by the label propagation algorithm.
    /**
     * Sets the label to a new value.
     * @param newLabel the new label value
     */
    void setLabel(int newLabel) {
        label = newLabel;
    }

    /**
     * Returns the current label for this vertex.
     * @return the current label for this vertex
     */
    int getLabel() {
        return label;
    }
}
