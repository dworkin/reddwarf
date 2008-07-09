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
import com.projectdarkstar.tools.dtc.data.HardwareResourceDTO;
import com.projectdarkstar.tools.dtc.data.HardwareResourceFamilyDTO;
import com.projectdarkstar.tools.dtc.data.PkgLibraryDTO;
import java.util.Map;

/**
 * This interface exposes operations that allow creating, updating,
 * and deleting resource objects like the hardware resources and
 * package library objects.
 * 
 * It is intended to be a remote interface exposing a stateless session
 * EJB3.0 bean.
 */
public interface ConfigResourcesService 
{
    /**
     * Add a HardwareResource object to persistent storage based on the
     * given hardwareResource.
     * 
     * @param hardwareResource hardwareResource to create
     * @return id of the newly persisted HardwareResource object
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long addHardwareResource(HardwareResourceDTO hardwareResource)
            throws DTCServiceException;
    /**
     * Add a HardwareResourceFamily object to persistent storage based on the
     * given hardwareResourceFamily
     * 
     * @param hardwareResourceFamily hardwareResourceFamily to create
     * @return id of the newly persisted HardwareResourceFamily object
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long addHardwareResourceFamily(HardwareResourceFamilyDTO hardwareResourceFamily)
            throws DTCServiceException;
    
    /**
     * Update the HardwareResource in storage with the given id.  The given
     * Map of updates maps bean attribute names to values that are to be
     * used for the updates.
     * 
     * @param id id of the HardwareResource to update
     * @param updates map of updates to use to update the HardwareResource
     * @return new versionNumber attribute of the updated HardwareResource
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long updateHardwareResource(Long id,
                                       Map<String, Object> updates)
            throws DTCServiceException;
    
    /**
     * Update the HardwareResourceFamily in storage with the given id.  The given
     * Map of updates maps bean attribute names to values that are to be
     * used for the updates.
     * 
     * @param id id of the HardwareResourceFamily to update
     * @param updates map of updates to use to update the HardwareResourceFamily
     * @return new versionNumber attribute of the updated HardwareResourceFamily
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long updateHardwareResourceFamily(Long id,
                                             Map<String, Object> updates)
            throws DTCServiceException;
    
    /**
     * Remove the HardwareResource with the given id from persistent storage.
     * 
     * @param id id of the HardwareResource to remove
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void deleteHardwareResource(Long id)
            throws DTCServiceException;
    
    /**
     * Remove the HardwareResourceFamily with the given id from persistent 
     * storage.
     * 
     * @param id id of the HardwareResourceFamily to remove
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void deleteHardwareResourceFamily(Long id)
            throws DTCServiceException;
    
    /**
     * Add a PkgLibrary object based on the given pkgLibrary to persistent
     * storage.  The given string of tags is a space and/or comma separated
     * list of tags that are to be assigned to the PkgLibrary as PkgLibraryTag
     * objects.  If there is no PkgLibraryTag object for a given tag, one will
     * automatically be created, otherwise, the already existing entity will
     * be used.
     * 
     * @param pkgLibrary pkgLibrary to add
     * @param tags comma and/or space separated list of tags to assign to the library
     * @return id of the newly created PkgLibrary
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long addPkgLibrary(PkgLibraryDTO pkgLibrary,
                              String tags)
            throws DTCServiceException;
    
    /**
     * Update the PkgLibrary object with the given id.  The given Map of updates
     * maps bean attribute names to values that are to be used for the update.
     * 
     * @param id id of the PkgLibrary to update
     * @param updates map of updates to update the PkgLibrary
     * @return new versionNumber attribute of the updated PkgLibrary
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public Long updatePkgLibrary(Long id,
                                 Map<String, Object> updates)
            throws DTCServiceException;
    
    /**
     * Remove the PkgLibrary with the given id from persistent storage.
     * 
     * @param id id of the PkgLibrary to remove
     * @throws com.projectdarkstar.tools.dtc.service.DTCServiceException
     */
    public void deletePkgLibrary(Long id)
            throws DTCServiceException;
}
