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
import javax.persistence.ManyToMany;
import javax.persistence.JoinTable;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OrderBy;
import javax.persistence.Version;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a system probe application used to monitor and collect
 * statistics during a DTC test.
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
    
    public SystemProbe() {}
    
    public SystemProbe(String name,
                       String className,
                       String classPath,
                       String metric,
                       String units,
                       PkgLibrary requiredPkg)
    {
        this.setName(name);
        this.setClassName(className);
        this.setClassPath(classPath);
        this.setMetric(metric);
        this.setUnits(units);
        this.setRequiredPkg(requiredPkg);
        
        this.setTags(new ArrayList<SystemProbeTag>());
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
    
    @Column(name = "name", nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    /**
     * Returns the fully qualified class name of this system probe
     * required to initiate execution of this system probe
     * 
     * @return main class name for the system probe
     */
    @Column(name = "className", nullable = false)
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    
    /**
     * Returns the classpath required to run the system probe application.
     * The items in this path are relative to the root of the filesystem
     * in the {@link #getRequiredPkg required} zip archive.
     * 
     * @return classpath required to run the system probe
     */
    @Column(name = "classPath", nullable = false)
    public String getClassPath() { return classPath; }
    public void setClassPath(String classPath) { this.classPath = classPath; }
    
    /**
     * Returns the name of the metric that this system probe is designed
     * to measure.
     * 
     * @return metric that this system probe measures
     */
    @Column(name = "metric", nullable = false)
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    
    /**
     * Returns the unit of measurement of the metric.
     * 
     * @return the unit of measurement of the metric.
     */
    @Column(name = "units", nullable = false)
    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }
    
    /**
     * Returns a list of {@link SystemProbeTag} objects that are used
     * to categorize system probes into groups.
     * 
     * @return list of tags for this system probe
     */
    @ManyToMany
    @OrderBy("tag")
    @JoinTable(name = "systemProbeTags",
               joinColumns = @JoinColumn(name = "systemProbeId"),
               inverseJoinColumns = @JoinColumn(name = "systemProbeTagId"))
    public List<SystemProbeTag> getTags() { return tags; }
    public void setTags(List<SystemProbeTag> tags) { this.tags = tags; }
    
    /**
     * Returns a list of arguments in the form of {@link Property} objects
     * to be passed to the system probe during run time.
     * 
     * @return list of arguments
     */
    @ManyToMany
    @OrderBy("property")
    @JoinTable(name = "systemProbeProperties",
               joinColumns = @JoinColumn(name = "systemProbeId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }
    
    /**
     * Returns the package library required to run this system probe.
     * It is assumed that this library is a zip archive.
     * 
     * @return the package library required to run this system probe
     */
    @ManyToOne
    @JoinColumn(name = "requiredPkg", nullable = false)
    public PkgLibrary getRequiredPkg() { return requiredPkg; }
    public void setRequiredPkg(PkgLibrary requiredPkg) { this.requiredPkg = requiredPkg; }
    
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof SystemProbe) || o == null) return false;

        SystemProbe other = (SystemProbe)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getName(), other.getName()) &&
                ObjectUtils.equals(this.getClassName(), other.getClassName()) &&
                ObjectUtils.equals(this.getClassPath(), other.getClassPath()) &&
                ObjectUtils.equals(this.getMetric(), other.getMetric()) &&
                ObjectUtils.equals(this.getUnits(), other.getUnits()) &&
                ObjectUtils.equals(this.getTags(), other.getTags()) &&
                ObjectUtils.equals(this.getProperties(), other.getProperties()) &&
                ObjectUtils.equals(this.getRequiredPkg(), other.getRequiredPkg());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashName = 31*hash + ObjectUtils.hashCode(this.getName());
        return hashId + hashName;
    }
}
