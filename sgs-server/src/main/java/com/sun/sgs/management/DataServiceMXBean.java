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

package com.sun.sgs.management;

import com.sun.sgs.service.DataService;
/**
 * The management interface for the data service.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #DATA_SERVICE_MXBEAN_NAME}.
 * 
 */
public interface DataServiceMXBean {
    /** The name for uniquely identifying this MBean. */
    String DATA_SERVICE_MXBEAN_NAME = "com.sun.sgs.service:type=DataService";
    
    /**
     * Returns the number of times 
     * {@link DataService#createReference(Object) createReference} 
     * has been called.
     * @return the number of times {@code createReference} has been called
     */
    long getCreateReferenceCount();
}
