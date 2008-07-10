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
 * Represents a runtime configuration for a {@link ServerAppDTO}
 */
public class ServerAppConfigDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String additionalCommandLine;
    
    private ServerAppDTO serverApp;
    private List<PropertyDTO> properties;
    
    public ServerAppConfigDTO(Long id,
                              Long versionNumber,
                              String name,
                              String additionalCommandLine)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setName(name);
        this.setAdditionalCommandLine(additionalCommandLine);
        
        this.setServerApp(null);
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
     * Returns a string to be appended to the runtime command line used
     * to start the server application.  This may be used to do things such
     * as modify JVM parameters.
     * 
     * @return string to append to the command line
     */
    public String getAdditionalCommandLine() { return additionalCommandLine; }
    protected void setAdditionalCommandLine(String additionalCommandLine) { this.additionalCommandLine = additionalCommandLine; }
    
    /**
     * Returns the parent {@link ServerAppDTO} which this configuration
     * is associated with.
     * 
     * @return parent {@link ServerAppDTO} for this configuration
     */
    public ServerAppDTO getServerApp() { return serverApp; }
    protected void setServerApp(ServerAppDTO serverApp) { this.serverApp = serverApp; }
    
    /**
     * Returns a list of arguments in the form of {@link PropertyDTO} objects
     * to be passed to the server during run time.
     * 
     * @return list of arguments
     */
    public List<PropertyDTO> getProperties() { return properties; }
    protected void setProperties(List<PropertyDTO> properties) { this.properties = properties; }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException
    {
        this.checkBlank("name");
        this.checkNull("additionalCommandLine");
        this.checkNull("serverApp");
        this.checkNull("properties");
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof ServerAppConfigDTO) || o == null) return false;

        ServerAppConfigDTO other = (ServerAppConfigDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getName(), other.getName()) &&
                ObjectUtils.equals(this.getAdditionalCommandLine(), other.getAdditionalCommandLine()) &&
                ObjectUtils.equals(this.getServerApp(), other.getServerApp()) &&
                ObjectUtils.equals(this.getProperties(), other.getProperties());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashName = 31*hash + ObjectUtils.hashCode(this.getName());
        return hashId + hashName;
    }
}
