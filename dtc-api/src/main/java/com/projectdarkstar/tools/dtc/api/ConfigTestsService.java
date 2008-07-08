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
import com.projectdarkstar.tools.dtc.data.TestSuiteDTO;
import com.projectdarkstar.tools.dtc.data.TestSpecDTO;
import java.util.Map;

/**
 * This interface exposes the operations that allow creating, updating,
 * and deleting the test suite specifications in the database.
 */
public interface ConfigTestsService 
{
    public Long addTestSuite(TestSuiteDTO testSuite)
            throws DTCServiceException;
    
    public Long updateTestSuite(Long id,
                                Map<String, Object> updates)
            throws DTCServiceException;
    
    public void deleteTestSuite(Long id)
            throws DTCServiceException;
    
    public Long addTestSpec(TestSpecDTO testSpec)
            throws DTCServiceException;
    
    public Long updateTestSpec(Long id,
                               Map<String, Object> updates)
            throws DTCServiceException;
    
    public void deleteTestSpec(Long id)
            throws DTCServiceException;
}
