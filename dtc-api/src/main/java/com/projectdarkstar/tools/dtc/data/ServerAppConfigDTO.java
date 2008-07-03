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
    
    public ServerAppConfigDTO(String name,
                              String additionalCommandLine)
    {
        this.setName(name);
        this.setAdditionalCommandLine(additionalCommandLine);
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
    public void validate() throws DTCInvalidDataException {}
}
