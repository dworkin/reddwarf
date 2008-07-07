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
 * Represents the results for a specific instance of a {@link TestSpecDTO}.
 * Each TestExecutionResult is part of a parent {@link TestExecutionDTO}
 * to make up one cohesive set of test results.
 */
public class TestExecutionResultDTO extends AbstractDTO
{
    private Long id;
    private Long versionNumber;
    
    private TestExecutionResultValueDTO result;
    private LogFileDTO resultSummary;
    
    private List<PropertyDTO> properties;
    private List<TestExecutionResultServerLogDTO> serverLogs;
    private List<TestExecutionResultClientLogDTO> clientLogs;
    private List<TestExecutionResultProbeLogDTO> probeLogs;
    
    private List<TestExecutionResultClientDataDTO> clientData;
    
    private List<HardwareResourceDTO> serverResources;
    private List<HardwareResourceDTO> clientResources;
    
    private List<HardwareResourceFamilyDTO> originalServerResources;
    private List<HardwareResourceFamilyDTO> originalClientResources;
    private String originalTestSpecName;
    private String originalTestSpecDescription;
    private String originalTestSpecTestRunner;
    private Long originalTestSpecTimeLimit;
    private Long originalTestSpecMaxClients;
    private TestSpecDTO originalTestSpec;
    
    private TestExecutionDTO parentExecution;
    
    public TestExecutionResultDTO() {}
    
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
    
    public TestExecutionResultValueDTO getResult() { return result; }
    protected void setResult(TestExecutionResultValueDTO result) { this.result = result; }
    public void updateResult(TestExecutionResultValueDTO result)
            throws DTCInvalidDataException {
        this.updateAttribute("result", result);
    }
            
    public LogFileDTO getResultSummary() { return resultSummary; }
    protected void setResultSummary(LogFileDTO resultSummary) { this.resultSummary = resultSummary; }
    public void updateResultSummary(LogFileDTO resultSummary)
            throws DTCInvalidDataException {
        this.updateAttribute("resultSummary", resultSummary);    }
    
    /**
     * Returns a list of arguments in the form of {@link PropertyDTO} objects
     * to be passed to the TestRunner during run time.  These are derived
     * from the original {@link TestSpecDTO} used to create this
     * TestExecutionResult and should be customized for each specific case.
     * 
     * @return list of arguments
     */
    public List<PropertyDTO> getProperties() { return properties; }
    protected void setProperties(List<PropertyDTO> properties) { this.properties = properties; }
    public void updateProperties(List<PropertyDTO> properties)
            throws DTCInvalidDataException {
        this.updateAttribute("properties", properties);
    }
    
    /**
     * A {@link TestExecutionResultServerLogDTO} is generated for each
     * {@link HardwareResourceDTO} that the server application is run on during
     * the test.  Returns a list of these logs.
     * 
     * @return list of server logs
     */
    public List<TestExecutionResultServerLogDTO> getServerLogs() { return serverLogs; }
    protected void setServerLogs(List<TestExecutionResultServerLogDTO> serverLogs) { this.serverLogs = serverLogs; }
    public void updateServerLogs(List<TestExecutionResultServerLogDTO> serverLogs)
            throws DTCInvalidDataException {
        this.updateAttribute("serverLogs", serverLogs);
    }
    
    /**
     * A {@link TestExecutionResultClientLogDTO} is generated for each
     * {@link ClientAppConfigDTO} client application simulator that is run
     * during the test.  Returns a list of these logs.
     * 
     * @return list of client logs
     */
    public List<TestExecutionResultClientLogDTO> getClientLogs() { return clientLogs; }
    protected void setClientLogs(List<TestExecutionResultClientLogDTO> clientLogs) { this.clientLogs = clientLogs; }
    public void updateClientLogs(List<TestExecutionResultClientLogDTO> clientLogs)
            throws DTCInvalidDataException {
        this.updateAttribute("clientLogs", clientLogs);
    }
    
    /**
     * A {@link TestExecutionResultProbeLogDTO} is generated for each
     * {@link SystemProbeDTO} monitoring the system during the test.
     * Returns a list of these logs.
     * 
     * @return list of probe logs
     */
    public List<TestExecutionResultProbeLogDTO> getProbeLogs() { return probeLogs; }
    protected void setProbeLogs(List<TestExecutionResultProbeLogDTO> probeLogs) { this.probeLogs = probeLogs; }
    public void updateProbeLogs(List<TestExecutionResultProbeLogDTO> probeLogs)
            throws DTCInvalidDataException {
        this.updateAttribute("probeLogs", probeLogs);
    }
    
    /**
     * A list of {@link TestExecutionResultClientDataDTO} objects are
     * periodically collected during the execution of a test to monitor how
     * many clients are acting in the system over time.  Returns a list
     * of these data objects.
     * 
     * @return list of client data points
     */
    public List<TestExecutionResultClientDataDTO> getClientData() { return clientData; }
    protected void setClientData(List<TestExecutionResultClientDataDTO> clientData) { this.clientData = clientData; }
    public void updateClientData(List<TestExecutionResultClientDataDTO> clientData)
            throws DTCInvalidDataException {
        this.updateAttribute("clientData", clientData);
    }
    
    
    /**
     * Returns a list of {@link HardwareResourceDTO} objects that are used to
     * run the server application on during the test.
     * 
     * @return list of server resources
     */
    public List<HardwareResourceDTO> getServerResources() { return serverResources; }
    protected void setServerResources(List<HardwareResourceDTO> serverResources) { this.serverResources = serverResources; }
    public void updateServerResources(List<HardwareResourceDTO> serverResources)
            throws DTCInvalidDataException {
        this.updateAttribute("serverResources", serverResources);
    }
    
    /**
     * Returns a list of {@link HardwareResourceDTO} objects taht are used
     * to run the client application simulators during the test
     * 
     * @return list of client resources
     */
    public List<HardwareResourceDTO> getClientResources() { return clientResources; }
    protected void setClientResources(List<HardwareResourceDTO> clientResources) { this.clientResources = clientResources; }
    public void updateClientResources(List<HardwareResourceDTO> clientResources)
            throws DTCInvalidDataException {
        this.updateAttribute("clientResources", clientResources);
    }
    
    
    public List<HardwareResourceFamilyDTO> getOriginalServerResources() { return originalServerResources; }
    private void setOriginalServerResources(List<HardwareResourceFamilyDTO> originalServerResources) { this.originalServerResources = originalServerResources; }
    
    public List<HardwareResourceFamilyDTO> getOriginalClientResources() { return originalClientResources; }
    private void setOriginalClientResources(List<HardwareResourceFamilyDTO> originalClientResources) { this.originalClientResources = originalClientResources; }
    
    
    
    
    public String getOriginalTestSpecName() { return originalTestSpecName; }
    private void setOriginalTestSpecName(String originalTestSpecName) { this.originalTestSpecName = originalTestSpecName; }
    
    public String getOriginalTestSpecDescription() { return originalTestSpecDescription; }
    private void setOriginalTestSpecDescription(String originalTestSpecDescription) { this.originalTestSpecDescription = originalTestSpecDescription; }
    
    public String getOriginalTestSpecTestRunner() { return originalTestSpecTestRunner; }
    private void setOriginalTestSpecTestRunner(String originalTestSpecTestRunner) { this.originalTestSpecTestRunner = originalTestSpecTestRunner; }
    
    public Long getOriginalTestSpecTimeLimit() { return originalTestSpecTimeLimit; }
    private void setOriginalTestSpecTimeLimit(Long originalTestSpecTimeLimit) { this.originalTestSpecTimeLimit = originalTestSpecTimeLimit; }
    
    public Long getOriginalTestSpecMaxClients() { return originalTestSpecMaxClients; }
    private void setOriginalTestSpecMaxClients(Long originalTestSpecMaxClients) { this.originalTestSpecMaxClients = originalTestSpecMaxClients; }
    
    public TestSpecDTO getOriginalTestSpec() { return originalTestSpec; }
    private void setOriginalTestSpec(TestSpecDTO originalTestSpec) { this.originalTestSpec = originalTestSpec; }
    
    public TestExecutionDTO getParentExecution() { return parentExecution; }
    protected void setParentExecution(TestExecutionDTO parentExecution) { this.parentExecution = parentExecution; }
    public void updateParentExecution(TestExecutionDTO parentExecution)
            throws DTCInvalidDataException {
        this.updateAttribute("parentExecution", parentExecution);
    }

    /** @inheritDoc */
    public void validate() throws DTCInvalidDataException {}
}
