/*
 * Copyright 2008 Sun Microsystems, Inc.
 *
 * This file is part of the Darkstar Test Cluster
 *
 * Darkstar Test Cluster is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Darkstar Test Cluster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.projectdarkstar.tools.dtc.api;

import com.projectdarkstar.tools.dtc.service.DTCServiceException;
import java.util.Map;

/**
 * This interface exposes operations that allow creating and configuring
 * TestExecutions before executing them.
 * 
 * It is intended to be a remote interface exposing a stateless session
 * EJB3.0 bean.
 */
public interface ConfigExecutionsService 
{

    /**
     * Generates a new TestExecution based on the TestSuite with the given
     * id.
     * 
     * @param testSuiteId id of the TestSuite to use to generate the TestExecution
     * @param name name to assign to the generated TestExecution
     * @param tags comma/space separated list of tags to assign to the TestExecution
     * @return id of the newly created TestExecution
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long generateTestExecution(Long testSuiteId,
                                      String name,
                                      String tags)
            throws DTCServiceException;
 
    /**
     * Creates a new TestExecution which is an exact replica of the
     * TestExecution with the given id.
     * 
     * @param testExecutionId
     * @param name name to assign to the generated TestExecution
     * @param tags comma/space separated list of tags to assign to the TestExecution
     * @return id of the newly created TestExecution
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long cloneTestExecution(Long testExecutionId,
                                   String name,
                                   String tags)
            throws DTCServiceException;
    
    
    /**
     * <p>
     * Update the TestExecution in storage with the given id.  The given
     * Map of updates maps bean attribute names to values that are to be
     * used for the updates.
     * </p>
     * 
     * <p>
     * Note that the only valid attributes that can be updated using this
     * method are:
     * <ul>
     * <li>name</li>
     * <li>tags</li>
     * <li>originalTestSuiteDarkstarPkg</li>
     * </ul>
     * Attempts to update other attributes will throw an exception.
     * </p>
     * 
     * @param testExecutionId id of the TestExecution to update
     * @param updates map of updates to use to update the TestExecution
     */
    public void updateTestExecution(Long testExecutionId,
                                    Map<String, Object> updates)
            throws DTCServiceException;
    
    
    /**
     * TODO: Future extension.
     * Include methods to customize each of the TestExecutionResult
     * objects in a TestExecution.
     */
    
}
