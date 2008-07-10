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

import com.projectdarkstar.tools.dtc.api.ConfigAppsService;
import com.projectdarkstar.tools.dtc.data.ClientAppConfigDTO;
import com.projectdarkstar.tools.dtc.data.ClientAppDTO;
import com.projectdarkstar.tools.dtc.data.ServerAppConfigDTO;
import com.projectdarkstar.tools.dtc.data.ServerAppDTO;
import com.projectdarkstar.tools.dtc.data.SystemProbeDTO;
import com.projectdarkstar.tools.dtc.exceptions.DTCServiceException;
import java.util.Map;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * This bean implements the ConfigAppsService interface providing operations
 * to add, update, and remove application configurations in the database.
 */
@Stateless
@Remote(ConfigAppsService.class)
public class ConfigAppsServiceBean implements ConfigAppsService
{
    @PersistenceContext(unitName="dtc")
    private EntityManager em;

    public Long addClientApp(ClientAppDTO clientApp) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long addClientAppConfig(ClientAppConfigDTO clientAppConfig) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long addServerApp(ServerAppDTO serverApp) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long addServerAppConfig(ServerAppConfigDTO serverAppConfig)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long addSystemProbe(SystemProbeDTO systemProbe, 
                               String tags) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void deleteClientApp(Long id)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void deleteClientAppConfig(Long id)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void deleteServerApp(Long id) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void deleteServerAppConfig(Long id) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void deleteSystemProbe(Long id)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long updateClientApp(Long id, 
                                Map<String, Object> updates)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long updateClientAppConfig(Long id,
                                      Map<String, Object> updates) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long updateServerApp(Long id,
                                Map<String, Object> updates)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long updateServerAppConfig(Long id, 
                                      Map<String, Object> updates) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long updateSystemProbe(Long id, 
                                  Map<String, Object> updates) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
