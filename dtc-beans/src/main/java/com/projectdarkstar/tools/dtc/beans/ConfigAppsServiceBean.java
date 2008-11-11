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

import com.projectdarkstar.tools.dtc.service.ConfigAppsService;
import com.projectdarkstar.tools.dtc.data.ClientAppConfigDTO;
import com.projectdarkstar.tools.dtc.data.ClientAppConfigTypeDTO;
import com.projectdarkstar.tools.dtc.data.ClientAppDTO;
import com.projectdarkstar.tools.dtc.data.ServerAppConfigDTO;
import com.projectdarkstar.tools.dtc.data.ServerAppDTO;
import com.projectdarkstar.tools.dtc.data.SystemProbeDTO;
import com.projectdarkstar.tools.dtc.data.PkgLibraryDTO;
import com.projectdarkstar.tools.dtc.data.PropertyDTO;
import com.projectdarkstar.tools.dtc.exceptions.DTCServiceException;
import com.projectdarkstar.tools.dtc.exceptions.DTCNotFoundException;
import com.projectdarkstar.tools.dtc.domain.ClientAppConfig;
import com.projectdarkstar.tools.dtc.domain.ClientAppConfigType;
import com.projectdarkstar.tools.dtc.domain.ClientApp;
import com.projectdarkstar.tools.dtc.domain.ServerAppConfig;
import com.projectdarkstar.tools.dtc.domain.ServerApp;
import com.projectdarkstar.tools.dtc.domain.PkgLibrary;
import com.projectdarkstar.tools.dtc.domain.Property;
import com.projectdarkstar.tools.dtc.domain.SystemProbe;
import com.projectdarkstar.tools.dtc.domain.SystemProbeTag;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.NoResultException;

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
        clientApp.validate();
        
        PkgLibrary requiredPkg = getPkgLibrary(clientApp.getRequiredPkg());
        ClientApp app = new ClientApp(clientApp.getName(),
                                      clientApp.getDescription(),
                                      requiredPkg);
        em.persist(app);
        return app.getId();
    }

    public Long addClientAppConfig(ClientAppConfigDTO clientAppConfig) 
            throws DTCServiceException {
        clientAppConfig.validate();
        
        ClientApp clientApp = getClientApp(clientAppConfig.getClientApp());
        ClientAppConfigType type = getClientAppConfigType(clientAppConfig.getPropertyMethod());
        List<Property> properties = buildProperties(clientAppConfig.getProperties());
        
        ClientAppConfig config = new ClientAppConfig(clientAppConfig.getName(),
                                                     clientAppConfig.getPath(),
                                                     type,
                                                     clientApp);
        config.setProperties(properties);
        
        em.persist(config);
        return config.getId();
    }

    public Long addServerApp(ServerAppDTO serverApp) 
            throws DTCServiceException {
        serverApp.validate();
        
        PkgLibrary requiredPkg = getPkgLibrary(serverApp.getRequiredPkg());
        ServerApp app = new ServerApp(serverApp.getName(),
                                      serverApp.getDescription(), 
                                      requiredPkg);
        
        em.persist(app);
        return app.getId();
    }

    public Long addServerAppConfig(ServerAppConfigDTO serverAppConfig)
            throws DTCServiceException {
        serverAppConfig.validate();
        
        ServerApp serverApp = getServerApp(serverAppConfig.getServerApp());
        List<Property> properties = buildProperties(serverAppConfig.getProperties());
        
        ServerAppConfig config = new ServerAppConfig(serverAppConfig.getName(),
                                                     serverAppConfig.getAdditionalCommandLine(),
                                                     serverApp);
        config.setProperties(properties);
        
        em.persist(config);
        return config.getId();
    }

    public Long addSystemProbe(SystemProbeDTO systemProbe, 
                               String tags) 
            throws DTCServiceException {
        systemProbe.validate();
        
        PkgLibrary requiredPkg = getPkgLibrary(systemProbe.getRequiredPkg());
        List<Property> properties = buildProperties(systemProbe.getProperties());
        List<SystemProbeTag> realTags = buildTags(tags);
        
        SystemProbe probe = new SystemProbe(systemProbe.getName(),
                                            systemProbe.getClassName(), 
                                            systemProbe.getClassPath(),
                                            systemProbe.getMetric(),
                                            systemProbe.getUnits(),
                                            requiredPkg);
        probe.setProperties(properties);
        probe.setTags(realTags);
        
        em.persist(probe);
        return probe.getId();
    }

    public void deleteClientApp(Long id)
            throws DTCServiceException {
        genericDelete(ClientApp.class, id);
    }

    public void deleteClientAppConfig(Long id)
            throws DTCServiceException {
        genericDelete(ClientAppConfig.class, id);
    }

    public void deleteServerApp(Long id) 
            throws DTCServiceException {
        genericDelete(ServerApp.class, id);
    }

    public void deleteServerAppConfig(Long id) 
            throws DTCServiceException {
        genericDelete(ServerAppConfig.class, id);
    }

    public void deleteSystemProbe(Long id)
            throws DTCServiceException {
        genericDelete(SystemProbe.class, id);
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
    
    private List<Property> buildProperties(List<PropertyDTO> properties) {
        List<Property> props = new ArrayList<Property>(properties.size());
        for(PropertyDTO p : properties) {
            Property pcopy = new Property(p.getDescription(),
                                          p.getProperty(),
                                          p.getValue());
            em.persist(pcopy);
            props.add(pcopy);
        }
        
        return props;
    }
    
    private List<SystemProbeTag> buildTags(String tags) {
        List<SystemProbeTag> tagList = new ArrayList<SystemProbeTag>();
        for(String tag : tags.split("\\s*,+\\s*")) {
            try {
                SystemProbeTag t = (SystemProbeTag) em.createQuery(
                        "from SystemProbeTag where tag=" + tag).getSingleResult();
                tagList.add(t);
            } catch (NoResultException e) {
                SystemProbeTag nt = new SystemProbeTag(tag);
                em.persist(nt);
                tagList.add(nt);
            }
        }
        
        return tagList;
    }
    
    private PkgLibrary getPkgLibrary(PkgLibraryDTO pkgLibrary) 
            throws DTCServiceException {
        if(pkgLibrary == null) {
            throw new NullPointerException("PkgLibrary cannot be null");
        }
        
        Long id = pkgLibrary.getId();
        PkgLibrary pkg = em.find(PkgLibrary.class, id);
        if(pkg == null) {
            throw new DTCNotFoundException("PkgLibrary with id: " + 
                                           id +
                                           " not found in database");
        }
        
        return pkg;
    }
    
    private ClientApp getClientApp(ClientAppDTO clientApp) 
            throws DTCServiceException {
        if(clientApp == null) {
            throw new NullPointerException("ClientApp cannot be null");
        }
        
        Long id = clientApp.getId();
        ClientApp app = em.find(ClientApp.class, id);
        if(app == null) { 
            throw new DTCNotFoundException("ClientApp with id: " +
                                           id +
                                           " not found in database");
        }
            
        return app;
    }
    
    private ClientAppConfigType getClientAppConfigType(ClientAppConfigTypeDTO type) 
            throws DTCServiceException {
        switch(type) {
            case PROPERTIES:
                return ClientAppConfigType.PROPERTIES;
            case CLI:
                return ClientAppConfigType.CLI;
            case ENV:
                return ClientAppConfigType.ENV;
            default:
                throw new DTCServiceException("Invalid ClientAppConfigType");
        }
    }
    
    private ServerApp getServerApp(ServerAppDTO serverApp)
            throws DTCServiceException {
        if(serverApp == null) {
            throw new NullPointerException("ServerApp cannot be null");
        }
        
        Long id = serverApp.getId();
        ServerApp app = em.find(ServerApp.class, id);
        if(app == null) { 
            throw new DTCNotFoundException("ServerApp with id: " +
                                           id +
                                           " not found in database");
        }
            
        return app;
    }
    
    private <T> void genericDelete(Class<T> clazz, Long id) 
            throws DTCNotFoundException {
        T obj = em.find(clazz, id);
        if(obj == null) {
            throw new DTCNotFoundException(clazz.getName() + 
                                           " with id: " +
                                           id +
                                           " not found in database");
        }
        em.remove(obj);
    }

}
