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
 * Represents a server application that can be run as the central
 * process in a DTC test.
 */
public class ServerAppDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String description;
    private String className;
    private String classPath;
    
    private List<ServerAppConfigDTO> configs;
    private PkgLibraryDTO requiredPkg;
    
    public ServerAppDTO(Long id,
                        Long versionNumber,
                        String name,
                        String description,
                        String className,
                        String classPath)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setName(name);
        this.setDescription(description);
        this.setClassName(className);
        this.setClassPath(classPath);
        
        this.setConfigs(new ArrayList<ServerAppConfigDTO>());
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
    
    public String getDescription() { return description; }
    protected void setDescription(String description) { this.description = description; }
    public void updateDescription(String description)
            throws DTCInvalidDataException {
        this.updateAttribute("description", description);
    }
    
    /**
     * Returns the main class name of this server application that implements
     * the AppListener interface in the sgs core.  This should be a fully
     * qualified class name.
     * 
     * @return main class name for the server application
     */
    public String getClassName() { return className; }
    protected void setClassName(String className) { this.className = className; }
    public void updateClassName(String className)
            throws DTCInvalidDataException {
        this.updateAttribute("className", className);
    }
    
    /**
     * Returns the classpath required to run the server application.
     * The items in this path are relative to the root of the filesystem
     * in the {@link #getRequiredPkg required} zip archive.
     * 
     * @return classpath required to run the server application
     */
    public String getClassPath() { return classPath; }
    protected void setClassPath(String classPath) { this.classPath = classPath; }
    public void updateClassPath(String classPath)
            throws DTCInvalidDataException {
        this.updateAttribute("classPath", classPath);
    }
    
    /**
     * Returns a list of server application configurations that can be used
     * to run this server application.
     * 
     * @return list of runtime configurations for this server app
     */
    public List<ServerAppConfigDTO> getConfigs() { return configs; }
    protected void setConfigs(List<ServerAppConfigDTO> configs) { this.configs = configs; }
    public void updateConfigs(List<ServerAppConfigDTO> configs)
            throws DTCInvalidDataException {
        this.updateAttribute("configs", configs);
    }
    
    /**
     * Returns the package library required to run this server application.
     * It is assumed that this library is a zip archive.
     * 
     * @return the package library required to run this server application.
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
        this.checkNull("description");
        this.checkNull("className");
        this.checkNull("classPath");
        this.checkNull("configs");
        this.checkNull("requiredPkg");
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof ServerAppDTO) || o == null) return false;

        ServerAppDTO other = (ServerAppDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getName(), other.getName()) &&
                ObjectUtils.equals(this.getDescription(), other.getDescription()) &&
                ObjectUtils.equals(this.getClassName(), other.getClassName()) &&
                ObjectUtils.equals(this.getClassPath(), other.getClassPath()) &&
                ObjectUtils.equals(this.getConfigs(), other.getConfigs()) &&
                ObjectUtils.equals(this.getRequiredPkg(), other.getRequiredPkg());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashName = 31*hash + ObjectUtils.hashCode(this.getName());
        return hashId + hashName;
    }
}
