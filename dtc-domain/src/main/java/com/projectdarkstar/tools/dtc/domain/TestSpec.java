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
import javax.persistence.OrderBy;
import javax.persistence.Version;

/**
 * Represents a complete test specification that pulls together all of the
 * details and parameters necessary to run a DTC test.
 */
@Entity
@Table(name = "TestSpec")
public class TestSpec implements Serializable
{
    private Long id;
    private Long versionNumber;
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
    
    @Column(name = "description", nullable = false, length = 1024)
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    /**
     * <p>
     * Returns the fully qualified class name of the test runner to be used
     * to execute the tests.  Different types of test runners have different
     * behaviors:
     * </p>
     * 
     * <ul>
     * <li>JUnitTestRunner - Each client application simulator is assumed to
     * be a suite of functional JUnit tests to be run against the server
     * application.  The JUnitTestRunner collects
     * and reports the results of the tests.</li>
     * <li>LoadTestRunner - Each client application simulator is injected
     * into the system and run against the server application.  The set of
     * system probes are also injected into the system, monitoring various
     * aspects.  If any system probe threshold violations occur, the test
     * is considered to fail.</li>
     * <li>CapacityTestRunner - Client application simulators are incrementally
     * introduced into the system until one or more of the system probes
     * experiences a threshold violation.  The number of clients that can
     * run in the system without experiencing any violations is the result
     * of the test.</li>
     * </ul>
     * 
     * @return test runner to be used for the tests
     */
    @Column(name = "testRunner", nullable = false)
    public String getTestRunner() { return testRunner; }
    public void setTestRunner(String testRunner) { this.testRunner = testRunner; }
    
    /**
     * Time limit in seconds to allow the test to run.  If the test
     * runs beyond the time limit, it is terminated.
     * 
     * @return time limit of test
     */
    @Column(name = "timeLimit", nullable = false)
    public Long getTimeLimit() { return timeLimit; }
    public void setTimeLimit(Long timeLimit) { this.timeLimit = timeLimit; }
    
    @Column(name = "maxClients", nullable = false)
    public Long getMaxClients() { return maxClients; }
    public void setMaxClients(Long maxClients) { this.maxClients = maxClients; }
    
    /**
     * Returns a list of arguments in the form of {@link Property} objects
     * to be passed to the TestRunner during run time.
     * 
     * @return list of arguments
     */
    @ManyToMany
    @JoinTable(name = "testSpecProperties",
               joinColumns = @JoinColumn(name = "testSpecId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public SortedSet<Property> getProperties() { return properties; }
    public void setProperties(SortedSet<Property> properties) { this.properties = properties; }
    
    /**
     * Returns the complete configuration required to run the server
     * application to be used as the central process of this test.
     * 
     * @return the server application configuration
     */
    @ManyToOne
    @JoinColumn(name = "serverAppConfig", nullable = false)
    public ServerAppConfig getServerAppConfig() { return serverAppConfig; }
    public void setServerAppConfig(ServerAppConfig serverAppConfig) { this.serverAppConfig = serverAppConfig; }
    
    /**
     * Returns the list of client application simulator configurations
     * to be used to stress the server during the test.
     * 
     * @return the client application simulator configurations
     */
    @ManyToMany
    @OrderBy("name")
    @JoinTable(name = "testSpecClientAppConfigs",
               joinColumns = @JoinColumn(name = "testSpecId"),
               inverseJoinColumns = @JoinColumn(name = "clientAppConfigId"))
    public List<ClientAppConfig> getClientAppConfigs() { return clientAppConfigs; }
    public void setClientAppConfigs(List<ClientAppConfig> clientAppConfigs) { this.clientAppConfigs = clientAppConfigs; }
    
    /**
     * Returns the list of system probes that are to be used to monitor
     * the state of the system while the test is running.
     * 
     * @return the system probes used to monitor the system during testing
     */
    @ManyToMany
    @OrderBy("name")
    @JoinTable(name = "testSpecSystemProbes",
               joinColumns = @JoinColumn(name = "testSpecId"),
               inverseJoinColumns = @JoinColumn(name = "systemProbeId"))
    public List<SystemProbe> getSystemProbes() { return systemProbes; }
    public void setSystemProbes(List<SystemProbe> systemProbes) { this.systemProbes = systemProbes; }
    
    /**
     * <p>
     * Returns a list of {@link HardwareResourceFamily} objects representing
     * the class of hardware resources that the server application should
     * be run on.  Before running a test, one resource of each family type
     * must be locked.
     * </p>
     * 
     * <p>
     * If there is only one resource in the list, a single node instance
     * of the server is started.  If there are multiple resources, the first
     * node is used to startup the core node, while the remaining are used
     * for app nodes.
     * </p>
     * @return list of server resources
     */
    @ManyToMany
    @OrderBy("name")
    @JoinTable(name = "testSpecServerResources",
               joinColumns = @JoinColumn(name = "testSpecId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceFamilyId"))
    public List<HardwareResourceFamily> getServerResources() { return serverResources; }
    public void setServerResources(List<HardwareResourceFamily> serverResources) { this.serverResources = serverResources; }
    
    /**
     * <p>
     * Returns a list of {@link HardwareResourceFamily} objects representing
     * the class of hardware resources that the client application simulators
     * should be run on.  Before running a test, one resource of each
     * family type must be locked.
     * </p>
     * 
     * <p>
     * Each client is assigned to a resource in round-robin fashion.  A resource
     * may be assigned more than one client application simulator.
     * </p>
     * 
     * @return list of client resources
     */
    @ManyToMany
    @OrderBy("name")
    @JoinTable(name = "testSpecClientResources",
               joinColumns = @JoinColumn(name = "testSpecId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceFamilyId"))
    public List<HardwareResourceFamily> getClientResources() { return clientResources; }
    public void setClientResources(List<HardwareResourceFamily> clientResources) { this.clientResources = clientResources; }
}
