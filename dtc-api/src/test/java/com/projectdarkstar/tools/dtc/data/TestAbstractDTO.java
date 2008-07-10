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
import org.junit.Test;
import org.junit.Assert;

/**
 * Test the AbstractDTO class
 */
public class TestAbstractDTO
{
    @Test
    public void testUpdateAttributeNormal() throws DTCInvalidDataException
    {
        //ensure that updating a null value that is already null does
        //not schedule the update in the updates map
        NormalDTO nullOverNull = new NormalDTO(null);
        nullOverNull.updateAttribute("attribute", null);
        Assert.assertFalse(nullOverNull.getUpdatedAttributes().containsKey("attribute"));
        
        //ensure that updating a null value with a not null value
        //will schedule the update in the updates map
        NormalDTO notNullOverNull = new NormalDTO(null);
        notNullOverNull.updateAttribute("attribute", "test");
        Assert.assertEquals("test", notNullOverNull.getUpdatedAttributes().get("attribute"));
        
        //ensure that updating a not null value with an equal value
        //will not schedule the update in the updates map
        NormalDTO notNullOverEqual = new NormalDTO("test");
        notNullOverEqual.updateAttribute("attribute", "test");
        Assert.assertFalse(notNullOverEqual.getUpdatedAttributes().containsKey("attribute"));
        
        //ensure that updating a not null value with an unequal value
        //will schedule the update in the updates map
        NormalDTO notNullOverNotEqual = new NormalDTO("test");
        notNullOverNotEqual.updateAttribute("attribute", "test2");
        Assert.assertEquals("test2", notNullOverNotEqual.getUpdatedAttributes().get("attribute"));
    }
    
    @Test (expected=DTCInvalidDataException.class)
    public void testUpdateAttributeNoGetter() throws DTCInvalidDataException
    {
        //ensure that updating an attribute with no getter throws
        //an exception
        NoGetterDTO noGetter = new NoGetterDTO("test");
        noGetter.updateAttribute("attribute", "test2");
    }
    
    @Test (expected=DTCInvalidDataException.class)
    public void testUpdateAttributePrivateGetter() throws DTCInvalidDataException
    {
        //ensure that updating an attribute with a private getter throws
        //an exception
        PrivateGetterDTO privateGetter = new PrivateGetterDTO("test");
        privateGetter.updateAttribute("attribute", "test2");
    }
    
    @Test (expected=DTCInvalidDataException.class)
    public void testUpdateAttributeExceptionGetter() throws DTCInvalidDataException
    {
        //ensure that updating an attribute with a getter that throws
        //and exception throws an exception
        ExceptionGetterDTO exceptionGetter = new ExceptionGetterDTO("test");
        exceptionGetter.updateAttribute("attribute", "test2");
    }
    
    
    @Test
    public void testValueToValidateUpdated() throws DTCInvalidDataException
    {
        //ensure that valueToValidate returns the value of the
        //attribute in the updates map if an
        //update has been made
        NormalDTO object = new NormalDTO("test");
        object.updateAttribute("attribute", "test2");
        Object value = object.valueToValidate("attribute");
        Assert.assertEquals(value, "test2");
    }
    
    @Test
    public void testValueToValidateNotUpdated() throws DTCInvalidDataException
    {
        //ensure that valueToValidate returns the current attribute
        //if not update has been made
        NormalDTO object = new NormalDTO("test");
        Object value = object.valueToValidate("attribute");
        Assert.assertEquals(value, "test");
    }
    
    @Test (expected=DTCInvalidDataException.class)
    public void testValueToValidateNonAttribute() throws DTCInvalidDataException
    {
        //ensure that valueToValidate throws the appropriate exception
        //when trying to retrieve an attribute that doesn not exist
        NormalDTO object = new NormalDTO("test");
        Object value = object.valueToValidate("nothing");
    }
    
    @Test
    public void testCheckBlankNotBlank() throws DTCInvalidDataException
    {
        NormalDTO object = new NormalDTO("test");
        object.checkBlank("attribute");
    }
    
    @Test (expected=DTCInvalidDataException.class)
    public void testCheckBlankBlank() throws DTCInvalidDataException
    {
        //ensure an exception is thrown when checking if a blank attribute
        //is blank
        NormalDTO object = new NormalDTO("");
        object.checkBlank("attribute");
    }
    
    @Test (expected=DTCInvalidDataException.class)
    public void testCheckBlankNull() throws DTCInvalidDataException
    {
        //ensure an exception is thrown when checking if a blank attribute
        //is null
        NormalDTO object = new NormalDTO(null);
        object.checkBlank("attribute");
    }
    
    @Test
    public void testCheckNullNotNull() throws DTCInvalidDataException
    {
        NormalDTO object = new NormalDTO("test");
        object.checkNull("attribute");
    }
    
    @Test (expected=DTCInvalidDataException.class)
    public void testCheckNullNull() throws DTCInvalidDataException
    {
        //ensure an exception is thrown when checking if a null attribute
        //is null
        NormalDTO object = new NormalDTO(null);
        object.checkNull("attribute");
    }
    
    public class NormalDTO extends AbstractDTO
    {
        private Object attribute;
        
        public NormalDTO(Object attribute)
        {
            this.attribute = attribute;
        }
        
        public Object getAttribute() { return attribute; }
        public void setAttribute(Object attribute) { this.attribute = attribute; }
        
        public void validate() throws DTCInvalidDataException {}
    }
    
    public class NoGetterDTO extends AbstractDTO
    {
        private Object attribute;
        
        public NoGetterDTO(Object attribute)
        {
            this.attribute = attribute;
        }
        
        public void validate() throws DTCInvalidDataException {}
    }
    
    public class PrivateGetterDTO extends AbstractDTO
    {
        private Object attribute;
        
        public PrivateGetterDTO(Object attribute)
        {
            this.attribute = attribute;
        }
        
        private Object getAttribute() { return attribute; }
        public void validate() throws DTCInvalidDataException {}
    }
    
    public class ExceptionGetterDTO extends AbstractDTO
    {
        private Object attribute;
        
        public ExceptionGetterDTO(Object attribute)
        {
            this.attribute = attribute;
        }
        
        public Object getAttribute() throws Exception
        { throw new Exception("error"); }
        
        public void validate() throws DTCInvalidDataException {}
    }
    
}
