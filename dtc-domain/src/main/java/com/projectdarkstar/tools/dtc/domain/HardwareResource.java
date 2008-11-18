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

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;
import javax.persistence.JoinColumn;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.ManyToMany;
import javax.persistence.JoinTable;
import javax.persistence.OrderBy;
import javax.persistence.Version;

import org.apache.commons.lang.ObjectUtils;


/**
 * Represents a physical hardware resource that can be used during
 * a DTC test.
 */
@Entity
@Table(name="HardwareResource")
public class HardwareResource implements Serializable
{
    private Long id;
    private Long versionNumber;
    private String hostname;
    private String lockedBy;
    private Date lockedAt;
    private Boolean enabled;
    
    private List<HardwareResourceFamily> families;

    public HardwareResource() {}
    
    public HardwareResource(String hostname,
                            String lockedBy,
                            Date lockedAt,
                            Boolean enabled)
    {
        this.setHostname(hostname);
        this.setLockedBy(lockedBy);
        this.setLockedAt(lockedAt);
        this.setEnabled(enabled);
        
        this.setFamilies(new ArrayList<HardwareResourceFamily>());
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
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
    public void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    
    /**
     * Returns the hostname of the resource
     * 
     * @return hostname of the resource
     */
    @Column(name="hostname", nullable=false)
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    /**
     * Returns the identifier of the entity which currently has a lock
     * on this resource.  If the resource is not locked, returns null.
     * 
     * @return the identifier of the locking entity
     */
    @Column(name="lockedBy", nullable=true)
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
    
    /**
     * Returns a {@link Date} object representing the time that this resource
     * was locked.  If the resource is not locked, returns null.
     * 
     * @return the time that this resource was locked
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="lockedAt", nullable=true)
    public Date getLockedAt() { return lockedAt; }
    public void setLockedAt(Date lockedAt) { this.lockedAt = lockedAt; }
    
    /**
     * Returns true if this resource is available for use during tests.
     * If it is not enabled, no tests may acquire a lock on it.
     * 
     * @return whether or not this resource is enabled for testing
     */
    @Column(name = "enabled", nullable = false)
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    /**
     * Returns a list of {@link HardwareResourceFamily} objects that
     * represents the set of families that this resource is a member of.
     * 
     * @return list of families for this resource
     */
    @ManyToMany
    @OrderBy("name")
    @JoinTable(name = "hardwareResourceFamilies",
               joinColumns = @JoinColumn(name = "hardwareResourceId"),
               inverseJoinColumns = @JoinColumn(name = "hardwareResourceFamilyId"))
    public List<HardwareResourceFamily> getFamilies() { return families; }
    public void setFamilies(List<HardwareResourceFamily> families) { this.families = families; }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof HardwareResource) || o == null) return false;

        HardwareResource other = (HardwareResource)o;
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
