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

import java.util.SortedSet;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.ManyToMany;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "HardwareResourceFamily")
public class HardwareResourceFamily implements Serializable
{
    private Long id;
    private String name;
    private String description;
    private String system;
    private String os;
    private String memory;
    
    private SortedSet<HardwareResource> members;
    
    public HardwareResourceFamily(String name,
                                  String description,
                                  String system,
                                  String os,
                                  String memory)
    {
        this.setName(name);
        this.setDescription(description);
        this.setSystem(system);
        this.setOs(os);
        this.setMemory(memory);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Column(name = "name", nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Column(name = "description", nullable = false, length = 1024)
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Column(name = "system", nullable = false)
    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }
    
    @Column(name = "memory", nullable = false)
    public String getMemory() { return memory; }
    public void setMemory(String memory) { this.memory = memory; }
    
    @Column(name = "os", nullable = false)
    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }
    
    @ManyToMany(mappedBy="families")
    public SortedSet<HardwareResource> getMembers() { return members; }
    public void setMembers(SortedSet<HardwareResource> members) { this.members = members; }
    
    
}
