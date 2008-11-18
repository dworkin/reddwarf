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
import javax.persistence.GenerationType;
import javax.persistence.Column;
import javax.persistence.Enumerated;
import javax.persistence.EnumType;
import javax.persistence.ManyToOne;
import javax.persistence.ManyToMany;
import javax.persistence.JoinTable;
import javax.persistence.JoinColumn;
import javax.persistence.OrderBy;
import javax.persistence.Version;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a runtime configuration for a {@link ClientApp}.
 */
@Entity
@Table(name = "ClientAppConfig")
public class ClientAppConfig implements Serializable
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String path;
    private ClientAppConfigType propertyMethod;
    
    private ClientApp clientApp;
    private List<Property> properties;
    
    public ClientAppConfig() {}
    
    public ClientAppConfig(String name,
                           String path,
                           ClientAppConfigType propertyMethod,
                           ClientApp clientApp)
    {
        this.setName(name);
        this.setPath(path);
        this.setPropertyMethod(propertyMethod);
        this.setClientApp(clientApp);
        
        this.setProperties(new ArrayList<Property>());
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
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
     * Returns the system path required to initiate execution of this
     * client simulator.  The path could be a java executable or
     * some other executable type since the client is not required to be
     * a java application.
     * 
     * @return path of the client application executable
     */
    @Column(name = "path", nullable = false)
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    
    /**
     * Returns the mechanism required to pass arguments to the client
     * executable.
     * 
     * @return mechanism required to pass arguments to the client
     */
    @Column(name = "propertyMethod", nullable = false)
    @Enumerated(EnumType.STRING)
    public ClientAppConfigType getPropertyMethod() { return propertyMethod; }
    public void setPropertyMethod(ClientAppConfigType propertyMethod) { this.propertyMethod = propertyMethod; }
    
    /**
     * Returns the parent {@link ClientApp} which this configuration
     * is associated with.
     * 
     * @return parent {@link ClientApp} for this configuration
     */
    @ManyToOne
    @JoinColumn(name="clientApp", nullable = false)
    public ClientApp getClientApp() { return clientApp; }
    public void setClientApp(ClientApp clientApp) { this.clientApp = clientApp; }
    
    /**
     * Returns a list of arguments in the form of {@link Property} objects
     * to be passed to the client during run time.
     * 
     * @return list of arguments
     */
    @ManyToMany
    @OrderBy("property")
    @JoinTable(name = "clientAppConfigProperties",
               joinColumns = @JoinColumn(name = "clientAppConfigId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof ClientAppConfig) || o == null) return false;

        ClientAppConfig other = (ClientAppConfig)o;
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
