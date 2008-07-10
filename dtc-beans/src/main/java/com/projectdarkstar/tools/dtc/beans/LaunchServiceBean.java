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

import com.projectdarkstar.tools.dtc.api.LaunchService;
import com.projectdarkstar.tools.dtc.data.PkgLibraryDTO;
import com.projectdarkstar.tools.dtc.service.DTCServiceException;
import javax.ejb.Stateless;
import javax.ejb.Remote;
import javax.persistence.PersistenceContext;
import javax.persistence.EntityManager;

/**
 * This bean implements the LaunchService providing operations to load
 * tests into the execution queue of the database.
 */
@Stateless
@Remote(LaunchService.class)
public class LaunchServiceBean implements LaunchService
{
    @PersistenceContext(unitName="dtc")
    private EntityManager em;


    /** @inheritDoc **/
    public void rerunTestExecution(Long testExecutionId,
                                   String name,
                                   String tags) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** @inheritDoc **/
    public void rerunTestExecutionAgainstNewDarkstar(Long testExecutionId,
                                                     String name,
                                                     String tags,
                                                     PkgLibraryDTO darkstarPkg) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** @inheritDoc **/
    public void rerunTestExecutionAgainstNewServerApp(Long testExecutionId,
                                                      String name,
                                                      String tags,
                                                      PkgLibraryDTO darkstarPkg) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** @inheritDoc **/
    public void runTestExecution(Long testExecutionId)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** @inheritDoc **/
    public void runTestSuite(Long testSuiteId,
                             String name,
                             String tags)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** @inheritDoc **/
    public void runTestSuiteAgainstNewDarkstar(Long testSuiteId,
                                               String name,
                                               String tags,
                                               PkgLibraryDTO darkstarPkg)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** @inheritDoc **/
    public void runTestSuiteAgainstNewServerApp(Long testSuiteId,
                                                String name,
                                                String tags,
                                                PkgLibraryDTO serverPkg)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
