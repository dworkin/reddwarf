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

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a snapshot of the number of clients in the system at runtime
 * at a specific point in time during the execution.
 */
@Entity
@Table(name = "TestExecutionResultClientData")
public class TestExecutionResultClientData implements Serializable
{
    private Long id;
    private Date timestamp;
    private List<TestExecutionResultClientDataTuple> values;
    
    private TestExecutionResult parentResult;
    
    public TestExecutionResultClientData() {}
    
    public TestExecutionResultClientData(Date timestamp,
                                         TestExecutionResult parentResult)
    {
        this.setTimestamp(timestamp);
        this.setParentResult(parentResult);
        
        this.setValues(new ArrayList<TestExecutionResultClientDataTuple>());
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
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "timestamp", nullable = false)
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    
    @OneToMany(mappedBy = "clientData")
    @OrderBy("originalClientName")
    public List<TestExecutionResultClientDataTuple> getValues() { return values; }
    public void setValues(List<TestExecutionResultClientDataTuple> values) { this.values = values; }
    
    @ManyToOne
    @JoinColumn(name = "parentResult", nullable = false)
    public TestExecutionResult getParentResult() { return parentResult; }
    public void setParentResult(TestExecutionResult parentResult) { this.parentResult = parentResult; }
    
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof TestExecutionResultClientData) || o == null) return false;

        TestExecutionResultClientData other = (TestExecutionResultClientData)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getTimestamp(), other.getTimestamp()) &&
                ObjectUtils.equals(this.getValues(), other.getValues()) &&
                ObjectUtils.equals(this.getParentResult(), other.getParentResult());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashTimestamp = 31*hash + ObjectUtils.hashCode(this.getTimestamp());
        return hashId + hashTimestamp;
    }

}
