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

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;
import javax.persistence.Version;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OrderBy;
import javax.persistence.JoinTable;
import javax.persistence.JoinColumn;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a binary package library which is required for either
 * a {@link ClientApp}, {@link ServerApp}, or {@link SystemProbe} to
 * execute.
 */
@Entity
@Table(name = "PkgLibrary")
public class PkgLibrary implements Serializable
{
    private Long id;
    private Long versionNumber;
    
    private String name;
    private BinaryFile file;
    
    private List<PkgLibraryTag> tags;
    
    public PkgLibrary() {}
    
    public PkgLibrary(String name,
                      BinaryFile file)
    {
        this.setName(name);
        this.setFile(file);
        
        this.setTags(new ArrayList<PkgLibraryTag>());
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
    
    @Column(name = "name", nullable = false, unique = true)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    /**
     * Returns the contents of the actual package library file as a
     * {@link BinaryFile}
     * 
     * @return the package library file
     */
    @ManyToOne
    @JoinColumn(name = "file", nullable = false)
    public BinaryFile getFile() { return file; }
    public void setFile(BinaryFile file) { this.file = file; }
    
    /**
     * Returns a list of {@link PkgLibraryTag} objects that are used
     * to categorize libraries into groups.
     * 
     * @return list of tags
     */
    @ManyToMany
    @OrderBy("tag")
    @JoinTable(name = "pkgLibraryTags",
               joinColumns = @JoinColumn(name = "pkgLibraryId"),
               inverseJoinColumns = @JoinColumn(name = "pkgLibraryTagId"))
    public List<PkgLibraryTag> getTags() { return tags; }
    public void setTags(List<PkgLibraryTag> tags) { this.tags = tags; }
    
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof PkgLibrary) || o == null) return false;

        PkgLibrary other = (PkgLibrary)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getName(), other.getName()) &&
                ObjectUtils.equals(this.getFile(), other.getFile()) &&
                ObjectUtils.equals(this.getTags(), other.getTags());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashName = 31*hash + ObjectUtils.hashCode(this.getName());
        return hashId + hashName;
    }
}
