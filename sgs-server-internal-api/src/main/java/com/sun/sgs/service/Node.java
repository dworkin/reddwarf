/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.service;

/**
 * An abstraction for node information, used in conjunction
 * with the {@link WatchdogService} and {@link NodeListener}s.
 */
public interface Node {

    /**
     * Health of a node.
     */
    public enum Health {


        /**
         * The node is operating normally and is available for additional
         * work.
         */
        GREEN,

        /**
         * The node is operating normally but is not available for
         * additional work.
         */
        YELLOW,

        /**
         * The node is operational, but is in need of attention such as
         * offloading some of its work.
         */
        ORANGE,

        /**
         * The node has failed, been shutdown, or its state is unknown.
         */
        RED;

        /**
         * Returns {@code true} if this health is worse than the specified
         * health.
         *
         * @param health a health object to compare
         *
         * @return {@code true} if this health is worse than the specified
         * health
         */
        public boolean worseThan(Health health) {
            return compareTo(health) > 0;
        }

        /**
         * Returns {@code true} if this health represents an operational state.
         *
         * @return {@code true} if this health represents an operational state
         */
        public boolean isAlive() {
            return !equals(Health.RED);
        }
    }

    /**
     * Returns the node ID, which is always non-negative.
     *
     * @return the node ID
     */
    long getId();

    /** 
     * Returns this node's host name.
     *
     * @return	this node's host name
     */
    String getHostName();
    
    /**
     * Returns {@code true} if the node is known to be alive, and
     * {@code false} if the node is thought to have failed or is
     * unknown.
     *
     * @return	{@code true} if the node is alive, and {@code false}
     * 		otherwise
     */
    boolean isAlive();

    /**
     * Returns the health of the node.
     *
     * @return	the node's health
     */
    Health getHealth();
}
