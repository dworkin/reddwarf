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
 * Captures complete runtime configuration and result log file for the
 * execution of the server application on a specific resource during
 * execution of the test.
 */
public class TestExecutionResultServerLogDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    
    private HardwareResourceDTO resource;
    private LogFileDTO logFile;
    
    private String originalServerAppName;
    private String originalServerAppDescription;
    private String originalServerAppClassName;
    private String originalServerAppClassPath;
    private PkgLibraryDTO originalServerAppRequiredPkg;
    
    private String originalServerAppConfigName;
    private String originalServerAppConfigAdditionalCommandLine;
    private ServerAppConfigDTO originalServerAppConfig;
    
    private List<PropertyDTO> properties;
    private TestExecutionResultDTO parentResult;
    
    public TestExecutionResultServerLogDTO(Long id,
                                           Long versionNumber,
                                           String originalServerAppName,
                                           String originalServerAppDescription,
                                           String originalServerAppClassName,
                                           String originalServerAppClassPath,
                                           String originalServerAppConfigName,
                                           String originalServerAppConfigAdditionalCommandLine)
    {
        this.setId(id);
        this.setVersionNumber(versionNumber);
        
        this.setOriginalServerAppName(originalServerAppName);
        this.setOriginalServerAppDescription(originalServerAppDescription);
        this.setOriginalServerAppClassName(originalServerAppClassName);
        this.setOriginalServerAppClassPath(originalServerAppClassPath);
        this.setOriginalServerAppRequiredPkg(null);
        
        this.setOriginalServerAppConfigName(originalServerAppConfigName);
        this.setOriginalServerAppConfigAdditionalCommandLine(originalServerAppConfigAdditionalCommandLine);
        this.setOriginalServerAppConfig(null);
        
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
    
    public String getOriginalServerAppName() { return originalServerAppName; }
    private void setOriginalServerAppName(String originalServerAppName) { this.originalServerAppName = originalServerAppName; }
    
    public String getOriginalServerAppDescription() { return originalServerAppDescription; }
    private void setOriginalServerAppDescription(String originalServerAppDescription) { this.originalServerAppDescription = originalServerAppDescription; }
    
    public String getOriginalServerAppClassName() { return originalServerAppClassName; }
    private void setOriginalServerAppClassName(String originalServerAppClassName) { this.originalServerAppClassName = originalServerAppClassName; }
    
    public String getOriginalServerAppClassPath() { return originalServerAppClassPath; }
    private void setOriginalServerAppClassPath(String originalServerAppClassPath) { this.originalServerAppClassPath = originalServerAppClassPath; }
    
    public PkgLibraryDTO getOriginalServerAppRequiredPkg() { return originalServerAppRequiredPkg; }
    private void setOriginalServerAppRequiredPkg(PkgLibraryDTO originalServerAppRequiredPkg) { this.originalServerAppRequiredPkg = originalServerAppRequiredPkg; }
    
    public String originalServerAppConfigName() { return originalServerAppConfigName; }
    private void setOriginalServerAppConfigName(String originalServerAppConfigName) { this.originalServerAppConfigName = originalServerAppConfigName; }
    
    public String getOriginalServerAppConfigAdditionalCommandLine() { return originalServerAppConfigAdditionalCommandLine; }
    private void setOriginalServerAppConfigAdditionalCommandLine(String originalServerAppConfigAdditionalCommandLine) { this.originalServerAppConfigAdditionalCommandLine = originalServerAppConfigAdditionalCommandLine; }
    
    public ServerAppConfigDTO getOriginalServerAppConfig() { return originalServerAppConfig; }
    private void setOriginalServerAppConfig(ServerAppConfigDTO originalServerAppConfig) { this.originalServerAppConfig = originalServerAppConfig; }
    
    
    public List<PropertyDTO> getProperties() { return properties; }
    protected void setProperties(List<PropertyDTO> properties) { this.properties = properties; }
    
    public TestExecutionResultDTO getParentResult() { return parentResult; }
    protected void setParentResult(TestExecutionResultDTO parentResult) { this.parentResult = parentResult; }
    public void updateParentResult(TestExecutionResultDTO parentResult)
            throws DTCInvalidDataException {
        this.updateAttribute("parentResult", parentResult);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
}
