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
import org.apache.commons.lang.ObjectUtils;

/**
 * Wrapper object for a {@link TestExecutionDTO} that is currently running, or is
 * waiting to be run.  TestQueue objects are intended to be picked up by
 * an external execution daemon to run the specified {@link TestExecutionDTO}.
 * When the execution is complete, the TestQueue object is discarded and
 * removed from persistent storage.
 */
public class TestQueueDTO extends AbstractDTO
{
    private Long id;
    private Date dateQueued;
    private Date dateStarted;
    private TestQueueStatusDTO status;
    
    private TestExecutionDTO execution;
    private TestExecutionResultDTO currentlyRunning;
    
    public TestQueueDTO(Long id,
                        Date dateQueued,
                        Date dateStarted,
                        TestQueueStatusDTO status)
    {
        this.setId(id);
        
        this.setDateQueued(dateQueued);
        this.setDateStarted(dateStarted);
        this.setStatus(status);
        
        this.setExecution(null);
        this.setCurrentlyRunning(null);
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Date getDateQueued() { return dateQueued; }
    protected void setDateQueued(Date dateQueued) { this.dateQueued = dateQueued; }
    public void updateDateQueued(Date dateQueued)
            throws DTCInvalidDataException {
        this.updateAttribute("dateQueued", dateQueued);
    }
    
    public Date getDateStarted() { return dateStarted; }
    protected void setDateStarted(Date dateStarted) { this.dateStarted = dateStarted; }
    public void updateDateStarted(Date dateStarted)
            throws DTCInvalidDataException {
        this.updateAttribute("dateStarted", dateStarted);
    }
    
    public TestQueueStatusDTO getStatus() { return status; }
    protected void setStatus(TestQueueStatusDTO status) { this.status = status; }
    public void updateStatus(TestQueueStatusDTO status)
            throws DTCInvalidDataException {
        this.updateAttribute("status", status);
    }
    
    public TestExecutionDTO getExecution () { return execution; }
    protected void setExecution(TestExecutionDTO execution ) { this.execution = execution; }
    public void updateExecution(TestExecutionDTO execution )
            throws DTCInvalidDataException {
        this.updateAttribute("execution", execution);
    }
    
    public TestExecutionResultDTO getCurrentlyRunning() { return currentlyRunning; }
    protected void setCurrentlyRunning(TestExecutionResultDTO currentlyRunning) { this.currentlyRunning = currentlyRunning; }
    public void updateCurrentlyRunning(TestExecutionResultDTO currentlyRunning)
            throws DTCInvalidDataException {
        this.updateAttribute("currentlyRunning", currentlyRunning);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof TestQueueDTO) || o == null) return false;

        TestQueueDTO other = (TestQueueDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getDateQueued(), other.getDateQueued()) &&
                ObjectUtils.equals(this.getDateStarted(), other.getDateStarted()) &&
                ObjectUtils.equals(this.getStatus(), other.getStatus()) &&
                ObjectUtils.equals(this.getExecution(), other.getExecution()) &&
                ObjectUtils.equals(this.getCurrentlyRunning(), other.getCurrentlyRunning());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        return hashId;
    }
}
