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

import org.apache.commons.lang.ObjectUtils;

/**
 * Captures complete runtime configuration and result log file for the
 * execution of the server application on a specific resource during
 * execution of the test.
 */
@Entity
@Table(name = "TestExecutionResultServerLog")
public class TestExecutionResultServerLog implements Serializable
{
    private Long id;
    private Long versionNumber;
    
    private HardwareResource resource;
    private LogFile logFile;
    
    private String originalServerAppName;
    private String originalServerAppDescription;
    private PkgLibrary originalServerAppRequiredPkg;
    
    private String originalServerAppConfigName;
    private String originalServerAppConfigAdditionalCommandLine;
    private ServerAppConfig originalServerAppConfig;
    
    private List<Property> properties;
    private TestExecutionResult parentResult;
    
    public TestExecutionResultServerLog() {}
    
    public TestExecutionResultServerLog(HardwareResource resource,
                                        ServerAppConfig originalServerAppConfig,
                                        TestExecutionResult parentResult)
    {
        this.setResource(resource);
        this.setLogFile(new LogFile(""));
        
        this.setOriginalServerAppName(originalServerAppConfig.getServerApp().getName());
        this.setOriginalServerAppDescription(originalServerAppConfig.getServerApp().getDescription());
        this.setOriginalServerAppRequiredPkg(originalServerAppConfig.getServerApp().getRequiredPkg());
        
        this.setOriginalServerAppConfigName(originalServerAppConfig.getName());
        this.setOriginalServerAppConfigAdditionalCommandLine(originalServerAppConfig.getAdditionalCommandLine());
        this.setOriginalServerAppConfig(originalServerAppConfig);
        
        this.setParentResult(parentResult);
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
    
    @ManyToOne
    @JoinColumn(name = "resource")
    public HardwareResource getResource() { return resource; }
    public void setResource(HardwareResource resource) { this.resource = resource; }
    
    @ManyToOne
    @JoinColumn(name = "logFile", nullable = false)
    public LogFile getLogFile() { return logFile; }
    public void setLogFile(LogFile logFile) { this.logFile = logFile; }
    
    @Column(name = "originalServerAppName", nullable = false)
    public String getOriginalServerAppName() { return originalServerAppName; }
    private void setOriginalServerAppName(String originalServerAppName) { this.originalServerAppName = originalServerAppName; }
    
    @Column(name = "originalServerAppDescription", nullable = false)
    public String getOriginalServerAppDescription() { return originalServerAppDescription; }
    private void setOriginalServerAppDescription(String originalServerAppDescription) { this.originalServerAppDescription = originalServerAppDescription; }
    
    @ManyToOne
    @JoinColumn(name = "originalServerAppRequiredPkg", nullable = false)
    public PkgLibrary getOriginalServerAppRequiredPkg() { return originalServerAppRequiredPkg; }
    private void setOriginalServerAppRequiredPkg(PkgLibrary originalServerAppRequiredPkg) { this.originalServerAppRequiredPkg = originalServerAppRequiredPkg; }
    
    @Column(name = "originalServerAppConfigName", nullable = false)
    public String getOriginalServerAppConfigName() { return originalServerAppConfigName; }
    private void setOriginalServerAppConfigName(String originalServerAppConfigName) { this.originalServerAppConfigName = originalServerAppConfigName; }
    
    @Column(name = "originalServerAppConfigAdditionalCommandLine", nullable = false)
    public String getOriginalServerAppConfigAdditionalCommandLine() { return originalServerAppConfigAdditionalCommandLine; }
    private void setOriginalServerAppConfigAdditionalCommandLine(String originalServerAppConfigAdditionalCommandLine) { this.originalServerAppConfigAdditionalCommandLine = originalServerAppConfigAdditionalCommandLine; }
    
    @ManyToOne
    @JoinColumn(name = "originalServerAppConfig", nullable = false)
    public ServerAppConfig getOriginalServerAppConfig() { return originalServerAppConfig; }
    private void setOriginalServerAppConfig(ServerAppConfig originalServerAppConfig) { this.originalServerAppConfig = originalServerAppConfig; }
    
    
    @ManyToMany
    @OrderBy("property")
    @JoinTable(name = "testExecutionResultServerLogProperties",
               joinColumns = @JoinColumn(name = "testExecutionResultServerLogId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }
    
    @ManyToOne
    @JoinColumn(name = "parentResult", nullable = false)
    public TestExecutionResult getParentResult() { return parentResult; }
    public void setParentResult(TestExecutionResult parentResult) { this.parentResult = parentResult; }
    
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof TestExecutionResultServerLog) || o == null) return false;

        TestExecutionResultServerLog other = (TestExecutionResultServerLog)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getResource(), other.getResource()) &&
                ObjectUtils.equals(this.getLogFile(), other.getLogFile()) &&
                ObjectUtils.equals(this.getOriginalServerAppName(), other.getOriginalServerAppName()) &&
                ObjectUtils.equals(this.getOriginalServerAppDescription(), other.getOriginalServerAppDescription()) &&
                ObjectUtils.equals(this.getOriginalServerAppRequiredPkg(), other.getOriginalServerAppRequiredPkg()) &&
                ObjectUtils.equals(this.getOriginalServerAppConfigName(), other.getOriginalServerAppConfigName()) &&
                ObjectUtils.equals(this.getOriginalServerAppConfigAdditionalCommandLine(), other.getOriginalServerAppConfigAdditionalCommandLine()) &&
                ObjectUtils.equals(this.getOriginalServerAppConfig(), other.getOriginalServerAppConfig()) &&
                ObjectUtils.equals(this.getProperties(), other.getProperties()) &&
                ObjectUtils.equals(this.getParentResult(), other.getParentResult());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashOriginalServerAppName = 31*hash + ObjectUtils.hashCode(this.getOriginalServerAppName());
        return hashId + hashOriginalServerAppName;
    }
    
}
