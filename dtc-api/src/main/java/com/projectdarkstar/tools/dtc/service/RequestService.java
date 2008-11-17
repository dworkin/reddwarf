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

import com.projectdarkstar.tools.dtc.data.ServerAppDTO;
import java.util.List;

/**
 * This interface is the central API used by clients to retrieve data
 * from the database.
 * 
 * It is intended to be a remote interface exposing a stateless session
 * EJB3.0 bean.
 */
public interface RequestService {
    
    public List<ServerAppDTO> getServerAppsPage(int pageIndex, int pageSize);

}
