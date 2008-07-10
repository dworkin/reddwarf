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

import com.projectdarkstar.tools.dtc.api.ConfigExecutionsService;
import com.projectdarkstar.tools.dtc.exceptions.DTCServiceException;
import java.util.Map;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * This bean implements the ConfigExecutionsService providing operations to
 * add, update, and configure TestExecution entities before the
 * tests are run.
 */
@Stateless
@Remote(ConfigExecutionsService.class)
public class ConfigExecutionsServiceBean implements ConfigExecutionsService
{
    @PersistenceContext(unitName="dtc")
    private EntityManager em;

    public Long cloneTestExecution(Long testExecutionId, String name, String tags) throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long generateTestExecution(Long testSuiteId, String name, String tags) throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void updateTestExecution(Long testExecutionId, Map<String, Object> updates) throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
