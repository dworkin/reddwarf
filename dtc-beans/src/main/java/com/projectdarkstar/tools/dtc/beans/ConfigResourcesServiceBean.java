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

import com.projectdarkstar.tools.dtc.service.ConfigResourcesService;
import com.projectdarkstar.tools.dtc.data.HardwareResourceDTO;
import com.projectdarkstar.tools.dtc.data.HardwareResourceFamilyDTO;
import com.projectdarkstar.tools.dtc.data.PkgLibraryDTO;
import com.projectdarkstar.tools.dtc.exceptions.DTCServiceException;
import java.util.Map;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * This bean implements the ConfigResourcesService providing operations to
 * add, remove, and update resource entities in the database.
 */
@Stateless
@Remote(ConfigResourcesService.class)
public class ConfigResourcesServiceBean implements ConfigResourcesService
{
    @PersistenceContext(unitName="dtc")
    private EntityManager em;

    public Long addHardwareResource(HardwareResourceDTO hardwareResource) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long addHardwareResourceFamily(HardwareResourceFamilyDTO hardwareResourceFamily)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long addPkgLibrary(PkgLibraryDTO pkgLibrary, 
                              String tags) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void deleteHardwareResource(Long id)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void deleteHardwareResourceFamily(Long id)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void deletePkgLibrary(Long id)
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long updateHardwareResource(Long id,
                                       Map<String, Object> updates) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long updateHardwareResourceFamily(Long id, 
                                             Map<String, Object> updates) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Long updatePkgLibrary(Long id,
                                 Map<String, Object> updates) 
            throws DTCServiceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
