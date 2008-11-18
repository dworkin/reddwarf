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

import java.util.List;
import java.util.ArrayList;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.ManyToMany;
import javax.persistence.OrderBy;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a tag entity used to categorize {@link TestExecution}
 * objects.
 */
@Entity
@Table(name = "TestExecutionTag")
public class TestExecutionTag implements Serializable
{
    private Long id;
    private String tag;
    
    private List<TestExecution> executions;
    
    public TestExecutionTag() {}
    
    public TestExecutionTag(String tag)
    {
        this.setTag(tag);
        this.setExecutions(new ArrayList<TestExecution>());
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Column(name = "tag", nullable = false, unique = true)
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    
    /**
     * Returns the list of {@link TestExecution} objects that are tagged
     * with this tag.
     * 
     * @return test executions tagged with this tag.
     */
    @ManyToMany(mappedBy = "tags")
    @OrderBy("dateFinished")
    public List<TestExecution> getExecutions() { return executions; }
    public void setExecutions(List<TestExecution> executions) { this.executions = executions; }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof TestExecutionTag) || o == null) return false;

        TestExecutionTag other = (TestExecutionTag)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getTag(), other.getTag()) &&
                ObjectUtils.equals(this.getExecutions(), other.getExecutions());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashTag = 31*hash + ObjectUtils.hashCode(this.getTag());
        return hashId + hashTag;
    }
}
