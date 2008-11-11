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
 * 
 * It is intended to be a remote interface exposing a stateless session
 * EJB3.0 bean.
 */
public interface ConfigAppsService
{
    /**
     * Add a ServerApp object based on the given serverApp to persistent
     * storage.  If the given serverApp's pkgLibrary field is set, 
     * this method will create a new PkgLibrary if it does not already
     * exist in the database.  Otherwise, it will set the pkgLibrary
     * reference to the PkgLibrary in the database with the already 
     * existing id.  Additionally, this method will <em>always</em>
     * create a ServerApp object that contains an empty list of
     * configs.  The configs attribute of the given serverApp will
     * be ignored if it has any objects in it.
     * 
     * @param serverApp the serverApp to add
     * @return id of the newly created ServerApp object
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long addServerApp(ServerAppDTO serverApp)
            throws DTCServiceException;
    
    /**
     * Add a ServerAppConfig object based on the given serverAppConfig to
     * persistent storage.
     * 
     * @param serverAppConfig the serverAppConfig to add
     * @return id of the newly created ServerAppConfig object
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long addServerAppConfig(ServerAppConfigDTO serverAppConfig)
            throws DTCServiceException;
    
    /**
     * Update the ServerApp in storage with the given id.  The given Map
     * of updates maps bean attribute names to values to be used for
     * the updates.
     * 
     * @param id id of the ServerApp to update
     * @param updates map of updates to use to update the ServerApp
     * @return new versionNumber attribute of the updated ServerApp
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long updateServerApp(Long id,
                                Map<String, Object> updates)
            throws DTCServiceException;
    
    /**
     * Update the ServerAppConfig in storage with the given id.  The given
     * Map of updates maps bean attribute names to values to be used for
     * the updates.
     * 
     * @param id id of the ServerAppConfig to update
     * @param updates map of updates to use to update the ServerAppConfig
     * @return new versionNumber attribute of the updated ServerAppConfig
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long updateServerAppConfig(Long id,
                                      Map<String, Object> updates)
            throws DTCServiceException;
    
    /**
     * Remove the ServerApp with the given id from persistent storage.
     * 
     * @param id id of the ServerApp to update
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void deleteServerApp(Long id)
            throws DTCServiceException;
    
    /**
     * Remove the ServerAppConfig with the given id from persistent storage.
     * 
     * @param id id of the ServerAppConfig to update
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void deleteServerAppConfig(Long id)
            throws DTCServiceException;
    
    /**
     * Add a ClientApp to persistent storage based on the given clientApp.
     * If the given clientApp's pkgLibrary field is set, 
     * this method will create a new PkgLibrary if it does not already
     * exist in the database.  Otherwise, it will set the pkgLibrary
     * reference to the PkgLibrary in the database with the already 
     * existing id.  Additionally, this method will <em>always</em>
     * create a ClientApp object that contains an empty list of
     * configs.  The configs attribute of the given clientApp will
     * be ignored if it has any objects in it.
     * 
     * @param clientApp clientApp to add
     * @return id of the newly created ClientApp
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long addClientApp(ClientAppDTO clientApp)
            throws DTCServiceException;
    
    /**
     * Add a ClientAppConfig to persistent storage based on the given
     * clientAppConfig.
     * 
     * @param clientAppConfig clientAppConfig to add
     * @return id of the newly created ClientAppConfig
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long addClientAppConfig(ClientAppConfigDTO clientAppConfig)
            throws DTCServiceException;
    
    /**
     * Update the ClientApp with the given id.  The given Map of updates
     * maps bean attribute names to values to be used for the updates.
     * 
     * @param id id of the ClientApp to update
     * @param updates map of updates to be used to update the ClientApp
     * @return new versionNumber attribute of the updated ClientApp
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long updateClientApp(Long id,
                                Map<String, Object> updates)
            throws DTCServiceException;
    
    /**
     * Update the ClientAppConfig with the given id.  The given Map of updates
     * maps bean attribute names to values to be used for the updates.
     * 
     * @param id id of the ClientAppConfig to update
     * @param updates map of updates to be used to update the ClientAppConfig
     * @return new versionNumber attribute of the updated ClientAppConfig
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long updateClientAppConfig(Long id,
                                      Map<String, Object> updates)
            throws DTCServiceException;
    
    /**
     * Remove the ClientApp with the given id from persistent storage.
     * 
     * @param id id of the ClientApp to remove
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void deleteClientApp(Long id)
            throws DTCServiceException;
    
    /**
     * Remove the ClientAppConfig with the given id from persistent storage.
     * 
     * @param id id of the ClientAppConfig to remove
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void deleteClientAppConfig(Long id)
            throws DTCServiceException;
    
    /**
     * Add a SystemProbe to persistent storage based on the given systemProbe.
     * The comma and/or space separated string of tags is to be used to assign
     * the new SystemProbe to a set of SystemProbeTag entities.  For each tag,
     * a new SystemProbeTag will be created or an existing tag entity with the 
     * same name will be used.
     * 
     * @param systemProbe systemProbe to add
     * @param tags comma and/or space separated list of tags
     * @return id of the newly created SystemProbe
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long addSystemProbe(SystemProbeDTO systemProbe,
                               String tags)
            throws DTCServiceException;
    
    /**
     * Update the SystemProbe with the given id in persistent storage.  The 
     * given Map of updates maps bean attribute names to values to be used
     * for the updates.
     * 
     * @param id id of the SystemProbe to update
     * @param updates map of updates to be used to update the SystemProbe
     * @return new versionNumber attribute of the updated SystemProbe
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long updateSystemProbe(Long id,
                                  Map<String, Object> updates)
            throws DTCServiceException;
    
    /**
     * Remove the SystemProbe with the given id from persistent storage.
     * 
     * @param id id of the SystemProbe to remove
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void deleteSystemProbe(Long id)
            throws DTCServiceException;
}
