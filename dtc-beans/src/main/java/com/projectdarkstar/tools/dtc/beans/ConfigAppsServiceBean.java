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
import com.projectdarkstar.tools.dtc.data.AbstractDTO;
import com.projectdarkstar.tools.dtc.data.ClientAppConfigDTO;
import com.projectdarkstar.tools.dtc.data.ClientAppConfigTypeDTO;
import com.projectdarkstar.tools.dtc.data.ClientAppDTO;
import com.projectdarkstar.tools.dtc.data.ServerAppConfigDTO;
import com.projectdarkstar.tools.dtc.data.ServerAppDTO;
import com.projectdarkstar.tools.dtc.data.SystemProbeDTO;
import com.projectdarkstar.tools.dtc.data.SystemProbeTagDTO;
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
import com.projectdarkstar.tools.dtc.util.Caster;
import org.apache.commons.beanutils.PropertyUtils;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
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
        
        PkgLibrary requiredPkg = genericGet(PkgLibraryDTO.class,
                                            PkgLibrary.class,
                                            clientApp.getRequiredPkg());
        ClientApp app = new ClientApp(clientApp.getName(),
                                      clientApp.getDescription(),
                                      requiredPkg);
        em.persist(app);
        return app.getId();
    }

    public Long addClientAppConfig(ClientAppConfigDTO clientAppConfig) 
            throws DTCServiceException {
        clientAppConfig.validate();
        
        ClientApp clientApp = genericGet(ClientAppDTO.class,
                                         ClientApp.class,
                                         clientAppConfig.getClientApp());
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
        
        PkgLibrary requiredPkg = genericGet(PkgLibraryDTO.class,
                                            PkgLibrary.class,
                                            serverApp.getRequiredPkg());
        ServerApp app = new ServerApp(serverApp.getName(),
                                      serverApp.getDescription(), 
                                      requiredPkg);
        
        em.persist(app);
        return app.getId();
    }

    public Long addServerAppConfig(ServerAppConfigDTO serverAppConfig)
            throws DTCServiceException {
        serverAppConfig.validate();
        
        ServerApp serverApp = genericGet(ServerAppDTO.class,
                                         ServerApp.class,
                                         serverAppConfig.getServerApp());
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
        
        PkgLibrary requiredPkg = genericGet(PkgLibraryDTO.class,
                                            PkgLibrary.class,
                                            systemProbe.getRequiredPkg());
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

    public void updateClientApp(Long id, 
                                Map<String, Object> updates)
            throws DTCServiceException {
        ClientApp app = em.find(ClientApp.class, id);
        if(app == null) {
            throw new DTCNotFoundException("ClientApp with id: " + id +
                                           " not found in the database.");
        }
        
        //update the app with the updates map
        for(Iterator<String> keyI = updates.keySet().iterator(); keyI.hasNext(); ) {
            String key = keyI.next();
            
            //update the configs if necessary
            if(key.equals("configs")) {
                List<ClientAppConfigDTO> configs = Caster.cast(updates.get(key));
                app.getConfigs().clear();
                for (Iterator<ClientAppConfigDTO> ic = configs.iterator(); ic.hasNext();) {
                    ClientAppConfigDTO c = ic.next();
                    ClientAppConfig realc = genericGet(ClientAppConfigDTO.class,
                                                       ClientAppConfig.class,
                                                       c);
                    app.getConfigs().add(realc);
                }
            }
            else if(key.equals("requiredPkg")) {
                PkgLibraryDTO pkg = Caster.cast(updates.get(key));
                PkgLibrary realPkg = genericGet(PkgLibraryDTO.class,
                                                PkgLibrary.class,
                                                pkg);
                app.setRequiredPkg(realPkg);
            }
            else {
                try {
                    PropertyUtils.setProperty(app, key, updates.get(key));
                } catch(Exception e) {
                    throw new DTCServiceException("Unable to set property: " +
                                                  key + " on " + app, e);
                }
            }
        }
    }

    public void updateClientAppConfig(Long id,
                                      Map<String, Object> updates) 
            throws DTCServiceException {
        ClientAppConfig config = em.find(ClientAppConfig.class, id);
        if(config == null) {
            throw new DTCNotFoundException("ClientAppConfig with id: " + id +
                                           " not found in the database.");
        }
        
        //update the app with the updates map
        for(Iterator<String> keyI = updates.keySet().iterator(); keyI.hasNext(); ) {
            String key = keyI.next();
            
            //update the configs if necessary
            if(key.equals("propertyMethod")) {
                ClientAppConfigTypeDTO type = Caster.cast(updates.get(key));
                ClientAppConfigType realType = getClientAppConfigType(type);
                config.setPropertyMethod(realType);
            }
            else if(key.equals("clientApp")) {
                ClientAppDTO app = Caster.cast(updates.get(key));
                ClientApp realApp = genericGet(ClientAppDTO.class,
                                               ClientApp.class,
                                               app);
                config.setClientApp(realApp);
            }
            else if(key.equals("properties")) {
                List<PropertyDTO> properties = Caster.cast(updates.get(key));
                List<Property> realProperties = buildProperties(properties);
                config.getProperties().clear();
                config.getProperties().addAll(realProperties);
            }
            else {
                try {
                    PropertyUtils.setProperty(config, key, updates.get(key));
                } catch(Exception e) {
                    throw new DTCServiceException("Unable to set property: " +
                                                  key + " on " + config, e);
                }
            }
        }
    }

    public void updateServerApp(Long id,
                                Map<String, Object> updates)
            throws DTCServiceException {
        ServerApp app = em.find(ServerApp.class, id);
        if(app == null) {
            throw new DTCNotFoundException("ServerApp with id: " + id +
                                           " not found in the database.");
        }
        
        //update the app with the updates map
        for(Iterator<String> keyI = updates.keySet().iterator(); keyI.hasNext(); ) {
            String key = keyI.next();
            
            //update the configs if necessary
            if(key.equals("configs")) {
                List<ServerAppConfigDTO> configs = Caster.cast(updates.get(key));
                app.getConfigs().clear();
                for (Iterator<ServerAppConfigDTO> ic = configs.iterator(); ic.hasNext();) {
                    ServerAppConfigDTO c = ic.next();
                    ServerAppConfig realc = genericGet(ServerAppConfigDTO.class,
                                                       ServerAppConfig.class,
                                                       c);
                    app.getConfigs().add(realc);
                }
            }
            else if(key.equals("requiredPkg")) {
                PkgLibraryDTO pkg = Caster.cast(updates.get(key));
                PkgLibrary realPkg = genericGet(PkgLibraryDTO.class,
                                                PkgLibrary.class,
                                                pkg);
                app.setRequiredPkg(realPkg);
            }
            else {
                try {
                    PropertyUtils.setProperty(app, key, updates.get(key));
                } catch(Exception e) {
                    throw new DTCServiceException("Unable to set property: " +
                                                  key + " on " + app, e);
                }
            }
        }
    }

    public void updateServerAppConfig(Long id, 
                                      Map<String, Object> updates) 
            throws DTCServiceException {
        ServerAppConfig config = em.find(ServerAppConfig.class, id);
        if(config == null) {
            throw new DTCNotFoundException("ServerAppConfig with id: " + id +
                                           " not found in the database.");
        }
        
        //update the app with the updates map
        for(Iterator<String> keyI = updates.keySet().iterator(); keyI.hasNext(); ) {
            String key = keyI.next();
            
            //update the configs if necessary
            if(key.equals("serverApp")) {
                ServerAppDTO app = Caster.cast(updates.get(key));
                ServerApp realApp = genericGet(ServerAppDTO.class,
                                               ServerApp.class,
                                               app);
                config.setServerApp(realApp);
            }
            else if(key.equals("properties")) {
                List<PropertyDTO> properties = Caster.cast(updates.get(key));
                List<Property> realProperties = buildProperties(properties);
                config.getProperties().clear();
                config.getProperties().addAll(realProperties);
            }
            else {
                try {
                    PropertyUtils.setProperty(config, key, updates.get(key));
                } catch(Exception e) {
                    throw new DTCServiceException("Unable to set property: " +
                                                  key + " on " + config, e);
                }
            }
        }
    }

    public void updateSystemProbe(Long id, 
                                  Map<String, Object> updates) 
            throws DTCServiceException {
        SystemProbe probe = em.find(SystemProbe.class, id);
        if(probe == null) {
            throw new DTCNotFoundException("SystemProbe with id: " + id +
                                           " not found in the database.");
        }
        
        //update the app with the updates map
        for(Iterator<String> keyI = updates.keySet().iterator(); keyI.hasNext(); ) {
            String key = keyI.next();
            
            //update the configs if necessary
            if(key.equals("tags")) {
                List<SystemProbeTagDTO> tags = Caster.cast(updates.get(key));
                List<SystemProbeTag> realTags = buildTags(tags);
                probe.getTags().clear();
                probe.getTags().addAll(realTags);
            }
            else if(key.equals("properties")) {
                List<PropertyDTO> properties = Caster.cast(updates.get(key));
                List<Property> realProperties = buildProperties(properties);
                probe.getProperties().clear();
                probe.getProperties().addAll(realProperties);
            }
            else if(key.equals("requiredPkg")) {
                PkgLibraryDTO pkg = Caster.cast(updates.get(key));
                PkgLibrary realPkg = genericGet(PkgLibraryDTO.class,
                                                PkgLibrary.class,
                                                pkg);
                probe.setRequiredPkg(realPkg);
            }
            else {
                try {
                    PropertyUtils.setProperty(probe, key, updates.get(key));
                } catch(Exception e) {
                    throw new DTCServiceException("Unable to set property: " +
                                                  key + " on " + probe, e);
                }
            }
        }
    }
    
    private List<Property> buildProperties(List<PropertyDTO> properties) {
        List<Property> props = new ArrayList<Property>(properties.size());
        for(PropertyDTO p : properties) {
            Property pcopy = null;
            Long id = p.getId();
            if(id != null) {
                pcopy = em.find(Property.class, id);
            }
            
            if(pcopy == null) {
                pcopy = new Property(p.getDescription(),
                                              p.getProperty(),
                                              p.getValue());
                em.persist(pcopy);
            }

            props.add(pcopy);
        }
        
        return props;
    }
    
    private List<SystemProbeTag> buildTags(List<SystemProbeTagDTO> tags) {
        List<SystemProbeTag> tagList = new ArrayList<SystemProbeTag>(tags.size());
        for(SystemProbeTagDTO t : tags) {
            try {
                SystemProbeTag realt = (SystemProbeTag) em.createQuery(
                        "from SystemProbeTag where tag=" + t.getTag()).getSingleResult();
                tagList.add(realt);
            } catch (NoResultException e) {
                SystemProbeTag nt = new SystemProbeTag(t.getTag());
                em.persist(nt);
                tagList.add(nt);
            }
        }

        return tagList;
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
    
    private <T extends AbstractDTO, V> V genericGet(Class<T> clazz,
                                                    Class<V> convert,
                                                    T obj) 
            throws DTCNotFoundException {
        if(obj == null) {
            throw new NullPointerException(clazz.getName() + " cannot be null");
        }
        
        Long id = obj.getId();
        V dbItem = em.find(convert, id);
        if(dbItem == null) {
            throw new DTCNotFoundException(convert.getName() +
                                           " with id: " +
                                           id +
                                           " not found in database");
        }
        
        return dbItem;
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
