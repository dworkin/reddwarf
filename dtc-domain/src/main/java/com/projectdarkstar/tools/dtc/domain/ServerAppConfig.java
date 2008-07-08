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

package com.projectdarkstar.tools.dtc.domain;

import java.util.List;
import java.util.ArrayList;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.JoinTable;
import javax.persistence.OrderBy;
import javax.persistence.Version;

/**
 * Represents a runtime configuration for a {@link ServerApp}
 */
@Entity
@Table(name = "ServerAppConfig")
public class ServerAppConfig implements Serializable
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String additionalCommandLine;
    
    private ServerApp serverApp;
    private List<Property> properties;
    
    public ServerAppConfig() {}
    
    public ServerAppConfig(String name,
                           String additionalCommandLine,
                           ServerApp serverApp)
    {
        this.setName(name);
        this.setAdditionalCommandLine(additionalCommandLine);
        this.setServerApp(serverApp);
        
        this.setProperties(new ArrayList<Property>());
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    /**
     * Returns the version number in the data store that this entity represents.
     * Whenever an update to an object is pushed to the persistent data
     * store, the version number is incremented.
     * 
     * @return version number of the entity
     */
    @Version
    @Column(name = "versionNumber")
    public Long getVersionNumber() { return versionNumber; }
    protected void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    
    @Column(name = "name", nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    /**
     * Returns a string to be appended to the runtime command line used
     * to start the server application.  This may be used to do things such
     * as modify JVM parameters.
     * 
     * @return string to append to the command line
     */
    @Column(name = "additionalCommandLine", nullable = false)
    public String getAdditionalCommandLine() { return additionalCommandLine; }
    public void setAdditionalCommandLine(String additionalCommandLine) { this.additionalCommandLine = additionalCommandLine; }
    
    /**
     * Returns the parent {@link ServerApp} which this configuration
     * is associated with.
     * 
     * @return parent {@link ServerApp} for this configuration
     */
    @ManyToOne
    @JoinColumn(name="serverApp", nullable = false)
    public ServerApp getServerApp() { return serverApp; }
    public void setServerApp(ServerApp serverApp) { this.serverApp = serverApp; }
    
    /**
     * Returns a list of arguments in the form of {@link Property} objects
     * to be passed to the server during run time.
     * 
     * @return list of arguments
     */
    @ManyToMany
    @OrderBy("property")
    @JoinTable(name = "serverAppConfigProperties",
               joinColumns = @JoinColumn(name = "serverAppConfigId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }
            
            
}
