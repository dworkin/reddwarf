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

package com.sun.sgs.impl.service.nodemap.affinity.graph;

import com.sun.sgs.auth.Identity;

/**
 * Vertices for a Label Propagation Algorithm graph.
 * Labels change as we iterate through the algorithm, and once the
 * algorithm has converged after several iterations, vertices with
 * the same label are in the same cluster.
 * <p>
 * We are using the identity's hash code for the label for faster
 * comparisons.  This has some risk of us clustering identities
 * that actually are not related, because hash codes are not guaranteed
 * to be unique.
 */
public final class LabelVertex {
    /** The label for this vertex. */
    private volatile int label;
    /** The identity this vertex represents.  */
    private final Identity id;
    /** The cached hashcode for this object. */
    private volatile int hashCode = 0;

    /**
     * Constructs a new vertex representing the given {@code id} and
     * initializes the label information to the hashcode of the {@code id}.
     * @param id the identity this vertex represents
     */
    public LabelVertex(Identity id) {
        this.id = id;
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
        if (o ==  this) {
            return true;
        }
        if (!(o instanceof LabelVertex)) {
            return false;
        }
        LabelVertex oVertex = (LabelVertex) o;
        return id.equals(oVertex.getIdentity());
    }

    /** {@inheritDoc} */
    public int hashCode() {
        // If the id is simply a number, it's very useful for testing/debugging
        // to use that number as the label.
        if (hashCode == 0) {
            try {
                hashCode = Integer.valueOf(id.getName());
            } catch (NumberFormatException e) {
                hashCode = id.hashCode();
            }
        }
        return hashCode;
    }

    /** {@inheritDoc} */
    public String toString() {
        return "[" + id.toString() + ":" + label + "]";
    }

    /**
     * Sets the label to a new value.
     * @param newLabel the new label value
     */
    public void setLabel(int newLabel) {
        label = newLabel;
    }

    /**
     * Returns the current label for this vertex.
     * @return the current label for this vertex
     */
    public int getLabel() {
        return label;
    }

    /**
     * Returns the identity this vertex represents.
     * @return the identity this vertex represents
     */
    public Identity getIdentity() {
        return id;
    }
}
