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
import org.apache.commons.beanutils.PropertyUtils;
import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;

/**
 * Provides basic functionality for a Data Transfer Object in the
 * Darkstar Test Cluster.
 */
public abstract class AbstractDTO implements Serializable
{
    private Boolean fullyPopulated = false;
    private Map<String, Object> updatedAttributes = new HashMap<String, Object>();
    
    public Boolean getFullyPopulated()
    {
        return fullyPopulated;
    }
    
    public Map<String, Object> getUpdatedAttributes()
    {
        return updatedAttributes;
    }

    /**
     * Schedules update of the attribute with the name attribute
     * by loading the given value into the updatedAttributes map.
     * The value is only loaded into the map if the object's current value
     * of the attribute is different than the given value.
     * 
     * @param attribute the name of the attribute to update
     * @param value the value to set the attribute to
     * 
     * @throws com.projectdarkstar.tools.dtc.service.DTCInvalidDataException
     * if there is a problem retrieving the current value of the attribute
     */
    protected void updateAttribute(String attribute, Object value)
            throws DTCInvalidDataException
    {
        try {
            Object currentValue = PropertyUtils.getProperty(this, attribute);
            
            if(currentValue != null && !currentValue.equals(value))
                updatedAttributes.put(attribute, value);
            else if(currentValue == null && value != null)
                updatedAttributes.put(attribute, value);
            else
                return;
        } catch(Exception e) {
            throw new DTCInvalidDataException(
                    "Error retrieving current value of attribute "+attribute,
                    e);
        }
    }
    
    /**
     * Returns the value to validate for a particular attribute.
     * If the attribute has a pending update via a call to {@link #updateAttribute(String,Object)}
     * then that value is returned.  Otherwise the current value of the attribute
     * is returned
     *
     * @param key the attribute name to get the value for
     * @return the value to validate
     * @throws DTCInvalidDataException if the attribute with the specified key cannot be retrieved
     */
    protected Object valueToValidate(String key)
            throws DTCInvalidDataException
    {
        try {
            if (this.getUpdatedAttributes().keySet().contains(key))
                return this.getUpdatedAttributes().get(key);
            else
                return PropertyUtils.getProperty(this, key);
        } catch(Exception e) {
            throw new DTCInvalidDataException(
                    "Error retrieving current value of attribute "+key,
                    e);
        }
    }
    
    /**
     * Checks if the given attribute is blank or null.
     *
     * @param key the attribute name to check
     * @throws DTCInvalidDataException if the attribute with the given name is a blank string
     */
    protected void checkBlank(String key) throws DTCInvalidDataException
    {
        //check that attribute is not blank
        Object checkValue = valueToValidate(key);
        
        if(checkValue == null || checkValue.equals(""))
            throw new DTCInvalidDataException("Value for "+key+" attribute cannot be left blank");
    }
    /**
     * Checks if the given attribute is null.
     *
     * @param key the attribute name to check
     * @throws DTCInvalidDataException if the attribute with the given name is a null
     */
    protected void checkNull(String key) throws DTCInvalidDataException
    {
        //check that attribute is not blank
        Object checkValue = valueToValidate(key);
        
        if(checkValue == null)
            throw new DTCInvalidDataException("Value for "+key+" attribute cannot be null");
    }


    
    
    /**
     * Validates that each attribute and pending updated attribute (from the
     * updatedAttributes Map) has a valid value in the context of the
     * particular object.
     * 
     * @throws com.projectdarkstar.tools.dtc.service.DTCInvalidDataException
     * if validation fails
     */
    public abstract void validate() throws DTCInvalidDataException;
}
