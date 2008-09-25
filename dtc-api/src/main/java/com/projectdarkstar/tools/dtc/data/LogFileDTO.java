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
import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a log file
 */
public class LogFileDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    private String log;
    
    public LogFileDTO(Long id,
                      Long versionNumber,
                      String log)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setLog(log);
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
    
    public String getLog() { return log; }
    protected void setLog(String log) { this.log = log; }
    public void updateLog(String log)
            throws DTCInvalidDataException {
        this.updateAttribute("log", log);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException
    {
        this.checkNull("log");
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof LogFileDTO) || o == null) return false;

        LogFileDTO other = (LogFileDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getLog(), other.getLog());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        return hashId;
    }
}
