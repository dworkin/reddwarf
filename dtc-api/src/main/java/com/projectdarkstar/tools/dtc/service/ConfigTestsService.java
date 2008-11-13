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

package com.projectdarkstar.tools.dtc.service;

import com.projectdarkstar.tools.dtc.exceptions.DTCServiceException;
import com.projectdarkstar.tools.dtc.data.TestSuiteDTO;
import com.projectdarkstar.tools.dtc.data.TestSpecDTO;
import java.util.Map;

/**
 * This interface exposes the operations that allow creating, updating,
 * and deleting the test suite specifications in the database.
 * 
 * It is intended to be a remote interface exposing a stateless session
 * EJB3.0 bean.
 */
public interface ConfigTestsService 
{
    /**
     * Creates a TestSuite object based on the given testSuite and
     * persists it to the database backed persistent storage.
     * 
     * @param testSuite
     * @return id of the newly persisted TestSuite
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long addTestSuite(TestSuiteDTO testSuite)
            throws DTCServiceException;
    
    /**
     * Updates a TestSuite object in persistent storage with the given id.
     * The given Map of updates maps bean attribute names to values that
     * are to be updated.
     * 
     * @param id id of the TestSuite to update
     * @param updates map of updates to update the TestSuite
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void updateTestSuite(Long id,
                                Map<String, Object> updates)
            throws DTCServiceException;
    
    /**
     * Remove the TestSuite with the given id from persistent storage.
     * 
     * @param id id of the TestSuite to remove
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void deleteTestSuite(Long id)
            throws DTCServiceException;
    
    /**
     * Creates a TestSpec object based on the given testSpec and
     * persists it to the database backed persistent storage.
     * 
     * @param testSpec
     * @return id of the newly persisted TestSpec
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long addTestSpec(TestSpecDTO testSpec)
            throws DTCServiceException;
    
    /**
     * Updates a TestSpec object in persistent storage with the given id.
     * The given Map of updates maps bean attribute names to values that are
     * to be updated.
     * 
     * @param id id of the TestSpec to update
     * @param updates map of updates to update the TestSpec
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void updateTestSpec(Long id,
                               Map<String, Object> updates)
            throws DTCServiceException;
    
    /**
     * Remove the TestSpec object with the given id from persistent storage.
     * 
     * @param id id of the TestSpec object to remove
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void deleteTestSpec(Long id)
            throws DTCServiceException;
}
