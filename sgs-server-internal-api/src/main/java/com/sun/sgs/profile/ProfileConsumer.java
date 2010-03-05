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

package com.sun.sgs.profile;

import com.sun.sgs.profile.ProfileCollector.ProfileLevel;

/**
 * A profile consumer object is used to gather profiling data for a 
 * specific system or application component.  Each profile consumer
 * has a level associated with it, which determines how much data the
 * consumer will gather.
 * <p>
 * Profile consumers are factories for profile counters, operations,
 * and samples.  These profiling data objects come in three varieties:
 * <ul>
 * <li> aggregate, where the data is collected until it is explicitly cleared
 * <li> task-local, where data is collected on a task-by-task basis into a 
 *      {@link ProfileReport}
 * <li> aggregate and task-local, where the data is both aggregated until
 *      cleared and reported on a task-by-task basis
 * </ul>
 *
 * The factory methods include a parameter indicating the minimum profile
 * level the consumer must be set at for the created object's profiling
 * data to be collected.
 * 
 * @see ProfileCounter
 * @see ProfileOperation
 * @see ProfileSample
 */
public interface ProfileConsumer {

    /**
     * Set the local profiling level for this consumer.  Setting the global
     * profiling level via 
     * {@link ProfileCollector#setDefaultProfileLevel(ProfileLevel)} will
     * override this value.
     * 
     * @param level the profiling level
     */
    void setProfileLevel(ProfileLevel level);
    
    /**
     * Get the local profiling level for this consumer. Defaults to the
     * value of {@link ProfileCollector#getDefaultProfileLevel()}.
     * 
     * @return the profiling level
     */
    ProfileLevel getProfileLevel();
    
    /**
     * The valid types of profile operations, counters and samples that
     * can be created with the profile data factory methods.
     */
    enum ProfileDataType {
        /**  
         * Task local data reported through the {@link ProfileReport} to
         * {@code ProfileListener}s on a per-task basis.
         */
        TASK,
        /**
         * Data that is aggregated until it is explicitly cleared.
         */
        AGGREGATE,
        /**
         * Data that is both aggregated and reported on a per-task basis.
         */
        TASK_AND_AGGREGATE,
    }
    
    /**
     * Creates the named operation in this consumer.  If an operation has
     * already been created by this consumer with the same {@code name},
     * {@code type} and {@code minLevel}, it is returned.
     *
     * @param name the name of the operation
     * @param type the type of operation to create
     * @param minLevel the minimum level of profiling that must be set to report
     *              this operation
     *
     * @return a {@code ProfileOperation} to note operations
     * 
     * @throws IllegalArgumentException if an operation has already been
     *         created with this {@code name} but a different {@code type}
     *         or {@code minLevel}
     */
    ProfileOperation createOperation(String name,  
                                     ProfileDataType type,
                                     ProfileLevel minLevel);

    /**
     * Creates the named counter in this consumer.  If a counter has
     * already been created by this consumer with the same {@code name},
     * {@code type} and {@code minLevel}, it is returned.
     *
     * @param name the name of the counter
     * @param type the type of counter to create
     * @param minLevel the minimum level of profiling that must be set to update
     *              this counter
     *
     * @return a {@code ProfileCounter}
     * 
     * @throws IllegalArgumentException if a counter has already been
     *         created with this {@code name} but a different {@code type}
     *         or {@code minLevel}
     */
    ProfileCounter createCounter(String name, 
                                 ProfileDataType type,
                                 ProfileLevel minLevel);

    /**
     * Creates the named sample collection in this consumer.  If a sample has
     * already been created by this consumer with the same {@code name},
     * {@code type}, and {@code minLevel}, it is returned.  
     *
     * @param name a name or description of the sample collection
     * @param type the type of sample collection to create
     * @param minLevel the minimum level of profiling that must be set to record
     *              this sample  
     *
     * @return a {@code ProfileSample} that collects {@code long} data
     * 
     * @throws IllegalArgumentException if a sample collection has already been
     *         created with this {@code name} but a different {@code type} or
     *         {@code minLevel}
     */
    ProfileSample createSample(String name, 
                               ProfileDataType type,
                               ProfileLevel minLevel);

    /**
     * The profile consumer's unique name.
     *
     * @return the name of this consumer
     */
    String getName();
}
