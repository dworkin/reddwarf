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

import com.projectdarkstar.tools.dtc.service.ConfigTestsService;
import com.projectdarkstar.tools.dtc.data.TestSpecDTO;
import com.projectdarkstar.tools.dtc.data.TestSuiteDTO;
import com.projectdarkstar.tools.dtc.exceptions.DTCServiceException;
import java.util.Map;
import javax.ejb.Stateless;
import javax.ejb.Remote;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * This bean implements the ConfigTestsService interface providing operations
 * to add, remove, and update test suite specifications in the database.
 */
@Stateless
@Remote(ConfigTestsService.class)
public class ConfigTestsServiceBean implements ConfigTestsService
{
    @PersistenceContext(unitName="dtc")
    private EntityManager em;

    public Long addTestSpec(TestSpecDTO testSpec)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long addTestSuite(TestSuiteDTO testSuite) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void deleteTestSpec(Long id) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void deleteTestSuite(Long id) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long updateTestSpec(Long id, 
                               Map<String, Object> updates)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long updateTestSuite(Long id, 
                                Map<String, Object> updates)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
