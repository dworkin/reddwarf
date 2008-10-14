/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
        TASK_AGGREGATE,
    }
    
    /**
     * Creates the named operation in this consumer.  If an operation with
     * this name has already been created by this consumer, it is returned, 
     * regardless of the supplied {@code type} and {@code minLevel}. 
     *
     * @param name the name of the operation
     * @param type the type of operation to create
     * @param minLevel the minimum level of profiling that must be set to report
     *              this operation
     *
     * @return a {@code ProfileOperation} to note operations
     */
    ProfileOperation createOperation(String name,  
                                     ProfileDataType type,
                                     ProfileLevel minLevel);

    /**
     * Creates the named counter in this consumer.  If an operation with
     * this name has already been created by this consumer, it is returned,
     * regardless of the supplied {@code type} and {@code minLevel}.
     *
     * @param name the name of the counter
     * @param type the type of operation to create
     * @param minLevel the minimum level of profiling that must be set to update
     *              this counter
     *
     * @return a {@code ProfileCounter}
     */
    ProfileCounter createCounter(String name, 
                                 ProfileDataType type,
                                 ProfileLevel minLevel);

    /**
     * Creates the named sample collection in this consumer.  If a
     * {@code ProfileSample} with this name has already been created by this
     * consumer, it is returned, regardless of the supplied {@code type} and 
     * {@code minLevel}.
     * <p>
     *  A negative value for {@code maxSamples} indicates an infinite
     *  number of samples.  Note that for non-task-local sample
     *  sources, this is a potential memory leak as the number of
     *  samples increases.  Once the limit of samples has been
     *  reached, older samples will be dropped to make room for the
     *  newest samples.
     *
     * @param name a name or description of the sample type
     * @param type the type of operation to create
     * @param maxSamples the maximum number of samples to keep.
     * @param minLevel the minimum level of profiling that must be set to record
     *              this sample  
     *
     * @return a {@code ProfileSample} that collects {@code long} data
     */
    ProfileSample createSample(String name, 
                               ProfileDataType type,
                               long maxSamples, 
                               ProfileLevel minLevel);

    /**
     * The profile consumer's unique name.
     *
     * @return the name of this consumer
     */
    String getName();
}
