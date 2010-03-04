/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
