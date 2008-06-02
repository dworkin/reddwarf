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
import javax.persistence.OrderBy;
import javax.persistence.Version;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "TestExecutionResultClientLog")
public class TestExecutionResultClientLog implements Serializable
{
    private Long id;
    private Long versionNumber;
    
    private HardwareResource resource;
    private LogFile logFile;
    
    private String originalClientAppName;
    private String originalClientAppDescription;
    private PkgLibrary originalClientAppRequiredPkg;
    
    private String originalClientAppConfigName;
    private String originalClientAppConfigPath;
    private ClientAppConfigType originalClientAppConfigPropertyMethod;
    private ClientAppConfig originalClientAppConfig;
    
    private SortedSet<Property> properties;
    private TestExecutionResult parentResult;
    
    
    public TestExecutionResultClientLog(ClientAppConfig originalClientAppConfig)
    {
        this.setOriginalClientAppName(originalClientAppConfig.getClientApp().getName());
        this.setOriginalClientAppDescription(originalClientAppConfig.getClientApp().getDescription());
        this.setOriginalClientAppRequiredPkg(originalClientAppConfig.getClientApp().getRequiredPkg());
        
        this.setOriginalClientAppConfigName(originalClientAppConfig.getName());
        this.setOriginalClientAppConfigPath(originalClientAppConfig.getPath());
        this.setOriginalClientAppConfigPropertyMethod(originalClientAppConfig.getPropertyMethod());
        this.setOriginalClientAppConfig(originalClientAppConfig);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
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
    
    
    @Column(name = "originalClientAppName", nullable = false)
    public String getOriginalClientAppName() { return originalClientAppName; }
    private void setOriginalClientAppName(String originalClientAppName) { this.originalClientAppName = originalClientAppName; }
    
    @Column(name = "originalClientAppDescription", nullable = false)
    public String getOriginalClientAppDescription() { return originalClientAppDescription; }
    private void setOriginalClientAppDescription(String originalClientAppDescription) { this.originalClientAppDescription = originalClientAppDescription; }
    
    @ManyToOne
    @JoinColumn(name = "originalClientAppRequiredPkg", nullable = false)
    public PkgLibrary getOriginalClientAppRequiredPkg() { return originalClientAppRequiredPkg; }
    private void setOriginalClientAppRequiredPkg(PkgLibrary originalClientAppRequiredPkg) { this.originalClientAppRequiredPkg = originalClientAppRequiredPkg; }
    
    @Column(name = "originalClientAppConfigName", nullable = false)
    public String getOriginalClientAppConfigName() { return originalClientAppConfigName; }
    private void setOriginalClientAppConfigName(String originalClientAppConfigName) { this.originalClientAppConfigName = originalClientAppConfigName; }
    
    @Column(name = "originalClientAppConfigPath", nullable = false)
    public String getOriginalClientAppConfigPath() { return originalClientAppConfigPath; }
    private void setOriginalClientAppConfigPath(String originalClientAppConfigPath) { this.originalClientAppConfigPath = originalClientAppConfigPath; }
    
    @Column(name = "originalClientAppConfigPropertyMethod", nullable = false)
    @Enumerated(EnumType.STRING)
    public ClientAppConfigType getOriginalClientAppConfigPropertyMethod() { return originalClientAppConfigPropertyMethod; }
    private void setOriginalClientAppConfigPropertyMethod(ClientAppConfigType originalClientAppConfigPropertyMethod) { this.originalClientAppConfigPropertyMethod = originalClientAppConfigPropertyMethod; }
    
    @ManyToOne
    @JoinColumn(name = "originalClientAppConfig", nullable = false)
    public ClientAppConfig getOriginalClientAppConfig() { return originalClientAppConfig; }
    private void setOriginalClientAppConfig(ClientAppConfig originalClientAppConfig) { this.originalClientAppConfig = originalClientAppConfig; }
    
    
    @ManyToMany
    @OrderBy("property")
    @JoinTable(name = "testExecutionResultClientLogProperties",
               joinColumns = @JoinColumn(name = "testExecutionResultClientLogId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public SortedSet<Property> getProperties() { return properties; }
    public void setProperties(SortedSet<Property> properties) { this.properties = properties; }
    
    @ManyToOne
    @JoinColumn(name = "parentResult", nullable = false)
    public TestExecutionResult getParentResult() { return parentResult; }
    public void setParentResult(TestExecutionResult parentResult) { this.parentResult = parentResult; }
}
