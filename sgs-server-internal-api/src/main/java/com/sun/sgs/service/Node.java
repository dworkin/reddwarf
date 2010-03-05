/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
