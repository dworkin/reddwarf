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
import java.sql.Date;
import java.util.List;
import java.util.ArrayList;
import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a physical hardware resource that can be used during
 * a DTC test.
 */
public class HardwareResourceDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    private String hostname;
    private String lockedBy;
    private Date lockedAt;
    private Boolean enabled;
    
    private List<HardwareResourceFamilyDTO> families;

    public HardwareResourceDTO(Long id,
                               Long versionNumber,
                               String hostname,
                               String lockedBy,
                               Date lockedAt,
                               Boolean enabled)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setHostname(hostname);
        this.setLockedBy(lockedBy);
        this.setLockedAt(lockedAt);
        this.setEnabled(enabled);
        
        this.setFamilies(new ArrayList<HardwareResourceFamilyDTO>());
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
    
    /**
     * Returns the hostname of the resource
     * 
     * @return hostname of the resource
     */
    public String getHostname() { return hostname; }
    protected void setHostname(String hostname) { this.hostname = hostname; }
    public void updateHostname(String hostname)
            throws DTCInvalidDataException {
        this.updateAttribute("hostname", hostname);
    }

    /**
     * Returns the identifier of the entity which currently has a lock
     * on this resource.  If the resource is not locked, returns null.
     * 
     * @return the identifier of the locking entity
     */
    public String getLockedBy() { return lockedBy; }
    protected void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
    public void updateLockedBy(String lockedBy)
            throws DTCInvalidDataException {
        this.updateAttribute("lockedBy", lockedBy);
    }
    
    /**
     * Returns a {@link Date} object representing the time that this resource
     * was locked.  If the resource is not locked, returns null.
     * 
     * @return the time that this resource was locked
     */
    public Date getLockedAt() { return lockedAt; }
    protected void setLockedAt(Date lockedAt) { this.lockedAt = lockedAt; }
    public void updateLockedAt(Date lockedAt)
            throws DTCInvalidDataException {
        this.updateAttribute("lockedAt", lockedAt);
    }
    
    /**
     * Returns true if this resource is available for use during tests.
     * If it is not enabled, no tests may acquire a lock on it.
     * 
     * @return whether or not this resource is enabled for testing
     */
    public Boolean getEnabled() { return enabled; }
    protected void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public void updateEnabled(Boolean enabled)
            throws DTCInvalidDataException {
        this.updateAttribute("enabled", enabled);
    }
    
    /**
     * Returns a list of {@link HardwareResourceFamilyDTO} objects that
     * represents the set of families that this resource is a member of.
     * 
     * @return list of families for this resource
     */
    public List<HardwareResourceFamilyDTO> getFamilies() { return families; }
    protected void setFamilies(List<HardwareResourceFamilyDTO> families) { this.families = families; }
    public void updateFamilies(List<HardwareResourceFamilyDTO> families)
            throws DTCInvalidDataException {
        this.updateAttribute("families", families);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException
    {
        this.checkBlank("hostname");
        this.checkNull("enabled");
        this.checkNull("families");
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof HardwareResourceDTO) || o == null) return false;

        HardwareResourceDTO other = (HardwareResourceDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getHostname(), other.getHostname()) &&
                ObjectUtils.equals(this.getLockedBy(), other.getLockedBy()) &&
                ObjectUtils.equals(this.getLockedAt(), other.getLockedAt()) &&
                ObjectUtils.equals(this.getEnabled(), other.getEnabled()) &&
                ObjectUtils.equals(this.getFamilies(), other.getFamilies());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashHostname = 31*hash + ObjectUtils.hashCode(this.getHostname());
        return hashId + hashHostname;
    }
}
