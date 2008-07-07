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
 * Represents a complete test specification that pulls together all of the
 * details and parameters necessary to run a DTC test.
 */
public class TestSpecDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String description;
    private String testRunner;
    private Long timeLimit;
    private Long maxClients;
    
    private List<PropertyDTO> properties;
    
    private ServerAppConfigDTO serverAppConfig;
    private List<ClientAppConfigDTO> clientAppConfigs;
    private List<SystemProbeDTO> systemProbes;
    
    private List<HardwareResourceFamilyDTO> serverResources;
    private List<HardwareResourceFamilyDTO> clientResources;
    
    public TestSpecDTO(String name,
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
    
    public String getDescription() { return description; }
    protected void setDescription(String description) { this.description = description; }
    public void updateDescription(String description)
            throws DTCInvalidDataException {
        this.updateAttribute("description", description);
    }
    
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
    public String getTestRunner() { return testRunner; }
    protected void setTestRunner(String testRunner) { this.testRunner = testRunner; }
    public void updateTestRunner(String testRunner)
            throws DTCInvalidDataException {
        this.updateAttribute("testRunner", testRunner);
    }
    
    /**
     * Time limit in seconds to allow the test to run.  If the test
     * runs beyond the time limit, it is terminated.
     * 
     * @return time limit of test
     */
    public Long getTimeLimit() { return timeLimit; }
    protected void setTimeLimit(Long timeLimit) { this.timeLimit = timeLimit; }
    public void updateTimeLimit(Long timeLimit)
            throws DTCInvalidDataException {
        this.updateAttribute("timeLimit", timeLimit);
    }
    
    public Long getMaxClients() { return maxClients; }
    protected void setMaxClients(Long maxClients) { this.maxClients = maxClients; }
    public void updateMaxClient(Long maxClients)
            throws DTCInvalidDataException {
        this.updateAttribute("maxClients", maxClients);
    }
    
    /**
     * Returns a list of arguments in the form of {@link PropertyDTO} objects
     * to be passed to the TestRunner during run time.
     * 
     * @return list of arguments
     */
    public List<PropertyDTO> getProperties() { return properties; }
    protected void setProperties(List<PropertyDTO> properties) { this.properties = properties; }
    public void updateProperties(List<PropertyDTO> properties)
            throws DTCInvalidDataException {
        this.updateAttribute("properties", properties);
    }
    
    /**
     * Returns the complete configuration required to run the server
     * application to be used as the central process of this test.
     * 
     * @return the server application configuration
     */
    public ServerAppConfigDTO getServerAppConfig() { return serverAppConfig; }
    protected void setServerAppConfig(ServerAppConfigDTO serverAppConfig) { this.serverAppConfig = serverAppConfig; }
    public void updateServerAppConfig(ServerAppConfigDTO serverAppConfig)
            throws DTCInvalidDataException {
        this.updateAttribute("serverAppConfig", serverAppConfig);
    }
    
    /**
     * Returns the list of client application simulator configurations
     * to be used to stress the server during the test.
     * 
     * @return the client application simulator configurations
     */
    public List<ClientAppConfigDTO> getClientAppConfigs() { return clientAppConfigs; }
    protected void setClientAppConfigs(List<ClientAppConfigDTO> clientAppConfigs) { this.clientAppConfigs = clientAppConfigs; }
    public void updateClientAppConfigs(List<ClientAppConfigDTO> clientAppConfigs)
            throws DTCInvalidDataException {
        this.updateAttribute("clientAppConfigs", clientAppConfigs);
    }
    
    /**
     * Returns the list of system probes that are to be used to monitor
     * the state of the system while the test is running.
     * 
     * @return the system probes used to monitor the system during testing
     */
    public List<SystemProbeDTO> getSystemProbes() { return systemProbes; }
    protected void setSystemProbes(List<SystemProbeDTO> systemProbes) { this.systemProbes = systemProbes; }
    public void updateSystemProbes(List<SystemProbeDTO> systemProbes)
            throws DTCInvalidDataException {
        this.updateAttribute("systemProbes", systemProbes);
    }
    
    /**
     * <p>
     * Returns a list of {@link HardwareResourceFamilyDTO} objects representing
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
    public List<HardwareResourceFamilyDTO> getServerResources() { return serverResources; }
    protected void setServerResources(List<HardwareResourceFamilyDTO> serverResources) { this.serverResources = serverResources; }
    public void updateServerResources(List<HardwareResourceFamilyDTO> serverResources)
            throws DTCInvalidDataException {
        this.updateAttribute("serverResources", serverResources);
    }
    
    /**
     * <p>
     * Returns a list of {@link HardwareResourceFamilyDTO} objects representing
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
    public List<HardwareResourceFamilyDTO> getClientResources() { return clientResources; }
    protected void setClientResources(List<HardwareResourceFamilyDTO> clientResources) { this.clientResources = clientResources; }
    public void updateClientResources(List<HardwareResourceFamilyDTO> clientResources)
            throws DTCInvalidDataException {
        this.updateAttribute("clientResources", clientResources);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
}
