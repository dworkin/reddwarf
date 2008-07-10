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

package com.projectdarkstar.tools.dtc.data;

import com.projectdarkstar.tools.dtc.service.DTCInvalidDataException;
import java.util.List;
import java.util.ArrayList;
import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a runtime configuration for a {@link ClientAppDTO}.
 */
public class ClientAppConfigDTO extends AbstractDTO 
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String path;
    private ClientAppConfigTypeDTO propertyMethod;
    
    private ClientAppDTO clientApp;
    private List<PropertyDTO> properties;
    
    public ClientAppConfigDTO(Long id,
                              Long versionNumber,
                              String name,
                              String path,
                              ClientAppConfigTypeDTO propertyMethod)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setName(name);
        this.setPath(path);
        this.setPropertyMethod(propertyMethod);
        
        this.setClientApp(null);
        this.setProperties(new ArrayList<PropertyDTO>());
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    /**
     * Returns the version number in the data store that this entity represents.
     * Whenever an update to an object is pushed to the persistent data
     * store, the version number is incremented.
     * 
     * @return version number of the entity
     */
    public Long getVersionNumber() { return versionNumber; }
    private void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    
    public String getName() { return name; }
    protected void setName(String name) { this.name = name; }
    public void updateName(String name) 
            throws DTCInvalidDataException {
        this.updateAttribute("name", name);
    }
    
    /**
     * Returns the system path required to initiate execution of this
     * client simulator.  The path could be a java executable or
     * some other executable type since the client is not required to be
     * a java application.
     * 
     * @return path of the client application executable
     */
    public String getPath() { return path; }
    protected void setPath(String path) { this.path = path; }
    public void updatePath(String path)
            throws DTCInvalidDataException {
        this.updateAttribute("path", path);
    }
    
    /**
     * Returns the mechanism required to pass arguments to the client
     * executable.
     * 
     * @return mechanism required to pass arguments to the client
     */
    public ClientAppConfigTypeDTO getPropertyMethod() { return propertyMethod; }
    protected void setPropertyMethod(ClientAppConfigTypeDTO propertyMethod) { this.propertyMethod = propertyMethod; }
    public void updatePropertyMethod(ClientAppConfigTypeDTO propertyMethod)
            throws DTCInvalidDataException {
        this.updateAttribute("propertyMethod", propertyMethod);
    }
    
    /**
     * Returns the parent {@link ClientAppDTO} which this configuration
     * is associated with.
     * 
     * @return parent {@link ClientAppDTO} for this configuration
     */
    public ClientAppDTO getClientApp() { return clientApp; }
    protected void setClientApp(ClientAppDTO clientApp) { this.clientApp = clientApp; }
    public void updateClientApp(ClientAppDTO clientApp)
            throws DTCInvalidDataException {
        this.updateAttribute("clientApp", clientApp);
    }
    
    /**
     * Returns a list of arguments in the form of {@link PropertyDTO} objects
     * to be passed to the client during run time.
     * 
     * @return list of arguments
     */
    public List<PropertyDTO> getProperties() { return properties; }
    protected void setProperties(List<PropertyDTO> properties) { this.properties = properties; }
    public void updateProperties(List<PropertyDTO> properties)
            throws DTCInvalidDataException {
        this.updateAttribute("properties", properties);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof ClientAppConfigDTO) || o == null) return false;

        ClientAppConfigDTO other = (ClientAppConfigDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getName(), other.getName()) &&
                ObjectUtils.equals(this.getPath(), other.getPath()) &&
                ObjectUtils.equals(this.getPropertyMethod(), other.getPropertyMethod()) &&
                ObjectUtils.equals(this.getClientApp(), other.getClientApp()) &&
                ObjectUtils.equals(this.getProperties(), other.getProperties());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashName = 31*hash + ObjectUtils.hashCode(this.getName());
        return hashId + hashName;
    }
}
