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

import com.sun.sgs.service.DataService;
/**
 * The management interface for the data service.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface DataServiceMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs.service:type=DataService";
    
    /**
     * Returns the number of times 
     * {@link DataService#createReference createReference} 
     * has been called.
     * @return the number of times {@code createReference} has been called
     */
    long getCreateReferenceCalls();

    /**
     * Returns the number of times 
     * {@link DataService#getBinding getBinding} 
     * has been called.
     * @return the number of times {@code getBinding} has been called
     */
    long getGetBindingCalls();
        
    /**
     * Returns the number of times 
     * {@link DataService#getBindingForUpdate getBindingForUpdate} 
     * has been called.
     * @return the number of times {@code getBindingForUpdate} has been called
     */
    long getGetBindingForUpdateCalls();
        
    /**
     * Returns the number of times 
     * {@link DataService#getObjectId getObjectId} 
     * has been called.
     * @return the number of times {@code getObjectId} has been called
     */
    long getGetObjectIdCalls();
        
    /**
     * Returns the number of times 
     * {@link DataService#markForUpdate markForUpdate} 
     * has been called.
     * @return the number of times {@code markForUpdate} has been called
     */
    long getMarkForUpdateCalls();
            
    /**
     * Returns the number of times 
     * {@link DataService#nextBoundName nextBoundName} 
     * has been called.
     * @return the number of times {@code nextBoundName} has been called
     */
    long getNextBoundNameCalls();
    
    /**
     * Returns the number of times 
     * {@link DataService#removeBinding removeBinding} 
     * has been called.
     * @return the number of times {@code removeBinding} has been called
     */
    long getRemoveBindingCalls();
        
    /**
     * Returns the number of times 
     * {@link DataService#removeObject removeObject} 
     * has been called.
     * @return the number of times {@code removeObject} has been called
     */
    long getRemoveObjectCalls();
       
    /**
     * Returns the number of times 
     * {@link DataService#setBinding setBinding} 
     * has been called.
     * @return the number of times {@code setBinding} has been called
     */
    long getSetBindingCalls();
  
    /**
     * Returns the number of times {@link DataService#getLocalNodeId
     * getLocalNodeId} has been called.
     * 
     * @return the number of times {@code getLocalNodeId} has been called
     */
    long getGetLocalNodeIdCalls();

    /**
     * Returns the number of times 
     * {@link DataService#createReferenceForId createReferenceForId} 
     * has been called.
     * @return the number of times {@code createReferenceForId} has been called
     */
    long getCreateReferenceForIdCalls();
        
    /**
     * Returns the number of times 
     * {@link DataService#getServiceBinding getServiceBinding} 
     * has been called.
     * @return the number of times {@code getServiceBinding} has been called
     */
    long getGetServiceBindingCalls();
            
    /**
     * Returns the number of times {@link
     * DataService#getServiceBindingForUpdate getServiceBindingForUpdate} has
     * been called.
     * @return the number of times {@code getServiceBindingForUpdate} has been
     * called
     */
    long getGetServiceBindingForUpdateCalls();
            
    /**
     * Returns the number of times 
     * {@link DataService#nextObjectId nextObjectId} 
     * has been called.
     * @return the number of times {@code nextObjectId} has been called
     */
    long getNextObjectIdCalls();
                
    /**
     * Returns the number of times 
     * {@link DataService#nextServiceBoundName nextServiceBoundName} 
     * has been called.
     * @return the number of times {@code nextServiceBoundName} has been called
     */
    long getNextServiceBoundNameCalls();
                    
    /**
     * Returns the number of times 
     * {@link DataService#removeServiceBinding removeServiceBinding} 
     * has been called.
     * @return the number of times {@code removeServiceBinding} has been called
     */
    long getRemoveServiceBindingCalls();
                        
    /**
     * Returns the number of times 
     * {@link DataService#setServiceBinding setServiceBinding} 
     * has been called.
     * @return the number of times {@code setServiceBinding} has been called
     */
    long getSetServiceBindingCalls();
}
