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

import com.projectdarkstar.tools.dtc.service.DTCInvalidDataException;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a collection of tests in the form of @link{TestSpecDTO} objects
 * that are to be run in succession as a suite.
 */
public class TestSuiteDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    private String name;
    private String description;
    
    private PkgLibraryDTO darkstarPkg;
    private List<TestSpecDTO> testSpecs;
    
    public TestSuiteDTO(Long id,
                        Long versionNumber,
                        String name,
                        String description)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setName(name);
        this.setDescription(description);
        
        this.setDarkstarPkg(null);
        this.setTestSpecs(new ArrayList<TestSpecDTO>());
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
    
    public String getDescription() { return description; }
    protected void setDescription(String description) { this.description = description; }
    public void updateDescription(String description)
            throws DTCInvalidDataException {
        this.updateAttribute("description", description);
    }
    
    /**
     * Returns the {@link PkgLibraryDTO} object that represents the binary
     * darkstar package that is to be used in this test suite.
     * 
     * @return darkstar package library
     */
    public PkgLibraryDTO getDarkstarPkg() { return darkstarPkg; }
    protected void setDarkstarPkg(PkgLibraryDTO darkstarPkg) { this.darkstarPkg = darkstarPkg; }
    public void updateDarkstarPkg(PkgLibraryDTO darkstarPkg)
            throws DTCInvalidDataException {
        this.updateAttribute("darkstarPkg", darkstarPkg);
    }
    
    /**
     * Returns the list of {@link TestSpecDTO} objects that are to be run
     * in succession 
     * 
     * @return list of tests
     */
    public List<TestSpecDTO> getTestSpecs() { return testSpecs; }
    protected void setTestSpecs(List<TestSpecDTO> testSpecs) { this.testSpecs = testSpecs; }
    public void updateTestSpecs(List<TestSpecDTO> testSpecs)
            throws DTCInvalidDataException {
        this.updateAttribute("testSpecs", testSpecs);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
}
