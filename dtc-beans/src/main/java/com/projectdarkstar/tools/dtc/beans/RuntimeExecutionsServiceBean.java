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

package com.projectdarkstar.tools.dtc.beans;

import com.projectdarkstar.tools.dtc.service.RuntimeExecutionsService;
import com.projectdarkstar.tools.dtc.data.LogFileDTO;
import com.projectdarkstar.tools.dtc.data.TestExecutionResultClientDataDTO;
import com.projectdarkstar.tools.dtc.data.TestExecutionResultProbeDataDTO;
import com.projectdarkstar.tools.dtc.data.TestExecutionResultValueDTO;
import com.projectdarkstar.tools.dtc.exceptions.DTCServiceException;
import javax.ejb.Stateless;
import javax.ejb.Remote;
import javax.persistence.PersistenceContext;
import javax.persistence.EntityManager;

/**
 * This bean implements the RuntimeExecutionsService providing operations
 * allowing updates of results and log files of test executions.
 */
@Stateless
@Remote(RuntimeExecutionsService.class)
public class RuntimeExecutionsServiceBean implements RuntimeExecutionsService
{
    
    @PersistenceContext(unitName = "dtc")
    private EntityManager em;

    public void appendClientLog(Long clientLogId,
                                String chunk)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void appendProbeLog(Long probeLogId,
                               String chunk)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void appendServerLog(Long serverLogId,
                                String chunk)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void addClientDataPoint(Long testExecutionResultId, 
                                   TestExecutionResultClientDataDTO data)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void addProbeDataPoint(Long probeLogId,
                                  TestExecutionResultProbeDataDTO data)
           throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void updateClientLog(Long clientLogId,
                                LogFileDTO logFile)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void updateProbeLog(Long probeLogId,
                               LogFileDTO logFile) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void updateServerLog(Long serverLogId,
                                LogFileDTO logFile)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void updateTestResult(Long testExecutionResultId,
                                 TestExecutionResultValueDTO result) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
