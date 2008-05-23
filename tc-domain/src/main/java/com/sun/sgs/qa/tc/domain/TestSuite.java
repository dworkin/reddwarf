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

import java.util.List;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.ManyToMany;
import javax.persistence.JoinTable;
import javax.persistence.JoinColumn;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "TestSuite")
public class TestSuite implements Serializable
{
    private Long id;
    private String name;
    private String description;
    
    private List<TestSpec> testSpecs;
    
    public TestSuite(String name,
                     String description)
    {
        this.setName(name);
        this.setDescription(description);
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
    
    @ManyToMany
    @JoinTable(name = "testSuiteTestSpecs",
               joinColumns = @JoinColumn(name = "testSuiteId"),
               inverseJoinColumns = @JoinColumn(name = "testSpecId"))
    public List<TestSpec> getTestSpecs() { return testSpecs; }
    public void setTestSpecs(List<TestSpec> testSpecs) { this.testSpecs = testSpecs; }
}
