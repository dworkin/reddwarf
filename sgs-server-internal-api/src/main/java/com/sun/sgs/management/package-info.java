/*
 * Copyright 2010 The RedDwarf Authors.  All rights reserved
 * Portions of this distribution have been modified as part of RedDwarf
 * The source code is governed by a GPLv2 license with "Classpath"
 * exception that can be found in the LICENSE file.
 */
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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

/**
 * Provides the RedDwarf management interfaces exposed via JMX.
 * <p>
 * <b>
 * These interfaces should not be considered stable;  they will evolve
 * as we gather more experience using JMX with RedDwarf.  In
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
