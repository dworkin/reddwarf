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

import java.util.List;
import java.util.SortedSet;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.ManyToMany;
import javax.persistence.JoinTable;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "TestSpec")
public class TestSpec implements Serializable
{
    private Long id;
    private String name;
    private String description;
    private String testRunner;
    private Long timeLimit;
    private Long maxClients;
    
    private SortedSet<Property> properties;
    
    private ServerAppConfig serverAppConfig;
    private List<ClientAppConfig> clientAppConfigs;
    private List<SystemProbe> systemProbes;
    
    private List<HardwareResourceFamily> serverResources;
    private List<HardwareResourceFamily> clientResources;
    
    public TestSpec(String name,
                    String description,
                    String testRunner,
                    Long timeLimit,
                    Long maxClients)
    {
        this.setName(name);
        this.setDescription(description);
        this.setTestRunner(testRunner);
        this.setTimeLimit(timeLimit);
        this.setMaxClients(maxClients);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Column(name = "name", nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Column(name = "description", nullable = false, length = 1024)
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    @Column(name = "testRunner", nullable = false)
    public String getTestRunner() { return testRunner; }
    public void setTestRunner(String testRunner) { this.testRunner = testRunner; }
    
    @Column(name = "timeLimit", nullable = false)
    public Long getTimeLimit() { return timeLimit; }
    public void setTimeLimit(Long timeLimit) { this.timeLimit = timeLimit; }
    
    @Column(name = "maxClients", nullable = false)
    public Long getMaxClients() { return maxClients; }
    public void setMaxClients(Long maxClients) { this.maxClients = maxClients; }
    
    
    @ManyToMany
    @JoinTable(name = "testSpecProperties",
               joinColumns = @JoinColumn(name = "testSpecId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public SortedSet<Property> getProperties() { return properties; }
    public void setProperties(SortedSet<Property> properties) { this.properties = properties; }
    
    @ManyToOne
    @JoinColumn(name = "serverAppConfig", nullable = false)
    public ServerAppConfig getServerAppConfig() { return serverAppConfig; }
    public void setServerAppConfig(ServerAppConfig serverAppConfig) { this.serverAppConfig = serverAppConfig; }
    
    @ManyToMany
    @JoinTable(name = "testSpecClientAppConfigs",
               joinColumns = @JoinColumn(name = "testSpecId"),
               inverseJoinColumns = @JoinColumn(name = "clientAppConfigId"))
    public List<ClientAppConfig> getClientAppConfigs() { return clientAppConfigs; }
    public void setClientAppConfigs(List<ClientAppConfig> clientAppConfigs) { this.clientAppConfigs = clientAppConfigs; }
    
    @ManyToMany
    @JoinTable(name = "testSpecSystemProbes",
               joinColumns = @JoinColumn(name = "testSpecId"),
               inverseJoinColumns = @JoinColumn(name = "systemProbeId"))
    public List<SystemProbe> getSystemProbes() { return systemProbes; }
    public void setSystemProbes(List<SystemProbe> systemProbes) { this.systemProbes = systemProbes; }
    
    @ManyToMany
    @JoinTable(name = "testSpecServerResources",
               joinColumns = @JoinColumn(name = "testSpecId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceFamilyId"))
    public List<HardwareResourceFamily> getServerResources() { return serverResources; }
    public void setServerResources(List<HardwareResourceFamily> serverResources) { this.serverResources = serverResources; }
    
    @ManyToMany
    @JoinTable(name = "testSpecClientResources",
               joinColumns = @JoinColumn(name = "testSpecId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceFamilyId"))
    public List<HardwareResourceFamily> getClientResources() { return clientResources; }
    public void setClientResources(List<HardwareResourceFamily> clientResources) { this.clientResources = clientResources; }
}
