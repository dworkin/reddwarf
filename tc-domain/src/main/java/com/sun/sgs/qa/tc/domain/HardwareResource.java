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

package com.sun.sgs.qa.tc.domain;

import java.sql.Date;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;


/**
 * 
 * @author Owen Kellett
 */
@Entity
@Table(name="HardwareResource")
public class HardwareResource implements Serializable
{
    private Long id;
    private String hostname;
    private String lockedBy;
    private Date lockedAt;
    
    private HardwareResourceFamily family;

    public HardwareResource() {}
    
    public HardwareResource(String hostname,
                            String lockedBy,
                            Date lockedAt,
                            HardwareResourceFamily family)
    {
        this.setHostname(hostname);
        this.setLockedBy(lockedBy);
        this.setLockedAt(lockedAt);
        this.setFamily(family);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Column(name="hostname", nullable=false)
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    @Column(name="lockedBy", nullable=true)
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
    
    @Column(name="lockedAt", nullable=true)
    public Date getLockedAt() { return lockedAt; }
    public void setLockedAt(Date lockedAt) { this.lockedAt = lockedAt; }
    
    @ManyToOne
    @JoinColumn(name="family")
    public HardwareResourceFamily getFamily() { return family; }
    public void setFamily(HardwareResourceFamily family) { this.family = family; }
}
