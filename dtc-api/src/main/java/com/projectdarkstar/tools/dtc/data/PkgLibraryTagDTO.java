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
 * Represents a tag entity used to categorize {@link PkgLibraryDTO}
 * objects.
 */
public class PkgLibraryTagDTO extends AbstractDTO
{
    private Long id;
    private String tag;
    
    private List<PkgLibraryDTO> libraries;
    
    public PkgLibraryTagDTO(Long id,
                            String tag)
    {
        this.setId(id);
        
        this.setTag(tag);
        
        this.setLibraries(new ArrayList<PkgLibraryDTO>());
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTag() { return tag; }
    protected void setTag(String tag) { this.tag = tag; }
    public void updateTag(String tag)
            throws DTCInvalidDataException {
        this.updateAttribute("tag", tag);
    }
    
    /**
     * Returns the list of {@link PkgLibraryDTO} objects that are tagged
     * with this tag.
     * 
     * @return libraries tagged with this tag.
     */
    public List<PkgLibraryDTO> getLibraries() { return libraries; }
    protected void setLibraries(List<PkgLibraryDTO> libraries) { this.libraries = libraries; }
    public void updateLibraries(List<PkgLibraryDTO> libraries)
            throws DTCInvalidDataException {
        this.updateAttribute("libraries", libraries);
    }
    
    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof PkgLibraryTagDTO) || o == null) return false;

        PkgLibraryTagDTO other = (PkgLibraryTagDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getTag(), other.getTag()) &&
                ObjectUtils.equals(this.getLibraries(), other.getLibraries());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashTag = 31*hash + ObjectUtils.hashCode(this.getTag());
        return hashId + hashTag;
    }
}
