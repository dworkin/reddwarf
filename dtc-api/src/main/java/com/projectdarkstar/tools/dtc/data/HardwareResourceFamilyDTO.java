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
import java.util.ArrayList;
import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a family of resources.  Each resource in the family should
 * have a common set of attributes.
 */
public class HardwareResourceFamilyDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String description;
    private String system;
    private String os;
    private String memory;
    
    private List<HardwareResourceDTO> members;
    
    public HardwareResourceFamilyDTO(Long id,
                                     Long versionNumber,
                                     String name,
                                     String description,
                                     String system,
                                     String os,
                                     String memory)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setName(name);
        this.setDescription(description);
        this.setSystem(system);
        this.setOs(os);
        this.setMemory(memory);
        
        this.setMembers(new ArrayList<HardwareResourceDTO>());
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

    public String getSystem() { return system; }
    protected void setSystem(String system) { this.system = system; }
    public void updateSystem(String system)
            throws DTCInvalidDataException {
        this.updateAttribute("system", system);
    }
    
    public String getMemory() { return memory; }
    protected void setMemory(String memory) { this.memory = memory; }
    public void updateMemory(String memory)
            throws DTCInvalidDataException {
        this.updateAttribute("memory", memory);
    }
    
    public String getOs() { return os; }
    protected void setOs(String os) { this.os = os; }
    public void updateOs(String os)
            throws DTCInvalidDataException {
        this.updateAttribute("os", os);
    }
    
    
    public List<HardwareResourceDTO> getMembers() { return members; }
    protected void setMembers(List<HardwareResourceDTO> members) { this.members = members; }
    public void updateMembers(List<HardwareResourceDTO> members)
            throws DTCInvalidDataException {
        this.updateAttribute("members", members);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
    
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof HardwareResourceFamilyDTO) || o == null) return false;

        HardwareResourceFamilyDTO other = (HardwareResourceFamilyDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getName(), other.getName()) &&
                ObjectUtils.equals(this.getDescription(), other.getDescription()) &&
                ObjectUtils.equals(this.getSystem(), other.getSystem()) &&
                ObjectUtils.equals(this.getOs(), other.getOs()) &&
                ObjectUtils.equals(this.getMemory(), other.getMemory());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashName = 31*hash + ObjectUtils.hashCode(this.getName());
        return hashId + hashName;
    }
}
