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
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.OneToMany;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;
import javax.persistence.OrderBy;
import javax.persistence.Version;

/**
 * Represents a client application simulator
 * package to be run against a {@link ServerApp} in a DTC test.
 */
@Entity
@Table(name = "ClientApp")
public class ClientApp implements Serializable
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String description;
    
    private List<ClientAppConfig> configs;
    private PkgLibrary requiredPkg;
    
    public ClientApp(String name,
                     String description)
    {
        this.setName(name);
        this.setDescription(description);
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
    
    @Column(name = "description", nullable = false)
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * Returns a list of client application configurations that can be used
     * to run this client application.
     * 
     * @return list of runtime configurations for this client app
     */
    @OneToMany(mappedBy = "clientApp")
    @OrderBy("name")
    public List<ClientAppConfig> getConfigs() { return configs; }
    public void setConfigs(List<ClientAppConfig> configs) { this.configs = configs; }
    
    /**
     * Returns the package library required to run this client application.
     * 
     * @return the package library required to run this client application.
     */
    @ManyToOne
    @JoinColumn(name = "requiredPkg", nullable = false)
    public PkgLibrary getRequiredPkg() { return requiredPkg; }
    public void setRequiredPkg(PkgLibrary requiredPkg) { this.requiredPkg = requiredPkg; }
}
