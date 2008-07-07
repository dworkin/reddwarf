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
    
    
    public TestExecutionResultClientLogDTO()
    {

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
    public void validate() throws DTCInvalidDataException {}
}
