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

import com.projectdarkstar.tools.dtc.data.ServerAppDTO;
import com.projectdarkstar.tools.dtc.data.ServerAppConfigDTO;
import com.projectdarkstar.tools.dtc.data.PkgLibraryDTO;
import com.projectdarkstar.tools.dtc.data.PkgLibraryTagDTO;
import com.projectdarkstar.tools.dtc.domain.ServerApp;
import com.projectdarkstar.tools.dtc.domain.ServerAppConfig;
import com.projectdarkstar.tools.dtc.domain.PkgLibrary;
import com.projectdarkstar.tools.dtc.domain.PkgLibraryTag;
import javax.ejb.Stateless;
import javax.ejb.Local;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 *
 */
@Stateless
@Local(DataConverter.class)
public class DataConverterImpl implements DataConverter {
    
    @PersistenceContext(unitName="dtc")
    private EntityManager em;

    public ServerAppDTO basicServerAppDTO(ServerApp serverApp) {
        ServerAppDTO dto = new ServerAppDTO(serverApp.getId(),
                                            serverApp.getVersionNumber(),
                                            serverApp.getName(),
                                            serverApp.getDescription(),
                                            basicPkgLibraryDTO(serverApp.getRequiredPkg()));
        return dto;
    }
    
    public ServerAppConfigDTO basicServerAppConfigDTO(ServerAppConfig serverAppConfig) {
        ServerAppConfigDTO dto = new ServerAppConfigDTO(serverAppConfig.getId(),
                                                        serverAppConfig.getVersionNumber(),
                                                        serverAppConfig.getName(),
                                                        serverAppConfig.getAdditionalCommandLine());
        return dto;
    }
    
    public PkgLibraryDTO basicPkgLibraryDTO(PkgLibrary pkgLibrary) {
        PkgLibraryDTO dto = new PkgLibraryDTO(pkgLibrary.getId(),
                                              pkgLibrary.getVersionNumber(),
                                              pkgLibrary.getName(),
                                              null);
        return dto;
    }
    
    public PkgLibraryTagDTO basicPkgLibraryTagDTO(PkgLibraryTag pkgLibraryTag) {
        PkgLibraryTagDTO dto = new PkgLibraryTagDTO(pkgLibraryTag.getId(),
                                                    pkgLibraryTag.getTag());
        return dto;
    }
            
}
