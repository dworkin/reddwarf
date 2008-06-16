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

import java.io.Serializable;
import java.sql.Date;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Enumerated;
import javax.persistence.EnumType;
import javax.persistence.OneToOne;
import javax.persistence.JoinColumn;

/**
 * Wrapper object for a {@link TestExecution} that is currently running, or is
 * waiting to be run.  TestQueue objects are intended to be picked up by
 * an external execution daemon to run the specified {@link TestExecution}.
 * When the execution is complete, the TestQueue object is discarded and
 * removed from persistent storage.
 */
@Entity
@Table(name = "TestQueue")
public class TestQueue implements Serializable
{
    private Long id;
    private Date dateQueued;
    private Date dateStarted;
    private TestQueueStatus status;
    
    private TestExecution execution;
    private TestExecutionResult currentlyRunning;
    
    public TestQueue(Date dateQueued,
                     TestExecution execution)
    {
        this.dateQueued = dateQueued;
        this.execution = execution;
        
        this.status = TestQueueStatus.WAITING;
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
    @Column(name = "dateQueued", nullable = false)
    public Date getDateQueued() { return dateQueued; }
    public void setDateQueued(Date dateQueued) { this.dateQueued = dateQueued; }
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateStarted")
    public Date getDateStarted() { return dateStarted; }
    public void setDateStarted(Date dateStarted) { this.dateStarted = dateStarted; }
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public TestQueueStatus getStatus() { return status; }
    public void setStatus(TestQueueStatus status) { this.status = status; }
    
    @OneToOne
    @JoinColumn(name = "execution", nullable = false)
    public TestExecution getExecution () { return execution; }
    public void setExecution(TestExecution execution ) { this.execution = execution; }
    
    @OneToOne
    @JoinColumn(name = "currentlyRunning")
    public TestExecutionResult getCurrentlyRunning() { return currentlyRunning; }
    public void setCurrentlyRunning(TestExecutionResult currentlyRunning) { this.currentlyRunning = currentlyRunning; }
    
}
