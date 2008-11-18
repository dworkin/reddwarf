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
import com.projectdarkstar.tools.dtc.domain.ServerApp;
import com.projectdarkstar.tools.dtc.service.RequestService;
import com.projectdarkstar.tools.dtc.util.Caster;
import java.util.List;
import java.util.ArrayList;
import javax.ejb.Stateless;
import javax.ejb.Local;
import javax.ejb.EJB;
import javax.persistence.PersistenceContext;
import javax.persistence.EntityManager;
import javax.persistence.Query;

/**
 *
 */
@Stateless
@Local(RequestService.class)
public class RequestServiceBean implements RequestService {
    
    @PersistenceContext(unitName="dtc")
    private EntityManager em;
    
    @EJB
    private DataConverter dc;

    public List<ServerAppDTO> getServerAppsPage(int pageIndex, int pageSize) {
        Query query = em.createQuery("SELECT s " +
                                     "FROM ServerApp s " + 
                                     "ORDER BY s.name");
        query.setFirstResult(pageIndex);
        query.setMaxResults(pageSize);
        
        List<ServerApp> apps = Caster.cast(query.getResultList());
        List<ServerAppDTO> result = new ArrayList<ServerAppDTO>();
        
        for(ServerApp app : apps) {
            result.add(dc.basicServerAppDTO(app));
        }
        return result;
    }

}
