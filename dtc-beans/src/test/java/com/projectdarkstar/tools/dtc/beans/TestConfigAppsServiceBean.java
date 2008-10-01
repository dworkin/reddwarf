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
import com.projectdarkstar.tools.dtc.data.PkgLibraryDTO;
import org.junit.Test;
import org.junit.Before;
//import org.easymock.EasyMock;
import java.lang.reflect.Field;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;


/**
 * Test the ConfigAppsServiceBean class
 */
public class TestConfigAppsServiceBean 
{
    private ConfigAppsServiceBean bean;
    private EntityManager em;
    
    /**
     * Initialize the bean.
     * Inject a mock EntityManager into the bean.
     */
    @Before
    public void initializeBean() throws Exception {
        /*bean = new ConfigAppsServiceBean();
        em = EasyMock.createMock(EntityManager.class);
        
        Field[] fields = ConfigAppsServiceBean.class.getDeclaredFields();
        for(Field f : fields) {
            if(f.isAnnotationPresent(PersistenceContext.class)) {
                f.setAccessible(true);
                f.set(bean, em);
            }
        }*/
    }
    
    /**
     * Configure the EntityManager to return a new dummy PkgLibrary
     */
    private void configureNewPkg() {
        
    }
    
    @Test
    public void testAddServerAppNewPkg() {
        //create the server app to be added
        /*PkgLibraryDTO pkgLibrary = new PkgLibraryDTO(null,
                                                     null,
                                                     "mypackage",
                                                     new byte[0]);
        ServerAppDTO newServerApp = new ServerAppDTO(null,
                                                     null,
                                                     "Server",
                                                     "Description",
                                                     "serverClass",
                                                     "path",
                                                     pkgLibrary);*/
        
        //create the expected resulting ServerApp
        
        
        //setup the expected EntityManager calls
    }
}
