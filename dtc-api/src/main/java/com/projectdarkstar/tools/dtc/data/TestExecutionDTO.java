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

package com.projectdarkstar.tools.dtc.data;

import com.projectdarkstar.tools.dtc.exceptions.DTCInvalidDataException;
import java.util.List;
import java.util.ArrayList;
import java.sql.Date;
import org.apache.commons.lang.ObjectUtils;

/**
 * Represents an instance of an execution of a {@link TestSuiteDTO}.
 * A TestExecution also retains all of the configuration options,
 * parameters, and names of the {@link TestSuiteDTO} as it was
 * at the point in time that the TestExecution was created.  Additionally,
 * complete result information and log files for the executed test
 * are stored.
 */
public class TestExecutionDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    private String name;
    private Date dateStarted;
    private Date dateFinished;
    
    private List<TestExecutionTagDTO> tags;
    private List<TestExecutionResultDTO> results;
    
    private PkgLibraryDTO originalTestSuiteDarkstarPkg;
    private String originalTestSuiteName;
    private String originalTestSuiteDescription;
    private TestSuiteDTO originalTestSuite;

    
    public TestExecutionDTO(Long id,
                            Long versionNumber,
                            String name,
                            Date dateStarted,
                            Date dateFinished,
                            String originalTestSuiteName,
                            String originalTestSuiteDescription)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setName(name);
        this.setDateStarted(dateStarted);
        this.setDateFinished(dateFinished);
        
        this.setOriginalTestSuiteDarkstarPkg(null);
        this.setOriginalTestSuiteName(originalTestSuiteName);
        this.setOriginalTestSuiteDescription(originalTestSuiteDescription);
        this.setOriginalTestSuite(null);
        
        this.setTags(new ArrayList<TestExecutionTagDTO>());
        this.setResults(new ArrayList<TestExecutionResultDTO>());
    }
    
    /**
     * Returns the id of the entity in persistent storage
     * 
     * @return id of the entity
     */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    /**
     * Returns the version number in the data store that this entity represents.
     * Whenever an update to an object is pushed to the persistent data
     * store, the version number is incremented.
     * 
     * @return version number of the entity
     */
    public Long getVersionNumber() { return versionNumber; }
    private void setVersionNumber(Long versionNumber) { this.versionNumber = versionNumber; }
    
    public String getName() { return name; }
    protected void setName(String name) { this.name = name; }
    public void updateName(String name)
            throws DTCInvalidDataException {
        this.updateAttribute("name", name);
    }
    
    public Date getDateStarted() { return dateStarted; }
    protected void setDateStarted(Date dateStarted) { this.dateStarted = dateStarted; }
    public void updateDateStarted(Date dateStarted)
            throws DTCInvalidDataException {
        this.updateAttribute("dateStarted", dateStarted);
    }
    
    public Date getDateFinished() { return dateFinished; } 
    protected void setDateFinished(Date dateFinished) { this.dateFinished = dateFinished; }
    public void updateDateFinished(Date dateFinished)
            throws DTCInvalidDataException {
        this.updateAttribute("dateFinished", dateFinished);
    }
    
    /**
     * Returns a list of {@link TestExecutionTagDTO} objects that are used
     * to categorize test executions into groups.
     * 
     * @return list of tags for this test execution
     */
    public List<TestExecutionTagDTO> getTags() { return tags; }
    protected void setTags(List<TestExecutionTagDTO> tags) { this.tags = tags; }
    
    /**
     * Returns a list of {@link TestExecutionResultDTO} objects that were executed
     * as part of this test execution.
     * 
     * @return list of results
     */
    public List<TestExecutionResultDTO> getResults() { return results; }
    protected void setResults(List<TestExecutionResultDTO> results) { this.results = results; }
    public void updateResults(List<TestExecutionResultDTO> results)
            throws DTCInvalidDataException {
        this.updateAttribute("results", results);
    }
    
    /**
     * Returns the original {@link PkgLibraryDTO} for the
     * {@link TestSuiteDTO#getDarkstarPkg darkstar package} required to run the
     * tests.  This attribute can be customized to run this test execution
     * against a different darkstar package if required.
     * 
     * @return original darkstar package required to run the tests
     */
    public PkgLibraryDTO getOriginalTestSuiteDarkstarPkg() { return originalTestSuiteDarkstarPkg; }
    protected void setOriginalTestSuiteDarkstarPkg(PkgLibraryDTO originalTestSuiteDarkstarPkg) { this.originalTestSuiteDarkstarPkg = originalTestSuiteDarkstarPkg; }
    public void updateOriginalTestSuiteDarkstarPkg(PkgLibraryDTO originalTestSuiteDarkstarPkg)
            throws DTCInvalidDataException {
        this.updateAttribute("originalTestSuiteDarkstarPkg", originalTestSuiteDarkstarPkg);
    }
    
    public String getOriginalTestSuiteName() { return originalTestSuiteName; }
    private void setOriginalTestSuiteName(String originalTestSuiteName) { this.originalTestSuiteName = originalTestSuiteName; }
    
    public String getOriginalTestSuiteDescription() { return originalTestSuiteDescription; }
    private void setOriginalTestSuiteDescription(String originalTestSuiteDescription) { this.originalTestSuiteDescription = originalTestSuiteDescription; }
    
    /**
     * Returns the original {@link TestSuiteDTO} that this test execution
     * is based on.
     * 
     * @return original {@link TestSuiteDTO} used to create the test execution
     */
    public TestSuiteDTO getOriginalTestSuite() { return originalTestSuite; }
    private void setOriginalTestSuite(TestSuiteDTO originalTestSuite) { this.originalTestSuite = originalTestSuite; }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException 
    {
        this.checkBlank("name");
        this.checkNull("tags");
        this.checkNull("results");
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof TestExecutionDTO) || o == null) return false;

        TestExecutionDTO other = (TestExecutionDTO)o;
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
