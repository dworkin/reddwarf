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
import javax.persistence.OneToMany;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;
import javax.persistence.OrderBy;
import javax.persistence.Version;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a server application that can be run as the central
 * process in a DTC test.
 */
@Entity
@Table(name = "ServerApp")
public class ServerApp implements Serializable
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String description;
    
    private List<ServerAppConfig> configs;
    private PkgLibrary requiredPkg;
    
    public ServerApp() {}
    
    public ServerApp(String name,
                     String description,
                     PkgLibrary requiredPkg)
    {
        this.setName(name);
        this.setDescription(description);
        this.setRequiredPkg(requiredPkg);
        
        this.setConfigs(new ArrayList<ServerAppConfig>());
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
    
    @Column(name= "description", nullable = false, length = 1024)
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    /**
     * Returns a list of server application configurations that can be used
     * to run this server application.
     * 
     * @return list of runtime configurations for this server app
     */
    @OneToMany(mappedBy = "serverApp")
    @OrderBy("name")
    public List<ServerAppConfig> getConfigs() { return configs; }
    public void setConfigs(List<ServerAppConfig> configs) { this.configs = configs; }
    
    /**
     * Returns the package library required to run this server application.
     * It is assumed that this library is a zip archive.
     * 
     * @return the package library required to run this server application.
     */
    @ManyToOne
    @JoinColumn(name = "requiredPkg", nullable = false)
    public PkgLibrary getRequiredPkg() { return requiredPkg; }
    public void setRequiredPkg(PkgLibrary requiredPkg) { this.requiredPkg = requiredPkg; }
    
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof ServerApp) || o == null) return false;

        ServerApp other = (ServerApp)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getName(), other.getName()) &&
                ObjectUtils.equals(this.getDescription(), other.getDescription()) &&
                ObjectUtils.equals(this.getConfigs(), other.getConfigs()) &&
                ObjectUtils.equals(this.getRequiredPkg(), other.getRequiredPkg());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashName = 31*hash + ObjectUtils.hashCode(this.getName());
        return hashId + hashName;
    }
    
}
