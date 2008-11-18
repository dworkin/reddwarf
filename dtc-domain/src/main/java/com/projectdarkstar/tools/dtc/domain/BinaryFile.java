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

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;
import javax.persistence.Lob;
import javax.persistence.Version;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a binary file
 */
@Entity
@Table(name = "BinaryFile")
public class BinaryFile implements Serializable
{
    private Long id;
    private Long versionNumber;
    private byte[] file;
    
    public BinaryFile() {}
    
    public BinaryFile(byte[] file)
    {
        this.setFile(file);
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
     * Returns the contents of the actual binary file as an
     * array of bytes.
     * 
     * @return the binary file
     */
    @Lob
    @Column(name = "file", nullable = false)
    public byte[] getFile() { return file; }
    public void setFile(byte[] file) { this.file = file; }

    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof BinaryFile) || o == null) return false;

        BinaryFile other = (BinaryFile)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getFile(), other.getFile());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        return hashId;
    }
}
