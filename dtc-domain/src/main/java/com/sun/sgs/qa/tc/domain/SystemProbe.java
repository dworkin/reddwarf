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
 *
 * @author owen
 */
@Entity
@Table(name = "SystemProbe")
public class SystemProbe implements Serializable
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String className;
    private String classPath;
    private String metric;
    private String units;
    
    private List<SystemProbeTag> tags;
    
    private List<Property> properties;
    private PkgLibrary requiredPkg;
    
    public SystemProbe(String name,
                       String className,
                       String classPath,
                       String metric,
                       String units)
    {
        this.setName(name);
        this.setClassName(className);
        this.setClassPath(classPath);
        this.setMetric(metric);
        this.setUnits(units);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Version
    @Column(name = "versionNumber")
    public Long getVersionNumber() { return versionNumber; }
    protected void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    
    @Column(name = "name", nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Column(name = "className", nullable = false)
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    
    @Column(name = "classPath", nullable = false)
    public String getClassPath() { return classPath; }
    public void setClassPath(String classPath) { this.classPath = classPath; }
    
    @Column(name = "metric", nullable = false)
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    
    @Column(name = "units", nullable = false)
    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }
    
    @ManyToMany
    @OrderBy("tag")
    @JoinTable(name = "systemProbeTags",
               joinColumns = @JoinColumn(name = "systemProbeId"),
               inverseJoinColumns = @JoinColumn(name = "systemProbeTagId"))
    public List<SystemProbeTag> getTags() { return tags; }
    public void setTags(List<SystemProbeTag> tags) { this.tags = tags; }
    
    @ManyToMany
    @OrderBy("property")
    @JoinTable(name = "systemProbeProperties",
               joinColumns = @JoinColumn(name = "systemProbeId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }
    
    @ManyToOne
    @JoinColumn(name = "requiredPkg", nullable = false)
    public PkgLibrary getRequiredPkg() { return requiredPkg; }
    public void setRequiredPkg(PkgLibrary requiredPkg) { this.requiredPkg = requiredPkg; }
}
