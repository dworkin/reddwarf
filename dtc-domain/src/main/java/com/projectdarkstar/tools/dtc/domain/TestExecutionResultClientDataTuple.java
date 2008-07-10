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

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents a snapshot of the number of clients in the system at runtime
 * at a specific point in time during the execution for a specific type
 * of {@link ClientAppConfig}.
 */
@Entity
@Table(name = "TestExecutionResultClientDataTuple")
public class TestExecutionResultClientDataTuple implements Serializable
{
    private Long id;
    private String originalClientName;
    private Long numClients;
    private ClientAppConfig client;
    
    private TestExecutionResultClientData clientData;
    
    public TestExecutionResultClientDataTuple() {}
    
    public TestExecutionResultClientDataTuple(String originalClientName,
                                              Long numClients,
                                              ClientAppConfig client,
                                              TestExecutionResultClientData clientData)
    {
        this.setOriginalClientName(originalClientName);
        this.setNumClients(numClients);
        this.setClient(client);
        this.setClientData(clientData);
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
    
    @Column(name = "originalClientName", nullable = false)
    public String getOriginalClientName() { return originalClientName; }
    public void setOriginalClientName(String originalClientName) { this.originalClientName = originalClientName; }
    
    @Column(name = "numClients", nullable = false)
    public Long getNumClients() { return numClients; }
    public void setNumClients(Long numClients) { this.numClients = numClients; }
    
    @ManyToOne
    @JoinColumn(name = "client", nullable = false)
    public ClientAppConfig getClient() { return client; }
    public void setClient(ClientAppConfig client) { this.client = client; }
    
    @ManyToOne
    @JoinColumn(name = "clientData", nullable = false)
    public TestExecutionResultClientData getClientData() { return clientData; }
    public void setClientData(TestExecutionResultClientData clientData) { this.clientData = clientData; }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof TestExecutionResultClientDataTuple) || o == null) return false;

        TestExecutionResultClientDataTuple other = (TestExecutionResultClientDataTuple)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getOriginalClientName(), other.getOriginalClientName()) &&
                ObjectUtils.equals(this.getNumClients(), other.getNumClients()) &&
                ObjectUtils.equals(this.getClient(), other.getClient()) &&
                ObjectUtils.equals(this.getClientData(), other.getClientData());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashOriginalClientName = 31*hash + ObjectUtils.hashCode(this.getOriginalClientName());
        return hashId + hashOriginalClientName;
    }
    
}
