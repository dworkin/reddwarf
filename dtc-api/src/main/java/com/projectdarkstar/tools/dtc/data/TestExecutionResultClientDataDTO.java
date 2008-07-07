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
import java.sql.Date;

/**
 * Represents a snapshot of the number of clients in the system at runtime
 * at a specific point in time during the execution.
 */
public class TestExecutionResultClientDataDTO extends AbstractDTO
{
    private Long id;
    private Date timestamp;
    private List<TestExecutionResultClientDataTupleDTO> values;
    
    private TestExecutionResultDTO parentResult;
    
    public TestExecutionResultClientDataDTO(Date timestamp)
    {
        this.setTimestamp(timestamp);
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
    
    public List<TestExecutionResultClientDataTupleDTO> getValues() { return values; }
    protected void setValues(List<TestExecutionResultClientDataTupleDTO> values) { this.values = values; }
    public void updateValues(List<TestExecutionResultClientDataTupleDTO> values)
            throws DTCInvalidDataException {
        this.updateAttribute("values", values);
    }
    
    public TestExecutionResultDTO getParentResult() { return parentResult; }
    protected void setParentResult(TestExecutionResultDTO parentResult) { this.parentResult = parentResult; }
    public void updateParentResult(TestExecutionResultDTO parentResult)
            throws DTCInvalidDataException {
        this.updateAttribute("parentResult", parentResult);
    }
    
    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
}
