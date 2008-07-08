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
import javax.persistence.Enumerated;
import javax.persistence.EnumType;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Version;

/**
 * Represents the results for a specific instance of a {@link TestSpec}.
 * Each TestExecutionResult is part of a parent {@link TestExecution}
 * to make up one cohesive set of test results.
 */
@Entity
@Table(name = "TestExecutionResult")
public class TestExecutionResult implements Serializable
{
    private Long id;
    private Long versionNumber;
    
    private TestExecutionResultValue result;
    private LogFile resultSummary;
    
    private List<Property> properties;
    private List<TestExecutionResultServerLog> serverLogs;
    private List<TestExecutionResultClientLog> clientLogs;
    private List<TestExecutionResultProbeLog> probeLogs;
    
    private List<TestExecutionResultClientData> clientData;
    
    private List<HardwareResource> serverResources;
    private List<HardwareResource> clientResources;
    
    private List<HardwareResourceFamily> originalServerResources;
    private List<HardwareResourceFamily> originalClientResources;
    private String originalTestSpecName;
    private String originalTestSpecDescription;
    private String originalTestSpecTestRunner;
    private Long originalTestSpecTimeLimit;
    private Long originalTestSpecMaxClients;
    private TestSpec originalTestSpec;
    
    private TestExecution parentExecution;
    
    public TestExecutionResult() {}
    
    public TestExecutionResult(TestSpec originalTestSpec,
                               TestExecution parentExecution)
    {
        this.setResult(TestExecutionResultValue.NOTRUN);
        this.setResultSummary(new LogFile(""));
        
        this.setProperties(new ArrayList<Property>());
        this.setServerLogs(new ArrayList<TestExecutionResultServerLog>());
        this.setClientLogs(new ArrayList<TestExecutionResultClientLog>());
        this.setProbeLogs(new ArrayList<TestExecutionResultProbeLog>());
        
        this.setClientData(new ArrayList<TestExecutionResultClientData>());
        this.setServerResources(new ArrayList<HardwareResource>());
        this.setClientResources(new ArrayList<HardwareResource>());
        
        this.setOriginalServerResources(originalTestSpec.getServerResources());
        this.setOriginalClientResources(originalTestSpec.getClientResources());
        this.setOriginalTestSpecName(originalTestSpec.getName());
        this.setOriginalTestSpecDescription(originalTestSpec.getDescription());
        this.setOriginalTestSpecTestRunner(originalTestSpec.getTestRunner());
        this.setOriginalTestSpecTimeLimit(originalTestSpec.getTimeLimit());
        this.setOriginalTestSpecMaxClients(originalTestSpec.getMaxClients());
        this.setOriginalTestSpec(originalTestSpec);
        
        this.setParentExecution(parentExecution);
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
    
    @Column(name = "result", nullable = false)
    @Enumerated(EnumType.STRING)
    public TestExecutionResultValue getResult() { return result; }
    public void setResult(TestExecutionResultValue result) { this.result = result; }
    
    @ManyToOne
    @JoinColumn(name = "resultSummary", nullable = false)
    public LogFile getResultSummary() { return resultSummary; }
    public void setResultSummary(LogFile resultSummary) { this.resultSummary = resultSummary; }
    
    /**
     * Returns a list of arguments in the form of {@link Property} objects
     * to be passed to the TestRunner during run time.  These are derived
     * from the original {@link TestSpec} used to create this
     * TestExecutionResult and should be customized for each specific case.
     * 
     * @return list of arguments
     */
    @ManyToMany
    @OrderBy("property")
    @JoinTable(name = "testExecutionResultProperties",
               joinColumns = @JoinColumn(name = "testExecutionResultId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }
    
    /**
     * A {@link TestExecutionResultServerLog} is generated for each
     * {@link HardwareResource} that the server application is run on during
     * the test.  Returns a list of these logs.
     * 
     * @return list of server logs
     */
    @OneToMany(mappedBy = "parentResult")
    public List<TestExecutionResultServerLog> getServerLogs() { return serverLogs; }
    public void setServerLogs(List<TestExecutionResultServerLog> serverLogs) { this.serverLogs = serverLogs; }
    
    /**
     * A {@link TestExecutionResultClientLog} is generated for each
     * {@link ClientAppConfig} client application simulator that is run
     * during the test.  Returns a list of these logs.
     * 
     * @return list of client logs
     */
    @OneToMany(mappedBy = "parentResult")
    public List<TestExecutionResultClientLog> getClientLogs() { return clientLogs; }
    public void setClientLogs(List<TestExecutionResultClientLog> clientLogs) { this.clientLogs = clientLogs; }
    
    /**
     * A {@link TestExecutionResultProbeLog} is generated for each
     * {@link SystemProbe} monitoring the system during the test.
     * Returns a list of these logs.
     * 
     * @return list of probe logs
     */
    @OneToMany(mappedBy = "parentResult")
    public List<TestExecutionResultProbeLog> getProbeLogs() { return probeLogs; }
    public void setProbeLogs(List<TestExecutionResultProbeLog> probeLogs) { this.probeLogs = probeLogs; }
    
    /**
     * A list of {@link TestExecutionResultClientData} objects are
     * periodically collected during the execution of a test to monitor how
     * many clients are acting in the system over time.  Returns a list
     * of these data objects.
     * 
     * @return list of client data points
     */
    @OneToMany(mappedBy="parentResult")
    @OrderBy("timestamp")
    public List<TestExecutionResultClientData> getClientData() { return clientData; }
    public void setClientData(List<TestExecutionResultClientData> clientData) { this.clientData = clientData; }
    
    
    /**
     * Returns a list of {@link HardwareResource} objects that are used to
     * run the server application on during the test.
     * 
     * @return list of server resources
     */
    @ManyToMany
    @OrderBy("hostname")
    @JoinTable(name = "testExecutionResultServerResources",
               joinColumns = @JoinColumn(name = "testExecutionResultId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceId"))
    public List<HardwareResource> getServerResources() { return serverResources; }
    public void setServerResources(List<HardwareResource> serverResources) { this.serverResources = serverResources; }
    
    /**
     * Returns a list of {@link HardwareResource} objects taht are used
     * to run the client application simulators during the test
     * 
     * @return list of client resources
     */
    @ManyToMany
    @OrderBy("hostname")
    @JoinTable(name = "testExecutionResultClientResources",
               joinColumns = @JoinColumn(name = "testExecutionResultId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceId"))
    public List<HardwareResource> getClientResources() { return clientResources; }
    public void setClientResources(List<HardwareResource> clientResources) { this.clientResources = clientResources; }
    
    
    @ManyToMany
    @OrderBy("hostname")
    @JoinTable(name = "testExecutionResultOriginalServerResources",
               joinColumns = @JoinColumn(name = "testExecutionResultId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceFamilyId"))
    public List<HardwareResourceFamily> getOriginalServerResources() { return originalServerResources; }
    private void setOriginalServerResources(List<HardwareResourceFamily> originalServerResources) { this.originalServerResources = originalServerResources; }
    
    @ManyToMany
    @OrderBy("hostname")
    @JoinTable(name = "testExecutionResultOriginalClientResources",
               joinColumns = @JoinColumn(name = "testExecutionResultId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceFamilyId"))
    public List<HardwareResourceFamily> getOriginalClientResources() { return originalClientResources; }
    private void setOriginalClientResources(List<HardwareResourceFamily> originalClientResources) { this.originalClientResources = originalClientResources; }
    
    
    
    
    @Column(name = "originalTestSpecName", nullable = false)
    public String getOriginalTestSpecName() { return originalTestSpecName; }
    private void setOriginalTestSpecName(String originalTestSpecName) { this.originalTestSpecName = originalTestSpecName; }
    
    @Column(name = "originalTestSpecDescription", nullable = false)
    public String getOriginalTestSpecDescription() { return originalTestSpecDescription; }
    private void setOriginalTestSpecDescription(String originalTestSpecDescription) { this.originalTestSpecDescription = originalTestSpecDescription; }
    
    @Column(name = "originalTestSpecTestRunner", nullable = false)
    public String getOriginalTestSpecTestRunner() { return originalTestSpecTestRunner; }
    private void setOriginalTestSpecTestRunner(String originalTestSpecTestRunner) { this.originalTestSpecTestRunner = originalTestSpecTestRunner; }
    
    @Column(name = "originalTestSpecTimeLimit", nullable = false)
    public Long getOriginalTestSpecTimeLimit() { return originalTestSpecTimeLimit; }
    private void setOriginalTestSpecTimeLimit(Long originalTestSpecTimeLimit) { this.originalTestSpecTimeLimit = originalTestSpecTimeLimit; }
    
    @Column(name = "originalTestSpecMaxClients", nullable = false)
    public Long getOriginalTestSpecMaxClients() { return originalTestSpecMaxClients; }
    private void setOriginalTestSpecMaxClients(Long originalTestSpecMaxClients) { this.originalTestSpecMaxClients = originalTestSpecMaxClients; }
    
    @ManyToOne
    @JoinColumn(name = "originalTestSpec", nullable = false)
    public TestSpec getOriginalTestSpec() { return originalTestSpec; }
    private void setOriginalTestSpec(TestSpec originalTestSpec) { this.originalTestSpec = originalTestSpec; }
    
    @ManyToOne
    @JoinColumn(name = "parentExecution", nullable = false)
    public TestExecution getParentExecution() { return parentExecution; }
    public void setParentExecution(TestExecution parentExecution) { this.parentExecution = parentExecution; }

}
