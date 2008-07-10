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
 * Represents a binary package library which is required for either
 * a {@link ClientAppDTO}, {@link ServerAppDTO}, or {@link SystemProbeDTO} to
 * execute.
 */
public class PkgLibraryDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    
    private String name;
    private byte[] file;
    
    private List<PkgLibraryTagDTO> tags;
    
    public PkgLibraryDTO(Long id,
                         Long versionNumber,
                         String name,
                         byte[] file)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setName(name);
        this.setFile(file);
        
        this.setTags(new ArrayList<PkgLibraryTagDTO>());
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
     * Returns the contents of the actual package library file as an
     * array of bytes.
     * 
     * @return the package library file
     */
    public byte[] getFile() { return file; }
    protected void setFile(byte[] file) { this.file = file; }
    public void updateFile(byte[] file)
            throws DTCInvalidDataException {
        this.updateAttribute("file", file);
    }
    
    /**
     * Returns a list of {@link PkgLibraryTagDTO} objects that are used
     * to categorize libraries into groups.
     * 
     * @return list of tags
     */
    public List<PkgLibraryTagDTO> getTags() { return tags; }
    protected void setTags(List<PkgLibraryTagDTO> tags) { this.tags = tags; }
    public void updateTags(List<PkgLibraryTagDTO> tags)
            throws DTCInvalidDataException {
        this.updateAttribute("tags", tags);
    }
    
    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException
    {
        this.checkBlank("name");
        this.checkNull("file");
        this.checkNull("tags");
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof PkgLibraryDTO) || o == null) return false;

        PkgLibraryDTO other = (PkgLibraryDTO)o;
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
