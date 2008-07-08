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
import com.projectdarkstar.tools.dtc.data.ServerAppDTO;
import com.projectdarkstar.tools.dtc.data.ServerAppConfigDTO;
import com.projectdarkstar.tools.dtc.data.ClientAppDTO;
import com.projectdarkstar.tools.dtc.data.ClientAppConfigDTO;
import com.projectdarkstar.tools.dtc.data.SystemProbeDTO;
import java.util.Map;

/**
 * This interface exposes operations that allow creating, updating,
 * and deleting configurations for the client, server, and system probe
 * applications.
 */
public interface ConfigAppsService
{
    public Long addServerApp(ServerAppDTO serverApp)
            throws DTCServiceException;
    public Long addServerAppConfig(ServerAppConfigDTO serverAppConfig)
            throws DTCServiceException;
    
    public Long updateServerApp(Long id,
                                Map<String, Object> updates)
            throws DTCServiceException;
    public Long updateServerAppConfig(Long id,
                                      Map<String, Object> updates)
            throws DTCServiceException;
    
    public Long deleteServerApp(Long id)
            throws DTCServiceException;
    public Long deleteServerAppConfig(Long id)
            throws DTCServiceException;
    
    
    public Long addClientApp(ClientAppDTO clientApp)
            throws DTCServiceException;
    public Long addClientAppConfig(ClientAppConfigDTO clientAppConfig)
            throws DTCServiceException;
    
    public Long updateClientApp(Long id,
                                Map<String, Object> updates)
            throws DTCServiceException;
    public Long updateClientAppConfig(Long id,
                                      Map<String, Object> updates)
            throws DTCServiceException;
    
    public void deleteClientApp(Long id)
            throws DTCServiceException;
    public void deleteClientAppConfig(Long id)
            throws DTCServiceException;
    
    
    public Long addSystemProbe(SystemProbeDTO systemProbe,
                               String tags)
            throws DTCServiceException;
    
    public Long updateSystemProbe(Long id,
                                  Map<String, Object> updates)
            throws DTCServiceException;
    
    public void deleteSystemProbe(Long id)
            throws DTCServiceException;
}
