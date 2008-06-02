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
 *
 * @author owen
 */
@Entity
@Table(name = "TestExecutionResult")
public class TestExecutionResult implements Serializable
{
    private Long id;
    private Long versionNumber;
    
    private TestExecutionResultValue result;
    private LogFile resultSummary;
    
    private SortedSet<Property> properties;
    private List<TestExecutionResultServerLog> serverLogs;
    private List<TestExecutionResultClientLog> clientLogs;
    private List<TestExecutionResultProbeLog> probeLogs;
    
    private List<HardwareResource> serverResources;
    private List<HardwareResource> clientResources;
    
    private String originalTestSpecName;
    private String originalTestSpecDescription;
    private String originalTestSpecTestRunner;
    private Long originalTestSpecTimeLimit;
    private Long originalTestSpecMaxClients;
    private TestSpec originalTestSpec;
    
    private TestExecution parentExecution;
    
    public TestExecutionResult(TestSpec originalTestSpec)
    {
        this.setOriginalTestSpecName(originalTestSpec.getName());
        this.setOriginalTestSpecDescription(originalTestSpec.getDescription());
        this.setOriginalTestSpecTestRunner(originalTestSpec.getTestRunner());
        this.setOriginalTestSpecTimeLimit(originalTestSpec.getTimeLimit());
        this.setOriginalTestSpecMaxClients(originalTestSpec.getMaxClients());
        this.setOriginalTestSpec(originalTestSpec);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
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
    
    @ManyToMany
    @OrderBy("property")
    @JoinTable(name = "testExecutionResultProperties",
               joinColumns = @JoinColumn(name = "testExecutionResultId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public SortedSet<Property> getProperties() { return properties; }
    public void setProperties(SortedSet<Property> properties) { this.properties = properties; }
    
    @OneToMany(mappedBy = "parentResult")
    public List<TestExecutionResultServerLog> getServerLogs() { return serverLogs; }
    public void setServerLogs(List<TestExecutionResultServerLog> serverLogs) { this.serverLogs = serverLogs; }
    
    @OneToMany(mappedBy = "parentResult")
    public List<TestExecutionResultClientLog> getClientLogs() { return clientLogs; }
    public void setClientLogs(List<TestExecutionResultClientLog> clientLogs) { this.clientLogs = clientLogs; }
    
    @OneToMany(mappedBy = "parentResult")
    public List<TestExecutionResultProbeLog> getProbeLogs() { return probeLogs; }
    public void setProbeLogs(List<TestExecutionResultProbeLog> probeLogs) { this.probeLogs = probeLogs; }
    
    
    @ManyToMany
    @OrderBy("hostname")
    @JoinTable(name = "testExecutionResultServerResources",
               joinColumns = @JoinColumn(name = "testExecutionResultId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceId"))
    public List<HardwareResource> getServerResources() { return serverResources; }
    public void setServerResources(List<HardwareResource> serverResources) { this.serverResources = serverResources; }
    
    @ManyToMany
    @OrderBy("hostname")
    @JoinTable(name = "testExecutionResultClientResources",
               joinColumns = @JoinColumn(name = "testExecutionResultId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceId"))
    public List<HardwareResource> getClientResources() { return clientResources; }
    public void setClientResources(List<HardwareResource> clientResources) { this.clientResources = clientResources; }
    
    
    
    
    @Column(name = "originalTestSpecName", nullable = false)
    public String getOriginalTestSpecName() { return originalTestSpecName; }
    public void setOriginalTestSpecName(String originalTestSpecName) { this.originalTestSpecName = originalTestSpecName; }
    
    @Column(name = "originalTestSpecDescription", nullable = false)
    public String getOriginalTestSpecDescription() { return originalTestSpecDescription; }
    public void setOriginalTestSpecDescription(String originalTestSpecDescription) { this.originalTestSpecDescription = originalTestSpecDescription; }
    
    @Column(name = "originalTestSpecTestRunner", nullable = false)
    public String getOriginalTestSpecTestRunner() { return originalTestSpecTestRunner; }
    public void setOriginalTestSpecTestRunner(String originalTestSpecTestRunner) { this.originalTestSpecTestRunner = originalTestSpecTestRunner; }
    
    @Column(name = "originalTestSpecTimeLimit", nullable = false)
    public Long getOriginalTestSpecTimeLimit() { return originalTestSpecTimeLimit; }
    public void setOriginalTestSpecTimeLimit(Long originalTestSpecTimeLimit) { this.originalTestSpecTimeLimit = originalTestSpecTimeLimit; }
    
    @Column(name = "originalTestSpecMaxClients", nullable = false)
    public Long getOriginalTestSpecMaxClients() { return originalTestSpecMaxClients; }
    public void setOriginalTestSpecMaxClients(Long originalTestSpecMaxClients) { this.originalTestSpecMaxClients = originalTestSpecMaxClients; }
    
    @ManyToOne
    @JoinColumn(name = "originalTestSpec", nullable = false)
    public TestSpec getOriginalTestSpec() { return originalTestSpec; }
    public void setOriginalTestSpec(TestSpec originalTestSpec) { this.originalTestSpec = originalTestSpec; }
    
    @ManyToOne
    @JoinColumn(name = "parentExecution", nullable = false)
    public TestExecution getParentExecution() { return parentExecution; }
    public void setParentExecution(TestExecution parentExecution) { this.parentExecution = parentExecution; }

}
