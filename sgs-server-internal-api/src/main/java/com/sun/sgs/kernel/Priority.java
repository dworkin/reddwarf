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

package com.sun.sgs.kernel;


/**
 * The priority descriptions for the system.
 */
public enum Priority {

    /**
     * The highest priority for tasks.
     */
    HIGH        (256) {
        /** {@inheritDoc} */
        public Priority higher() { return HIGH; }
        /** {@inheritDoc} */
        public Priority lower()  { return MEDIUM_HIGH; }
    },

    /**
     * A medium high priority for tasks.
     */
    MEDIUM_HIGH (192) { 
        /** {@inheritDoc} */
        public Priority higher() { return HIGH; }
        /** {@inheritDoc} */
        public Priority lower()  { return MEDIUM; }
    },

    /**
     * The default priority for tasks.
     */
    MEDIUM      (128) {
        /** {@inheritDoc} */
        public Priority higher() { return MEDIUM_HIGH; }
        /** {@inheritDoc} */
        public Priority lower()  { return MEDIUM_LOW; }
    },

    /**
     * A medium low priority for tasks.
     */
    MEDIUM_LOW  (64) {
        /** {@inheritDoc} */
        public Priority higher() { return MEDIUM; }
        /** {@inheritDoc} */
        public Priority lower()  { return LOW; }
    },

    /**
     * The lowest priority for tasks.
     */
    LOW         (16) {
        /** {@inheritDoc} */
        public Priority higher() { return MEDIUM_LOW; }
        /** {@inheritDoc} */
        public Priority lower()  { return LOW; }
    };

    // The numeric value of this priority
    private final int value;
    
    /**
     * The private constructor that ensures no additional
     * <code>Priority</code> types can ever be created.
     */
    private Priority(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value that backs this priority.  These
     * values are only important in deciding the relative distance
     * between priorities.
     *
     * @return the numeric value of this priority
     */
    public int getValue() {
        return value;
    }

    /**
     * Returns the default <code>Priority</code> used by the system.
     *
     * @return the default <code>Priority</code>
     */
    public static Priority getDefaultPriority() {
        return MEDIUM;
    }

    /**
     * Returns the priority that is higher than this priority,
     * or this priority if this priority is the highest priority
     * defined.
     *
     * @return the priority higher than this priority, or this
     *         priority if this is the highest priority
     */
    public abstract Priority higher();

    /**
     * Returns the priority that is lower than this priority,
     * or this priority if this priority is the lowest priority
     * defined.
     *
     * @return the priority lower than this priority, or this
     *         priority if this is the lowest priority
     */
    public abstract Priority lower();

}
