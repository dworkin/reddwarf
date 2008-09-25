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
import org.apache.commons.lang.ObjectUtils;

/**
 * Captures complete runtime configuration, hardware resource executed on,
 * and result log file for the execution of a {@link ClientAppConfigDTO}
 * client application simulator.
 */
public class TestExecutionResultClientLogDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    
    private HardwareResourceDTO resource;
    private LogFileDTO logFile;
    
    private String originalClientAppName;
    private String originalClientAppDescription;
    private PkgLibraryDTO originalClientAppRequiredPkg;
    
    private String originalClientAppConfigName;
    private String originalClientAppConfigPath;
    private ClientAppConfigTypeDTO originalClientAppConfigPropertyMethod;
    private ClientAppConfigDTO originalClientAppConfig;
    
    private List<PropertyDTO> properties;
    private TestExecutionResultDTO parentResult;
    
    public TestExecutionResultClientLogDTO(Long id,
                                           Long versionNumber,
                                           String originalClientAppName,
                                           String originalClientAppDescription,
                                           String originalClientAppConfigName,
                                           String originalClientAppConfigPath,
                                           ClientAppConfigTypeDTO originalClientAppConfigPropertyMethod)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setResource(null);
        this.setLogFile(null);
        
        this.setOriginalClientAppName(originalClientAppName);
        this.setOriginalClientAppDescription(originalClientAppDescription);
        this.setOriginalClientAppRequiredPkg(null);
        this.setOriginalClientAppConfigName(originalClientAppConfigName);
        this.setOriginalClientAppConfigPath(originalClientAppConfigPath);
        this.setOriginalClientAppConfigPropertyMethod(originalClientAppConfigPropertyMethod);
        this.setOriginalClientAppConfig(null);
        
        this.setProperties(new ArrayList<PropertyDTO>());
        this.setParentResult(null);
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
    
    
    public String getOriginalClientAppName() { return originalClientAppName; }
    private void setOriginalClientAppName(String originalClientAppName) { this.originalClientAppName = originalClientAppName; }
    
    public String getOriginalClientAppDescription() { return originalClientAppDescription; }
    private void setOriginalClientAppDescription(String originalClientAppDescription) { this.originalClientAppDescription = originalClientAppDescription; }
    
    public PkgLibraryDTO getOriginalClientAppRequiredPkg() { return originalClientAppRequiredPkg; }
    private void setOriginalClientAppRequiredPkg(PkgLibraryDTO originalClientAppRequiredPkg) { this.originalClientAppRequiredPkg = originalClientAppRequiredPkg; }
    
    public String getOriginalClientAppConfigName() { return originalClientAppConfigName; }
    private void setOriginalClientAppConfigName(String originalClientAppConfigName) { this.originalClientAppConfigName = originalClientAppConfigName; }
    
    public String getOriginalClientAppConfigPath() { return originalClientAppConfigPath; }
    private void setOriginalClientAppConfigPath(String originalClientAppConfigPath) { this.originalClientAppConfigPath = originalClientAppConfigPath; }
    
    public ClientAppConfigTypeDTO getOriginalClientAppConfigPropertyMethod() { return originalClientAppConfigPropertyMethod; }
    private void setOriginalClientAppConfigPropertyMethod(ClientAppConfigTypeDTO originalClientAppConfigPropertyMethod) { this.originalClientAppConfigPropertyMethod = originalClientAppConfigPropertyMethod; }
    
    public ClientAppConfigDTO getOriginalClientAppConfig() { return originalClientAppConfig; }
    private void setOriginalClientAppConfig(ClientAppConfigDTO originalClientAppConfig) { this.originalClientAppConfig = originalClientAppConfig; }
    
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

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException
    {
        this.checkNull("resource");
        this.checkNull("logFile");
        this.checkNull("properties");
        this.checkNull("parentResult");
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
	if(!(o instanceof TestExecutionResultClientLogDTO) || o == null) return false;

        TestExecutionResultClientLogDTO other = (TestExecutionResultClientLogDTO)o;
        return ObjectUtils.equals(this.getId(), other.getId()) &&
                ObjectUtils.equals(this.getVersionNumber(), other.getVersionNumber()) &&
                ObjectUtils.equals(this.getResource(), other.getResource()) &&
                ObjectUtils.equals(this.getLogFile(), other.getLogFile()) &&
                ObjectUtils.equals(this.getOriginalClientAppName(), other.getOriginalClientAppName()) &&
                ObjectUtils.equals(this.getOriginalClientAppDescription(), other.getOriginalClientAppDescription()) &&
                ObjectUtils.equals(this.getOriginalClientAppRequiredPkg(), other.getOriginalClientAppRequiredPkg()) &&
                ObjectUtils.equals(this.getOriginalClientAppConfigName(), other.getOriginalClientAppConfigName()) &&
                ObjectUtils.equals(this.getOriginalClientAppConfigPath(), other.getOriginalClientAppConfigPath()) &&
                ObjectUtils.equals(this.getOriginalClientAppConfigPropertyMethod(), other.getOriginalClientAppConfigPropertyMethod()) &&
                ObjectUtils.equals(this.getOriginalClientAppConfig(), other.getOriginalClientAppConfig()) &&
                ObjectUtils.equals(this.getProperties(), other.getProperties()) &&
                ObjectUtils.equals(this.getParentResult(), other.getParentResult());
    }
    
    public int hashCode() {
        int hash = 7;
        int hashId = 31*hash + ObjectUtils.hashCode(this.getId());
        int hashOriginalClientAppName = 31*hash + ObjectUtils.hashCode(this.getOriginalClientAppName());
        return hashId + hashOriginalClientAppName;
    }
}
