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

/**
 * Represents a tag entity used to categorize {@link TestExecutionDTO}
 * objects.
 */
public class TestExecutionTagDTO extends AbstractDTO
{
    private Long id;
    private String tag;
    
    private List<TestExecutionDTO> executions;
    
    public TestExecutionTagDTO(String tag)
    {
        this.setTag(tag);
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
     * Returns the list of {@link TestExecutionDTO} objects that are tagged
     * with this tag.
     * 
     * @return test executions tagged with this tag.
     */
    public List<TestExecutionDTO> getExecutions() { return executions; }
    protected void setExecutions(List<TestExecutionDTO> executions) { this.executions = executions; }
    public void updateExecutions(List<TestExecutionDTO> executions)
            throws DTCInvalidDataException {
        this.updateAttribute("executions", executions);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
}
