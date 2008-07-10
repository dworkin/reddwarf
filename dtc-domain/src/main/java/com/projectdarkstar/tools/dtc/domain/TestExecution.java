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
import java.sql.Date;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.ManyToOne;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.CascadeType;
import javax.persistence.OrderBy;
import javax.persistence.Version;

import org.apache.commons.lang.ObjectUtils;

/**
 * Represents an instance of an execution of a {@link TestSuite}.
 * A TestExecution also retains all of the configuration options,
 * parameters, and names of the {@link TestSuite} as it was
 * at the point in time that the TestExecution was created.  Additionally,
 * complete result information and log files for the executed test
 * are stored.
 */
@Entity
@Table(name = "TestExecution")
public class TestExecution implements Serializable
{
    private Long id;
    private Long versionNumber;
    private String name;
    private Date dateStarted;
    private Date dateFinished;
    
    private List<TestExecutionTag> tags;
    private List<TestExecutionResult> results;
    
    private PkgLibrary originalTestSuiteDarkstarPkg;
    private String originalTestSuiteName;
    private String originalTestSuiteDescription;
    private TestSuite originalTestSuite;

    public TestExecution() {}
    
    public TestExecution(String name,
                         TestSuite originalTestSuite)
    {
        this.setName(name);
        this.setDateStarted(null);
        this.setDateFinished(null);
        
        this.setOriginalTestSuiteDarkstarPkg(originalTestSuite.getDarkstarPkg());
        this.setOriginalTestSuiteName(originalTestSuite.getName());
        this.setOriginalTestSuiteDescription(originalTestSuite.getDescription());
        this.setOriginalTestSuite(originalTestSuite);
        
        this.setTags(new ArrayList<TestExecutionTag>());
        this.setResults(new ArrayList<TestExecutionResult>());
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
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateStarted", nullable = true)
    public Date getDateStarted() { return dateStarted; }
    public void setDateStarted(Date dateStarted) { this.dateStarted = dateStarted; }
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateFinished", nullable = true)
    public Date getDateFinished() { return dateFinished; } 
    public void setDateFinished(Date dateFinished) { this.dateFinished = dateFinished; }
    
    /**
     * Returns a list of {@link TestExecutionTag} objects that are used
     * to categorize test executions into groups.
     * 
     * @return list of tags for this test execution
     */
    @ManyToMany
    @OrderBy("tag")
    @JoinTable(name = "testExecutionTags",
               joinColumns = @JoinColumn(name = "testExecutionId"),
               inverseJoinColumns = @JoinColumn(name = "testExecutionTagId"))
    public List<TestExecutionTag> getTags() { return tags; }
    public void setTags(List<TestExecutionTag> tags) { this.tags = tags; }
    
    /**
     * Returns a list of {@link TestExecutionResult} objects that were executed
     * as part of this test execution.
     * 
     * @return list of results
     */
    @OneToMany(mappedBy = "parentExecution", cascade=CascadeType.REMOVE)
    public List<TestExecutionResult> getResults() { return results; }
    public void setResults(List<TestExecutionResult> results) { this.results = results; }
    
    /**
     * Returns the original {@link PkgLibrary} for the
     * {@link TestSuite#getDarkstarPkg darkstar package} required to run the
     * tests.  This attribute can be customized to run this test execution
     * against a different darkstar package if required.
     * 
     * @return original darkstar package required to run the tests
     */
    @ManyToOne
    @JoinColumn(name = "originalTestSuiteDarkstarPkg", nullable = false)
    public PkgLibrary getOriginalTestSuiteDarkstarPkg() { return originalTestSuiteDarkstarPkg; }
    public void setOriginalTestSuiteDarkstarPkg(PkgLibrary originalTestSuiteDarkstarPkg) { this.originalTestSuiteDarkstarPkg = originalTestSuiteDarkstarPkg; }
    
    @Column(name = "originalTestSuiteName", nullable = false)
    public String getOriginalTestSuiteName() { return originalTestSuiteName; }
    private void setOriginalTestSuiteName(String originalTestSuiteName) { this.originalTestSuiteName = originalTestSuiteName; }
    
    @Column(name = "originalTestSuiteDescription", nullable = false)
    public String getOriginalTestSuiteDescription() { return originalTestSuiteDescription; }
    private void setOriginalTestSuiteDescription(String originalTestSuiteDescription) { this.originalTestSuiteDescription = originalTestSuiteDescription; }
    
    /**
     * Returns the original {@link TestSuite} that this test execution
     * is based on.
     * 
     * @return original {@link TestSuite} used to create the test execution
     */
    @ManyToOne
    @JoinColumn(name = "originalTestSuite", nullable = false)
    public TestSuite getOriginalTestSuite() { return originalTestSuite; }
    public void setOriginalTestSuite(TestSuite originalTestSuite) { this.originalTestSuite = originalTestSuite; }
    
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof TestExecution) || o == null) return false;

        TestExecution other = (TestExecution)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getName(), other.getName()) &&
                ObjectUtils.equals(this.getDateStarted(), other.getDateStarted()) &&
                ObjectUtils.equals(this.getDateFinished(), other.getDateFinished()) &&
                ObjectUtils.equals(this.getTags(), other.getTags()) &&
                ObjectUtils.equals(this.getResults(), other.getResults()) &&
                ObjectUtils.equals(this.getOriginalTestSuiteDarkstarPkg(), other.getOriginalTestSuiteDarkstarPkg()) &&
                ObjectUtils.equals(this.getOriginalTestSuiteName(), other.getOriginalTestSuiteName()) &&
                ObjectUtils.equals(this.getOriginalTestSuiteDescription(), other.getOriginalTestSuiteDescription()) &&
                ObjectUtils.equals(this.getOriginalTestSuite(), other.getOriginalTestSuite());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashName = 31*hash + ObjectUtils.hashCode(this.getName());
        return hashId + hashName;
    }
    
}
