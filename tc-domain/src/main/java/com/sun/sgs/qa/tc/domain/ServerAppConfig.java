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

package com.sun.sgs.qa.tc.domain;

import java.util.SortedSet;
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

/**
 *
 * @author owen
 */
@Entity
@Table(name = "ServerAppConfig")
public class ServerAppConfig implements Serializable
{
    private Long id;
    private String name;
    private String additionalCommandLine;
    
    private ServerApp serverApp;
    private SortedSet<Property> properties;
    
    public ServerAppConfig(String name,
                           String additionalCommandLine)
    {
        this.setName(name);
        this.setAdditionalCommandLine(additionalCommandLine);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Column(name = "name", nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Column(name = "additionalCommandLine", nullable = false)
    public String getAdditionalCommandLine() { return additionalCommandLine; }
    public void setAdditionalCommandLine(String additionalCommandLine) { this.additionalCommandLine = additionalCommandLine; }
    
    @ManyToOne
    @JoinColumn(name="serverApp")
    public ServerApp getServerApp() { return serverApp; }
    public void setServerApp(ServerApp serverApp) { this.serverApp = serverApp; }
    
    @ManyToMany
    @JoinTable(name = "serverAppConfigProperties",
               joinColumns = @JoinColumn(name = "serverAppConfigId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public SortedSet<Property> getProperties() { return properties; }
    public void setProperties(SortedSet<Property> properties) { this.properties = properties; }
            
            
}
