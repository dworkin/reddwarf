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
import javax.persistence.JoinTable;
import javax.persistence.JoinColumn;
import javax.persistence.Version;
import javax.persistence.ManyToOne;

/**
 * Represents a collection of tests in the form of @link{TestSpec} objects
 * that are to be run in succession as a suite.
 */
@Entity
@Table(name = "TestSuite")
public class TestSuite implements Serializable
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String description;
    
    private PkgLibrary darkstarPkg;
    private List<TestSpec> testSpecs;
    
    public TestSuite() {}
    
    public TestSuite(String name,
                     String description,
                     PkgLibrary darkstarPkg)
    {
        this.setName(name);
        this.setDescription(description);
        this.setDarkstarPkg(darkstarPkg);
        
        this.setTestSpecs(new ArrayList<TestSpec>());
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
    
    /**
     * Returns the version number in the data store that this entity represents.
     * Whenever an update to an object is pushed to the persistent data
     * store, the version number is incremented.
     * 
     * @return version number of the entity
     */
    @Version
    @Column(name = "versionNumber")
    public Long getVersionNumber() { return versionNumber; }
    protected void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    
    @Column(name = "name", nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Column(name = "description", nullable = false, length = 1024)
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    /**
     * Returns the {@link PkgLibrary} object that represents the binary
     * darkstar package that is to be used in this test suite.
     * 
     * @return darkstar package library
     */
    @ManyToOne
    @JoinColumn(name = "darkstarPkg", nullable = false)
    public PkgLibrary getDarkstarPkg() { return darkstarPkg; }
    public void setDarkstarPkg(PkgLibrary darkstarPkg) { this.darkstarPkg = darkstarPkg; }
    
    /**
     * Returns the list of {@link TestSpec} objects that are to be run
     * in succession 
     * 
     * @return list of tests
     */
    @ManyToMany
    @JoinTable(name = "testSuiteTestSpecs",
               joinColumns = @JoinColumn(name = "testSuiteId"),
               inverseJoinColumns = @JoinColumn(name = "testSpecId"))
    public List<TestSpec> getTestSpecs() { return testSpecs; }
    public void setTestSpecs(List<TestSpec> testSpecs) { this.testSpecs = testSpecs; }
}
