/*
 * Copyright 2008 Sun Microsystems, Inc.
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

/**
 * Provides the Project Darkstar management interfaces exposed via JMX.
 * <p>
 * By convention, only one of these objects will be registered with the 
 * platform MBean server.  The {@code ObjectName} for uniquely identifying 
 * MBeans in this package is the field {@code MXBEAN_NAME} within each MBean.
 * <p>
 * Each node in a Project Darkstar cluster may be monitored locally or 
 * remotely.  See 
 * <a href="../app/doc-files/config-properties.html#MonitoringProperties">this document</a>
 * for information on enabling remote monitoring and management.
 * <p>
 * For more information, please refer to the <a href="http://java.sun.com/javase/6/docs/technotes/guides/management/toc.html">
 * Java SE Monitoring and Management Guide</a>, especially Chapter 2, 
 * Monitoring and Management Using JMX Technology.
 * <p>
 * Additional information on the use of MBeans, with simple examples, can be
 * found in
 * <a href="http://java.sun.com/javase/6/docs/api/java/lang/management/package-summary.html?is-external=true#examples">here</a>
 * in the {@code java.lang.management} package.
 */
package com.sun.sgs.management;
