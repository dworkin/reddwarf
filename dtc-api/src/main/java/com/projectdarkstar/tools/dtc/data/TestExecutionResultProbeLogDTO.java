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
import org.apache.commons.lang.ObjectUtils;

/**
 * Captures complete runtime configuration, hardware resource executed on,
 * and result log file for the execution of a {@link SystemProbeDTO}
 * during the test.
 */
public class TestExecutionResultProbeLogDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    
    private HardwareResourceDTO resource;
    private LogFileDTO logFile;
    
    private String originalSystemProbeName;
    private String originalSystemProbeClassName;
    private String originalSystemProbeClassPath;
    private String originalSystemProbeMetric;
    private String originalSystemProbeUnits;
    private PkgLibraryDTO originalSystemProbeRequiredPkg;
    private SystemProbeDTO originalSystemProbe;
    
    private List<PropertyDTO> properties;
    private TestExecutionResultDTO parentResult;
    
    private List<TestExecutionResultProbeDataDTO> data;
    
    public TestExecutionResultProbeLogDTO(Long id,
                                          Long versionNumber,
                                          String originalSystemProbeName,
                                          String originalSystemProbeClassName,
                                          String originalSystemProbeClassPath,
                                          String originalSystemProbeMetric,
                                          String originalSystemProbeUnits)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setOriginalSystemProbeName(originalSystemProbeName);
        this.setOriginalSystemProbeClassName(originalSystemProbeClassName);
        this.setOriginalSystemProbeClassPath(originalSystemProbeClassPath);
        this.setOriginalSystemProbeMetric(originalSystemProbeMetric);
        this.setOriginalSystemProbeUnits(originalSystemProbeUnits);
        
        this.setOriginalSystemProbeRequiredPkg(null);
        this.setOriginalSystemProbe(null);
        
        this.setProperties(new ArrayList<PropertyDTO>());
        this.setParentResult(null);
        
        this.setData(new ArrayList<TestExecutionResultProbeDataDTO>());
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
    
    public HardwareResourceDTO getResource() { return resource; }
    protected void setResource(HardwareResourceDTO resource) { this.resource = resource; }
    public void updateResource(HardwareResourceDTO resource)
            throws DTCInvalidDataException {
        this.updateAttribute("resource", resource);
    }
    
    public LogFileDTO getLogFile() { return logFile; }
    protected void setLogFile(LogFileDTO logFile) { this.logFile = logFile; }
    public void updateLogFile(LogFileDTO logFile)
            throws DTCInvalidDataException {
        this.updateAttribute("logFile", logFile);
    }
    
    
    public String getOriginalSystemProbeName() { return originalSystemProbeName; }
    private void setOriginalSystemProbeName(String originalSystemProbeName) { this.originalSystemProbeName = originalSystemProbeName; }
    
    public String getOriginalSystemProbeClassName() { return originalSystemProbeClassName; }
    private void setOriginalSystemProbeClassName(String originalSystemProbeClassName) { this.originalSystemProbeClassName = originalSystemProbeClassName; }
    
    public String getOriginalSystemProbeClassPath() { return originalSystemProbeClassPath; }
    private void setOriginalSystemProbeClassPath(String originalSystemProbeClassPath) { this.originalSystemProbeClassPath = originalSystemProbeClassPath; }
    
    public String getOriginalSystemProbeMetric() { return originalSystemProbeMetric; }
    private void setOriginalSystemProbeMetric(String originalSystemProbeMetric) { this.originalSystemProbeMetric = originalSystemProbeMetric; }
    
    public String getOriginalSystemProbeUnits() { return originalSystemProbeUnits; }
    private void setOriginalSystemProbeUnits(String originalSystemProbeUnits) { this.originalSystemProbeUnits = originalSystemProbeUnits; }
    
    public PkgLibraryDTO getOriginalSystemProbeRequiredPkg() { return originalSystemProbeRequiredPkg; }
    private void setOriginalSystemProbeRequiredPkg(PkgLibraryDTO originalSystemProbeRequiredPkg) { this.originalSystemProbeRequiredPkg = originalSystemProbeRequiredPkg; }
    
    public SystemProbeDTO getOriginalSystemProbe() { return originalSystemProbe; }
    private void setOriginalSystemProbe(SystemProbeDTO originalSystemProbe) { this.originalSystemProbe = originalSystemProbe; }
    
    
    
    
    public List<PropertyDTO> getProperties() { return properties; }
    protected void setProperties(List<PropertyDTO> properties) { this.properties = properties; }
    public void updateProperties(List<PropertyDTO> properties)
            throws DTCInvalidDataException {
        this.updateAttribute("properties", properties);
    }
    
    public TestExecutionResultDTO getParentResult() { return parentResult; }
    protected void setParentResult(TestExecutionResultDTO parentResult) { this.parentResult = parentResult; }
    public void updateParentResult(TestExecutionResultDTO parentResult)
            throws DTCInvalidDataException {
        this.updateAttribute("parentResult", parentResult);
    }
    
    /**
     * A list of {@link TestExecutionResultProbeDataDTO} objects are
     * periodically collected during the execution of a {@link SystemProbeDTO}
     * to monitor the specific metric that the probe measures over time.
     * Returns a list of these data objects.
     * 
     * @return list of probe data points
     */
    public List<TestExecutionResultProbeDataDTO> getData() { return data; }
    protected void setData(List<TestExecutionResultProbeDataDTO> data) { this.data = data; }
    public void updateData(List<TestExecutionResultProbeDataDTO> data)
            throws DTCInvalidDataException {
        this.updateAttribute("data", data);
    }

    
    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException
    {
        this.checkNull("resource");
        this.checkNull("logFile");
        this.checkNull("properties");
        this.checkNull("parentResult");
        this.checkNull("data");
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof TestExecutionResultProbeLogDTO) || o == null) return false;

        TestExecutionResultProbeLogDTO other = (TestExecutionResultProbeLogDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getResource(), other.getResource()) &&
                ObjectUtils.equals(this.getLogFile(), other.getLogFile()) &&
                ObjectUtils.equals(this.getOriginalSystemProbeName(), other.getOriginalSystemProbeName()) &&
                ObjectUtils.equals(this.getOriginalSystemProbeClassName(), other.getOriginalSystemProbeClassName()) &&
                ObjectUtils.equals(this.getOriginalSystemProbeClassPath(), other.getOriginalSystemProbeClassPath()) &&
                ObjectUtils.equals(this.getOriginalSystemProbeMetric(), other.getOriginalSystemProbeMetric()) &&
                ObjectUtils.equals(this.getOriginalSystemProbeUnits(), other.getOriginalSystemProbeUnits()) &&
                ObjectUtils.equals(this.getOriginalSystemProbeRequiredPkg(), other.getOriginalSystemProbeRequiredPkg()) &&
                ObjectUtils.equals(this.getOriginalSystemProbe(), other.getOriginalSystemProbe()) &&
                ObjectUtils.equals(this.getProperties(), other.getProperties()) &&
                ObjectUtils.equals(this.getParentResult(), other.getParentResult()) &&
                ObjectUtils.equals(this.getData(), other.getData());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashOriginalSystemProbeName = 31*hash + ObjectUtils.hashCode(this.getOriginalSystemProbeName());
        return hashId + hashOriginalSystemProbeName;
    }
}
