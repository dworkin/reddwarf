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

import java.sql.Date;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a snapshot of the data collected by a specific {@link SystemProbe}
 * at a specific point in time.
 */
@Entity
@Table(name = "TestExecutionResultProbeData")
public class TestExecutionResultProbeData implements Serializable
{
    private Long id;
    private Date timestamp;
    private Long value;
    
    private TestExecutionResultProbeLog parentProbe;
    
    public TestExecutionResultProbeData() {}
    
    public TestExecutionResultProbeData(Date timestamp,
                                        Long value,
                                        TestExecutionResultProbeLog parentProbe)
    {
        this.setTimestamp(timestamp);
        this.setValue(value);
        
        this.setParentProbe(parentProbe);
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
    
    @Column(name = "value", nullable = false)
    public Long getValue() { return value; }
    public void setValue(Long value) { this.value = value; }
    
    @ManyToOne
    @JoinColumn(name = "parentProbe", nullable = false)
    public TestExecutionResultProbeLog getParentProbe() { return parentProbe; }
    public void setParentProbe(TestExecutionResultProbeLog parentProbe) { this.parentProbe = parentProbe; }
    
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof TestExecutionResultProbeData) || o == null) return false;

        TestExecutionResultProbeData other = (TestExecutionResultProbeData)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getTimestamp(), other.getTimestamp()) &&
                ObjectUtils.equals(this.getValue(), other.getValue()) &&
                ObjectUtils.equals(this.getParentProbe(), other.getParentProbe());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashTimestamp = 31*hash + ObjectUtils.hashCode(this.getTimestamp());
        return hashId + hashTimestamp;
    }
}
