/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
 * 
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
