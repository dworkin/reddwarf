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

/**
 * Provides the Project Darkstar management interfaces exposed via JMX.
 * <p>
 * <b>
 * These interfaces should not be considered stable;  they will evolve
 * as we gather more experience using JMX with Project Darkstar.  In
 * particular, more data and statistics might be exposed and notifications
 * might be added, and some might be removed if we find they are not
 * generally useful.
 * </b>
 * <p>
 * By convention, only one of each of these objects will be registered with the 
 * platform MBean server.  The {@code ObjectName} for uniquely identifying 
 * MBeans in this package is the field {@code MXBEAN_NAME} within each MBean.
 * <p>
 * For more information, please refer to the <a href=
 * "http://java.sun.com/javase/6/docs/technotes/guides/management/toc.html">
 * Java SE Monitoring and Management Guide</a>, especially Chapter 2, 
 * Monitoring and Management Using JMX Technology.
 * <p>
 * Profiling for services can be dynamically enabled with 
 * {@link com.sun.sgs.management.ProfileControllerMXBean#setConsumerLevel 
 * ProfileControllerMXBean.setConsumerLevel}.  
 * The names of the profile consumers enabled in the system can be found with
 * {@link com.sun.sgs.management.ProfileControllerMXBean#getProfileConsumers
 * ProfileControllerMXBean.getProfileConsumers}.
 * <p>
 * Additional information on the use of MBeans, with simple examples, can be
 * found <a href=
 * "http://java.sun.com/javase/6/docs/api/java/lang/management/package-summary.html?is-external=true#examples">
 * here</a> in the {@code java.lang.management} package.
 */
package com.sun.sgs.management;
