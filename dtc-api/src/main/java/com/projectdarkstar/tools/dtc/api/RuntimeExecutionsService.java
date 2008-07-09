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
import com.projectdarkstar.tools.dtc.data.LogFileDTO;
import com.projectdarkstar.tools.dtc.data.TestExecutionResultClientDataDTO;
import com.projectdarkstar.tools.dtc.data.TestExecutionResultProbeDataDTO;
import com.projectdarkstar.tools.dtc.data.TestExecutionResultValueDTO;

/**
 * This interface exposes operations to be used by the slave execution
 * daemons in order to update results and log files for TestExecutions.
 * 
 * It is intended to be a remote interface exposing a stateless session
 * EJB3.0 bean.
 */
public interface RuntimeExecutionsService
{

    /**
     * Update the logFile attribute of the TestExecutionResultServerLog
     * object with the given id.
     * 
     * @param serverLogId id of the TestExecutionResultServerLog to update
     * @param logFile new log file
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void updateServerLog(Long serverLogId,
                                LogFileDTO logFile)
            throws DTCServiceException;
    
    /**
     * Update the logFile attribute of the TestExecutionResultClientLog
     * object with the given id.
     * 
     * @param clientLogId id of the TestExecutionResultClientLog to update
     * @param logFile new log file
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void updateClientLog(Long clientLogId,
                                LogFileDTO logFile)
            throws DTCServiceException;
    
    /**
     * Update the logFile attribute of the TestExecutionResultProbeLog
     * object with the given id.
     * 
     * @param probeLogId id of the TestExecutionResultProbeLog to update
     * @param logFile new log file
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void updateProbeLog(Long probeLogId,
                               LogFileDTO logFile)
            throws DTCServiceException;
    
    
    /**
     * Add a new data point representing the number of realtime clients
     * in the system at a single point in time.
     * 
     * @param testExecutionResultId id of TestExecutionResult which collects the data
     * @param data data point to add to the list
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void addClientDataPoint(Long testExecutionResultId,
                                   TestExecutionResultClientDataDTO data)
            throws DTCServiceException;
    
    
    /**
     * Add a new data point collected by a probe.
     * 
     * @param probeLogId id of the probe log to update
     * @param data data point to add to the probe log's list
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void addProbeDataPoint(Long probeLogId,
                                  TestExecutionResultProbeDataDTO data)
            throws DTCServiceException;
    
    
    /**
     * Update the result of a test execution
     * 
     * @param testExecutionResultId id of the TestExecutionResult to update
     * @param result value of the result
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void updateTestResult(Long testExecutionResultId,
                                 TestExecutionResultValueDTO result)
            throws DTCServiceException;
    
    
}
