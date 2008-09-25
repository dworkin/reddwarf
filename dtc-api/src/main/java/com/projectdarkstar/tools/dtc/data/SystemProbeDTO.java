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

import com.projectdarkstar.tools.dtc.exceptions.DTCInvalidDataException;
import java.util.List;
import java.util.ArrayList;
import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a system probe application used to monitor and collect
 * statistics during a DTC test.
 */
public class SystemProbeDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String className;
    private String classPath;
    private String metric;
    private String units;
    
    private List<SystemProbeTagDTO> tags;
    
    private List<PropertyDTO> properties;
    private PkgLibraryDTO requiredPkg;
    
    public SystemProbeDTO(Long id,
                          Long versionNumber,
                          String name,
                          String className,
                          String classPath,
                          String metric,
                          String units)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setName(name);
        this.setClassName(className);
        this.setClassPath(classPath);
        this.setMetric(metric);
        this.setUnits(units);
        
        this.setTags(new ArrayList<SystemProbeTagDTO>());
        this.setProperties(new ArrayList<PropertyDTO>());
        this.setRequiredPkg(null);
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
    private void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    
    public String getName() { return name; }
    protected void setName(String name) { this.name = name; }
    public void updateName(String name)
            throws DTCInvalidDataException {
        this.updateAttribute("name", name);
    }
    
    /**
     * Returns the fully qualified class name of this system probe
     * required to initiate execution of this system probe
     * 
     * @return main class name for the system probe
     */
    public String getClassName() { return className; }
    protected void setClassName(String className) { this.className = className; }
    public void updateClassName(String className)
            throws DTCInvalidDataException {
        this.updateAttribute("className", className);
    }
    
    /**
     * Returns the classpath required to run the system probe application.
     * The items in this path are relative to the root of the filesystem
     * in the {@link #getRequiredPkg required} zip archive.
     * 
     * @return classpath required to run the system probe
     */
    public String getClassPath() { return classPath; }
    protected void setClassPath(String classPath) { this.classPath = classPath; }
    public void updateClassPath(String classPath)
            throws DTCInvalidDataException {
        this.updateAttribute("classPath", classPath);
    }
    
    /**
     * Returns the name of the metric that this system probe is designed
     * to measure.
     * 
     * @return metric that this system probe measures
     */
    public String getMetric() { return metric; }
    protected void setMetric(String metric) { this.metric = metric; }
    public void updateMetric(String metric)
            throws DTCInvalidDataException {
        this.updateAttribute("metric", metric);
    }
    
    /**
     * Returns the unit of measurement of the metric.
     * 
     * @return the unit of measurement of the metric.
     */
    public String getUnits() { return units; }
    protected void setUnits(String units) { this.units = units; }
    public void updateUnits(String units)
            throws DTCInvalidDataException {
        this.updateAttribute("units", units);
    }
    
    
    /**
     * Returns a list of {@link SystemProbeTagDTO} objects that are used
     * to categorize system probes into groups.
     * 
     * @return list of tags for this system probe
     */
    public List<SystemProbeTagDTO> getTags() { return tags; }
    protected void setTags(List<SystemProbeTagDTO> tags) { this.tags = tags; }
    public void updateTags(List<SystemProbeTagDTO> tags)
            throws DTCInvalidDataException {
        this.updateAttribute("tags", tags);
    }
    
    /**
     * Returns a list of arguments in the form of {@link PropertyDTO} objects
     * to be passed to the system probe during run time.
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
     * Returns the package library required to run this system probe.
     * It is assumed that this library is a zip archive.
     * 
     * @return the package library required to run this system probe
     */
    public PkgLibraryDTO getRequiredPkg() { return requiredPkg; }
    protected void setRequiredPkg(PkgLibraryDTO requiredPkg) { this.requiredPkg = requiredPkg; }
    public void updateRequiredPkg(PkgLibraryDTO requiredPkg)
            throws DTCInvalidDataException {
        this.updateAttribute("requiredPkg", requiredPkg);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException 
    {
        this.checkBlank("name");
        this.checkNull("className");
        this.checkNull("classPath");
        this.checkNull("metric");
        this.checkNull("units");
        this.checkNull("tags");
        this.checkNull("properties");
        this.checkNull("requiredPkg");
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof SystemProbeDTO) || o == null) return false;

        SystemProbeDTO other = (SystemProbeDTO)o;
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
