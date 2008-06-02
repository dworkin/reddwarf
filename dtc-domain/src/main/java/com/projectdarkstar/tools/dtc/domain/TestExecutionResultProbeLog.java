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
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Version;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "TestExecutionResultProbeLog")
public class TestExecutionResultProbeLog implements Serializable
{
    private Long id;
    private Long versionNumber;
    
    private HardwareResource resource;
    private LogFile logFile;
    
    private String originalSystemProbeName;
    private String originalSystemProbeClassName;
    private String originalSystemProbeClassPath;
    private String originalSystemProbeMetric;
    private String originalSystemProbeUnits;
    private PkgLibrary originalSystemProbeRequiredPkg;
    private SystemProbe originalSystemProbe;
    
    private SortedSet<Property> properties;
    private TestExecutionResult parentResult;
    
    private List<TestExecutionResultProbeData> data;
    
    public TestExecutionResultProbeLog(SystemProbe originalSystemProbe)
    {
        this.setOriginalSystemProbeName(originalSystemProbe.getName());
        this.setOriginalSystemProbeClassName(originalSystemProbe.getClassName());
        this.setOriginalSystemProbeClassPath(originalSystemProbe.getClassPath());
        this.setOriginalSystemProbeMetric(originalSystemProbe.getMetric());
        this.setOriginalSystemProbeUnits(originalSystemProbe.getUnits());
        this.setOriginalSystemProbeRequiredPkg(originalSystemProbe.getRequiredPkg());
        this.setOriginalSystemProbe(originalSystemProbe);
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
    
    
    @Column(name = "originalSystemProbeName", nullable = false)
    public String getOriginalSystemProbeName() { return originalSystemProbeName; }
    private void setOriginalSystemProbeName(String originalSystemProbeName) { this.originalSystemProbeName = originalSystemProbeName; }
    
    @Column(name = "originalSystemProbeClassName", nullable = false)
    public String getOriginalSystemProbeClassName() { return originalSystemProbeClassName; }
    private void setOriginalSystemProbeClassName(String originalSystemProbeClassName) { this.originalSystemProbeClassName = originalSystemProbeClassName; }
    
    @Column(name = "originalSystemProbeClassPath", nullable = false)
    public String getOriginalSystemProbeClassPath() { return originalSystemProbeClassPath; }
    private void setOriginalSystemProbeClassPath(String originalSystemProbeClassPath) { this.originalSystemProbeClassPath = originalSystemProbeClassPath; }
    
    @Column(name = "originalSystemProbeMetric", nullable = false)
    public String getOriginalSystemProbeMetric() { return originalSystemProbeMetric; }
    private void setOriginalSystemProbeMetric(String originalSystemProbeMetric) { this.originalSystemProbeMetric = originalSystemProbeMetric; }
    
    @Column(name = "originalSystemProbeUnits", nullable = false)
    public String getOriginalSystemProbeUnits() { return originalSystemProbeUnits; }
    private void setOriginalSystemProbeUnits(String originalSystemProbeUnits) { this.originalSystemProbeUnits = originalSystemProbeUnits; }
    
    @ManyToOne
    @JoinColumn(name = "originalSystemProbeRequiredPkg", nullable = false)
    public PkgLibrary getOriginalSystemProbeRequiredPkg() { return originalSystemProbeRequiredPkg; }
    private void setOriginalSystemProbeRequiredPkg(PkgLibrary originalSystemProbeRequiredPkg) { this.originalSystemProbeRequiredPkg = originalSystemProbeRequiredPkg; }
    
    @ManyToOne
    @JoinColumn(name = "originalSystemProbe", nullable = false)
    public SystemProbe getOriginalSystemProbe() { return originalSystemProbe; }
    public void setOriginalSystemProbe(SystemProbe originalSystemProbe) { this.originalSystemProbe = originalSystemProbe; }
    
    
    
    
    @ManyToMany
    @OrderBy("property")
    @JoinTable(name = "testExecutionResultProbeLogProperties",
               joinColumns = @JoinColumn(name = "testExecutionResultProbeLogId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public SortedSet<Property> getProperties() { return properties; }
    public void setProperties(SortedSet<Property> properties) { this.properties = properties; }
    
    @ManyToOne
    @JoinColumn(name = "parentResult", nullable = false)
    public TestExecutionResult getParentResult() { return parentResult; }
    public void setParentResult(TestExecutionResult parentResult) { this.parentResult = parentResult; }
    
    @OneToMany(mappedBy="parentProbe")
    @OrderBy("timestamp")
    public List<TestExecutionResultProbeData> getData() { return data; }
    public void setData(List<TestExecutionResultProbeData> data) { this.data = data; }
}
