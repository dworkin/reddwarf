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

/**
 * Represents a snapshot of the data collected by a specific {@link SystemProbeDTO}
 * at a specific point in time.
 */
public class TestExecutionResultProbeDataDTO extends AbstractDTO
{
    private Long id;
    private Date timestamp;
    private Long value;
    
    private TestExecutionResultProbeLogDTO parentProbe;
    
    public TestExecutionResultProbeDataDTO(Long id,
                                           Date timestamp,
                                           Long value)
    {
        this.setId(id);
        
        this.setTimestamp(timestamp);
        this.setValue(value);
        
        this.setParentProbe(null);
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Date getTimestamp() { return timestamp; }
    protected void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public void updateTimestamp(Date timestamp)
            throws DTCInvalidDataException {
        this.updateAttribute("timestamp", timestamp);
    }
    
    public Long getValue() { return value; }
    protected void setValue(Long value) { this.value = value; }
    public void updateValue(Long value)
            throws DTCInvalidDataException {
        this.updateAttribute("value", value);
    }
    
    public TestExecutionResultProbeLogDTO getParentProbe() { return parentProbe; }
    protected void setParentProbe(TestExecutionResultProbeLogDTO parentProbe) { this.parentProbe = parentProbe; }
    public void updateParentProbe(TestExecutionResultProbeLogDTO parentProbe)
            throws DTCInvalidDataException {
        this.updateAttribute("parentProbe", parentProbe);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
}
