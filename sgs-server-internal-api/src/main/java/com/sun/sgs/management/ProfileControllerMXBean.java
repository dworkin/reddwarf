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

package com.sun.sgs.management;

import com.sun.sgs.profile.ProfileCollector.ProfileLevel;

/**
 * The management information for this node's profiling data.  The profiling
 * levels for each of the individual profile consumers can be modified through 
 * this interface.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface ProfileControllerMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs:type=ProfileController";
    
    // Maybe add a way to add/remove listeners?
    
    /**
     * Gets the default profile level for all newly created consumers.
     * 
     * @return the default profile level for consumer creation
     */
    ProfileLevel getDefaultProfileLevel();
    
    /**
     * Sets the default profile level for all newly created consumers.
     * 
     * @param level the default profile level for consumer creation
     */
    void setDefaultProfileLevel(ProfileLevel level);
    
    /**
     * Gets the names of all profile consumers in the system.
     * 
     * @return the names of all the profile consumers
     */
    String[] getProfileConsumers();
    
    /**
     * Gets the current profile level of the named consumer.
     * 
     * @param consumer the consumer name
     *
     * @return the profile level for the named consumer
     * @throws IllegalArgumentException if the consumer has not been created
     */
    ProfileLevel getConsumerLevel(String consumer);
    
    /**
     * Sets the current profile level of the named consumer.
     * 
     * @param consumer the consumer name
     * @param level the profile level
     * 
     * @throws IllegalArgumentException if the consumer has not been created
     */
    void setConsumerLevel(String consumer, ProfileLevel level);
}
