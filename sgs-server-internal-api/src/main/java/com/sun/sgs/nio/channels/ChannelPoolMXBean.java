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

package com.sun.sgs.nio.channels;

/**
 * The management interface for a channel pool.
 * <p>
 * A Java virtual machine has several instances of the implementation class
 * of this interface. A class implementing this interface is an
 * {@code javax.management.MXBean} that is obtained by calling the
 * {@link Channels#getChannelPoolMXBeans()} method
 * [[NOT IMPLEMENTED: or from the platform MBeanServer]].
 * <p>
 * The {@code ObjectName} for uniquely identifying the MXBean of this type
 * within an {@code MBeanServer} is:
 * <blockquote>
 * <code>java.nio:type=ChannelPool,name=<i>pool name</i></code> 
 * </blockquote>
 */
public interface ChannelPoolMXBean {

    /**
     * Returns the name representing this channel pool.
     * 
     * @return the name of this channel pool
     */
    String getName();

    /**
     * Returns an estimate of the number of open channels in this pool.
     * <p>
     * The number of open channels is the number of channels opened since
     * the Java virtual machine has started execution minus the number of
     * channels that have been closed.
     * 
     * @return an estimate of the number of open channels in this pool
     */
    long getCount();
}
