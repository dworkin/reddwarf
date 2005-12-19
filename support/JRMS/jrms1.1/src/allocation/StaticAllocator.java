/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * StaticAllocator.java
 */
package com.sun.multicast.allocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Vector;
import com.sun.multicast.util.BASE64Encoder;
import com.sun.multicast.util.Util;

/**
 * A MulticastAddressAllocator for statically allocated addresses. This
 * class allocates, deallocates, and otherwise manages multicast addresses
 * using a static configuration file. It implements the 
 * MulticastAddressAllocator interface, so it may be used with other 
 * JRMS allocators through the MulticastAddressManager class. Most applications 
 * should access this class through the MulticastAddressManager class.
 * 
 * <P>Each StaticAllocator keeps its state in a Properties object 
 * (and a few other data structures). In order to provide maximum reliability 
 * and because address allocations often persist across a long period of time, 
 * you may set a configuration file into which the allocator will save its 
 * state whenever it is changed. This allows address allocations to be 
 * preserved across multiple sessions.
 * Since some platforms do not handle simultaneous file access well, each
 * configuration file should be used by only one StaticAllocator object at once.
 * 
 * <P>The initial state of the allocator may be read from a configuration file
 * or a Properties object or set up by calling the addAddress method. Often,
 * the configuration file is created by hand by an administrator, who enters
 * multicast addresses into this file.
 * 
 * <P><STRONG>CAUTION:</STRONG>The addresses in the file need not be
 * administratively scoped. However, caution should be used to ensure that there
 * is no possibility of collisions (two hosts accidentally using the same 
 * address at the same time).
 * 
 * <P>The configuration file is a property file in the
 * format used by the java.util.Properties class. This file represents a list of
 * properties. Each property consists of a key and a value, each of which is a
 * String. There are two types of properties: scope properties and address
 * state properties.
 *
 * <P>A scope property represents a scope in the scope list.
 * The key for a scope property is a string of the form "Scope-N", where N
 * is a number indicating the scope's position in the scope list (starting with
 * 1 as the first scope in the list). The value has the form
 * "startAddr-endAddr ttl name lang-tag", where startAddr and endAddr are the
 * first and last addresses in the scope, ttl is the maximum ttl to be used
 * within the scope, name is the name of the scope (surrounded by double
 * quotes), and lang-tag is the language tag to be associated with the name.
 * This format can't handle scope names that contain double quotes or scopes
 * that have more than one name.
 *
 * <P>A lease property represents an existing lease. The key for a lease
 * property is a string consisting of the letter "L", followed by a base64
 * encoding of the lease identifier. The value has the form "addressSet
 * startTime duration", where addressSet consists of a left parenthesis,
 * zero or more addressRanges separated by spaces, and a right parenthesis.
 * startTime is a long representing the number of milliseconds since midnight
 * GMT on January 1, 1970. duration is an int number of seconds. An
 * addressRange is a starting address, a hyphen, and an ending address. And
 * the addresses are IPv4 addresses in dotted decimal format.
 * 
 * <P>The exact format of a property file is defined in The Java Language
 * Specification. A typical line in a StaticAllocator configuration file
 * looks like 
 * "LnTtc4jhWKJQaZLHQyGgLwQ\=\==(239.255.0.1-239.255.0.1)\ 938465910522\ -1".
 * This line indicates a lease whose lease identifier is base64 encoded as
 * "nTtc4jhWKJQaZLHQyGgLwQ==", which contains the single address 239.255.0.1,
 * allocated from Sep 27, 1999 20:58:30 GMT for an indefinite period of time.
 *
 * @see                         MulticastAddressManager
 * @see                         MulticastAddressAllocator
 */
public class StaticAllocator implements MulticastAddressAllocator, Cloneable {

    /**
     * Create a new StaticAllocator with no configuration file and no initial 
     * configuration. All state is stored in local objects and may be 
     * retrieved at any time with the getConfiguration method.
     * @exception AddressAllocationException if the allocator
     * could not be created
     */
    public StaticAllocator() throws AddressAllocationException {
        savedState = new Properties();
        random = new Random();
        
        ResourceBundle myResources = ResourceBundle.getBundle(
	    "com.sun.multicast.allocation.resources.AllocationResources");

        readConfig();
    }

    /**
     * Create a new StaticAllocator with no configuration file and the 
     * specified initial configuration. All state is stored in local objects 
     * and may be retrieved at any time with the getConfiguration method.
     * @exception AddressAllocationException if the allocator
     * could not be created
     */
    public StaticAllocator(Properties props) throws AddressAllocationException {
        this();

        setConfiguration(props);
    }

    /**
     * Create a new StaticAllocator using the specified configuration file.
     * If the config file does not exist, it is created.
     *
     * @param path the pathname of the configuration file
     * @exception AddressAllocationException if the allocator
     * could not be created
     */
    public StaticAllocator(String path) throws AddressAllocationException {
        this();

        setConfigFilePath(path);
    }

    /**
     * Gets the configuration file's path name. If there is no
     * configuration file, null is returned.
     * 
     * @return the configuration file's path name (null if none)
     */
    public synchronized String getConfigFilePath() {
        if (configFile == null) {
            return (null);
        } else {
            return (configFile.getPath());
        }
    }

    /**
     * Sets the configuration file's path name. The current state of
     * the allocator is discarded and replaced with whatever is in the
     * specified configuration file. If the path is null, the allocator's
     * configuration is not changed, but it is no longer associated with
     * any file.
     * 
     * @param path the configuration file's path name
     * @exception AddressAllocationException if the allocator
     * could not be created
     */
    public synchronized void setConfigFilePath(String path) 
            throws AddressAllocationException {
        if (path == null) {
            configFile = null;
        } else {
            File oldConfigFile = configFile;
            Properties oldState = savedState;

            try {
                configFile = new File(path);

                if (!configFile.exists()) {
                    writeConfig();
                } 

                readConfigFromFile();
                writeConfig();      // Just to make sure we can
            } catch (AddressAllocationException e) {
                configFile = oldConfigFile;
                savedState = oldState;

                try {
                    readConfig();
                } catch (AddressAllocationException e2) {}

                throw e;
            }
        }
    }

    /**
     * Creates a new StaticAllocator object that works like this one and 
     * returns it to the caller.  This is probably not a good idea, 
     * since the other StaticAllocator will share the same pool of addresses, 
     * but won't be coordinated with this one.
     * 
     * @return a copy of this StaticAllocator object
     * @exception CloneNotSupportedException if an error occurs
     */
    public Object clone() throws CloneNotSupportedException {
        StaticAllocator allocator = null;

        try {
            allocator = new StaticAllocator(getConfiguration());

            allocator.setConfigFilePath(getConfigFilePath());
        } catch (AddressAllocationException e) {
            throw new CloneNotSupportedException();
        }

        return (allocator);
    }

    /**
     * Gets a Properties object that represents the current state of 
     * this object.  The format of the properties are described in the 
     * javadoc for this class.
     * 
     * @return a Properties object that represents the current state of this 
     * object
     * @exception AddressAllocationException if an error occurs
     */
    public synchronized Properties getConfiguration() 
	throws AddressAllocationException {

        // Create the properties from the data structures

        Properties props = new Properties();

        // This method works even during the constructor, when the 
	// data structures haven't been initialized yet.

        if (leases != null) {

	    // Create properties for Scopes

	    Enumeration scopes = sl.getScopes();
	    int i = 1;
	    while (scopes.hasMoreElements()) {
	        Scope scope = (Scope) scopes.nextElement();
	        props.put("Scope-" + i, makeScopePropertyValue(scope));
	    }

	    // Create properties for leases

	    try {
                Enumeration leaseIDs = leases.keys();
                while (leaseIDs.hasMoreElements()) {
                    byte [] leaseID = (byte []) leaseIDs.nextElement();
                    Lease lease = (StaticAllocatorLease) leases.get(leaseID);
		    props.put("L" + new String(BASE64Encoder.encode(leaseID), 
			"UTF8"), makeLeasePropertyValue(lease));
	        }
	    } catch (UnsupportedEncodingException e) {
	        throw new AddressAllocationInternalException(e);
	    }
        }

        return (props);
    }

    /**
     * Makes a scope property value string out of a scope.
     * 
     * @param scope the Scope from which to make the string
     * @return the scope property value string representing the scope
     */
    private static String makeScopePropertyValue(Scope scope) {
        AddressRange range = scope.getAddresses();
        ScopeName name = scope.getDefaultName();
        return (makeAddressRangeString(range) + " " +
	    scope.getTTL() + " \"" + 
	    name.getName() + "\" " +
	    name.getLanguage());
    }

    /**
     * Makes a lease property value string out of a Lease.
     * 
     * @param lease the Lease from which to make the string
     * @return the lease property value string representing the lease
     */
    private static String makeLeasePropertyValue(Lease lease)
	throws AddressAllocationException {

        AddressSet set = lease.getAddresses();
	return (makeAddressSetString(set) + " " +
	    lease.getStartTime().getTime() + " " +
	    lease.getDuration());
    }

    /**
     * Makes an address range string out of an AddressRange.
     * 
     * @param range the AddressRange from which to make the string
     * @return the address range string representing the AddressRange
     */
    private static String makeAddressRangeString(AddressRange range) {
        return (range.getFirstAddress() + "-" + range.getLastAddress());
    }

    /**
     * Makes an address set string out of an AddressSet.
     * 
     * @param set the AddressSet from which to make the string
     * @return the address set string representing the AddressSet
     */
    private static String makeAddressSetString(AddressSet set) {
        String result = "(";
        Enumeration ranges = set.getAddressRanges();
        boolean first = true;
        while (ranges.hasMoreElements()) {
  	    AddressRange range = (AddressRange) ranges.nextElement();
	    if (first)
	        first = false;
	    else
  	        result = result + " ";
	    result = result + makeAddressRangeString(range);
        }
        result = result + ")";
        return (result);
    }

    /**
     * Sets the current state of this object based on a Properties object.
     * The format of the properties are described in the javadoc for this class.
     * 
     * @param props a Properties object that is used to set the current state 
     * of this object
     * @exception AddressAllocationException if an error occurs
     */
    public synchronized void setConfiguration(Properties props) 
	throws AddressAllocationException {

        try {

            // Do the hard work

            setConfig(props);
        } catch (AddressAllocationException e) {
	    e.printStackTrace();

            // If we hit an exception, try to throw away any changes we 
	    // might have started
	    if (inRecovery) {
	        inRecovery = false;
  	    } else {
	        inRecovery = true;
                try {
                    readConfig();
                } catch (Exception e2) {}
	        inRecovery = false;
    	    }
	    throw e;
        }

        // If everything went well, update the configuration.

        writeConfig();
    }

    /**
     * Inform the allocator that the MulticastAddressManager is now managing it.
     * If this method throws an exception, the MulticastAddressManager will not
     * accept it for management.
     * 
     * <P><STRONG>Note:</STRONG> This method should not be used by any classes
     * other than MulticastAddressManager.
     * @exception AddressAllocationException if the allocator does
     * not want to be managed
     */
    public synchronized void init() throws AddressAllocationException {}

    /**
     * Inform the allocator that the MulticastAddressManager is no longer
     * managing it. There is no way for the allocator to reject this.
     * 
     * <P><STRONG>Note:</STRONG> This method should not be used by any classes
     * other than MulticastAddressManager.
     */
    public synchronized void term() {}

    /**
     * Get the name used to identify the allocator. The application may supply 
     * this name as an argument to MulticastAddressManager.allocAddress() and
     * MulticastAddressManager.findAllocator(). The name may not be null, should
     * be unique, and must remain the same during the lifetime of a
     * MulticastAddressAllocator object.
     */
    public String getAllocatorName() {
        return ("StaticAllocator");
    }

    /**
     * Requests a change in the start time of a lease. This method
     * is package local because it should only be called by
     * StaticAllocatorLease.
     *
     * @param lease the lease to be changed
     * @param requestedStartTime the requested start time (null if now)
     * @param requiredStartTime the latest acceptable start time (null if now)
     * @exception AddressAllocationException if the request could not be 
     * satisfied
     */
    void requestSetStartTime(StaticAllocatorLease lease,
        Date requestedStartTime, Date requiredStartTime)
        throws AddressAllocationException {
        // @@@ Should implement this.
        throw new AddressAllocationException(
        myResources.getString("noSTCAllowed"));
    }

    /**
     * Requests a change in the duration of this lease. This method
     * is package local because it should only be called by
     * StaticAllocatorLease.
     *
     * @param lease the lease to be changed
     * @param requestedDuration the requested duration in seconds 
     * (-1 if indefinite)
     * @param requiredDuration the required duration in seconds 
     * (-1 if indefinite)
     * @exception AddressAllocationException if the request could not be 
     * satisfied
     */
    void requestSetDuration(StaticAllocatorLease lease,
	int requestedDuration, int requiredDuration)
	throws AddressAllocationException {
	// @@@ Should implement this.
	throw new AddressAllocationException(
	    myResources.getString("noDCAllowed"));
    }

    /**
     * Requests release of a lease. This method
     * is package local because it should only be called by
     * StaticAllocatorLease.
     *
     * @param lease the lease to be released
     * @exception AddressAllocationException if the request could not be 
     * satisfied
     */
    void requestRelease(StaticAllocatorLease lease)
	throws AddressAllocationException {
	// @@@ Should implement this.
	throw new AddressAllocationException(
	    myResources.getString("noReleases"));
    }

    /**
     * Get the multicast scope list.
     *
     * @return the multicast scope list
     * @exception AddressAllocationException if an exception was encountered
     */
    public ScopeList getScopeList(AddressType addressType)
	throws AddressAllocationException {
	return (sl);
    }

    /**
     * Allocate one or more multicast addresses, matching the specified 
     * parameters.
     *
     * <P>This method is used by the MulticastAddressManager when its 
     * allocateAddresses method has been called.
     *
     * @param scope the administrative scope requested
     * @param ttl the maximum ttl that will be used
     * @param count the number of multicast addresses requested (usually one)
     * @param requestedStartTime the requested start time (null if now)
     * @param requiredStartTime the latest acceptable start time (null if now)
     * @param requestedDuration the requested duration in seconds 
     * (-1 if indefinite)
     * @param requiredDuration the required duration in seconds 
     * (-1 if indefinite)
     * @param addressesRequested a requested address set (null if any will do)
     * @return the multicast address lease granted
     * @exception javax.jrms.addralloc.AddressAllocationException if an error 
     * occurred
     * @exception javax.jrms.addralloc.NoAddressAvailableException if no 
     * address was available that met the requirements
     */
    public Lease allocateAddresses(Scope scope, int ttl, int count, 
        Date requestedStartTime,
        Date requiredStartTime, int requestedDuration, int requiredDuration,
        AddressSet addressesRequested) throws AddressAllocationException,
        NoAddressAvailableException {

        // Check arguments
        checkScope(scope);
        if (ttl > scope.getTTL())
            throw new IllegalArgumentException(
		myResources.getString("TTLtoolarge"));
        if (count < 1) {
            throw new IllegalArgumentException(
		myResources.getString("countLTone"));
        } 
        if (addressesRequested != null) {
            throw new IllegalArgumentException(
		myResources.getString("notSupported"));
        }

        AddressSet addressSet =	(AddressSet) free.get(scope);
        if (count > addressSet.getAddressCount()) {
            throw new AddressAllocationException(
		myResources.getString("notEnough"));
        } 

        Enumeration ranges = addressSet.getAddressRanges();
        Vector leaseRanges = new Vector();
        while ((count > 0) && ranges.hasMoreElements()) {
            AddressRange range = (AddressRange) ranges.nextElement();
            long rangeCount = range.getAddressCount();
            if (rangeCount <= count) {
                leaseRanges.addElement(range);
                count = (int) (count - rangeCount);
            } else {
	        IPv4Address firstAddress = 
		    (IPv4Address) range.getFirstAddress();
	        IPv4Address lastAddress = new IPv4Address(Util.intToInetAddress(
                    firstAddress.toInt() + count - 1));
                AddressRange leaseRange = 
		    new AddressRange(firstAddress, lastAddress);
                leaseRanges.addElement(leaseRange);
                count = 0;
            }
        }
        AddressSet leaseSet = new AddressSet(leaseRanges.elements());
      
        byte [] leaseID = new byte [16];
        random.nextBytes(leaseID);
        addressSet = addressSet.removeAll(leaseSet);
        Lease lease = new StaticAllocatorLease(leaseID, scope, leaseSet,
	    new Date(), -1, this);
	free.put(scope, addressSet);
	leases.put(leaseID, lease);

	// If everything went well, update the configuration.
	writeConfig();
	return (lease);
    }

    /**
     * Check whether a scope value is valid.
     * @param scope the scope value
     * @exception AddressAllocationException if the scope is not valid
     */
    private void checkScope(Scope scope) throws AddressAllocationException {
        // @@@ Should use a Hashtable for more efficiency
        Enumeration scopes = sl.getScopes();
        while (scopes.hasMoreElements()) {
            if (scope.equals((Scope) scopes.nextElement()))
                return;
        }
        throw new AddressAllocationException(
 	    myResources.getString("scopeNotSupported"));
    }

    /**
     * Set the configuration based on a Properties object.
     * @param props the Properties object
     * @exception AddressAllocationException if an exception occurred
     */
    private void setConfig(Properties props) 
	throws AddressAllocationException {

        try {

            // Create new data structures

	    free = new Hashtable();
            leases = new Hashtable();
	    // We'll create sl, the ScopeList in a second.

	    // First, get all the scope properties and create the free lists
	    // and ScopeList from those.

	    int i = 1;
	    boolean done = false;
	    Vector scopes = new Vector();
	    while (!done) {
	        String value = (String) props.get("Scope-" + i);
	        if (value == null)
	    	    done = true;
	        else {
	    	    Scope scope = parseScopePropertyValue(value);
		    Vector freeRanges = new Vector();
		    freeRanges.addElement(scope.getAddresses());
    		    free.put(scope, new AddressSet(freeRanges.elements()));
		    i = i + 1;
	        }
	    }
	    sl = new ScopeList(free.keys());

            // Next get the lease properties and create the lease list

            Enumeration keys = props.keys();
            while (keys.hasMoreElements()) {

		// For each lease property,

                String key = (String) keys.nextElement();
		if ((key.length() < 2) || (key.charAt(0) != 'L'))
		  continue;

                // Create the lease, put it in the lease list,
		// and remove the addresses from the free list

		byte [] leaseID = 
		    BASE64Encoder.decode(key.substring(1).getBytes("UTF8"));
		Lease lease = parseLeasePropertyValueString(
                    (String) props.get(key), leaseID);

		leases.put(leaseID, lease);

		Scope scope = lease.getScope();
		AddressSet addressSet =	(AddressSet) free.get(scope);
		addressSet = addressSet.removeAll(lease.getAddresses());
		free.put(scope, addressSet);
            }
        } catch (AddressAllocationException e) {
            throw e;
        } catch (Exception e) {

            // Remap exceptions to AddressAllocationInternalException

            throw new AddressAllocationInternalException(e);
        }
    }

    /**
     * Parses a scope property value string to make a Scope.
     * 
     * @param value the string from which to make the Scope
     * @return the Scope created from the string
     * @exception AddressAllocationException if a parse error occurred
     */
    private static Scope parseScopePropertyValue(String value)
	throws AddressAllocationException {
	try {
	    StringTokenizer st = new StringTokenizer(value, " \"");
	    AddressRange range = parseAddressRangeString(st.nextToken());
	    int ttl = Integer.parseInt(st.nextToken());
	    ScopeName name = new ScopeName(st.nextToken(), st.nextToken());
	    Vector nameList = new Vector();
	    nameList.addElement(name);
	    return (new Scope(range, ttl, nameList.elements(), name));
        } catch (NoSuchElementException e) {
	    throw new AddressAllocationException(
		myResources.getString("config"));
        } 
    }

    /**
     * Parse a lease property value string to make a Lease.
     * 
     * @param value the String from which to make the Lease
     * @param leaseID the lease identifier for the lease
     * @return the Lease
     * @exception AddressAllocationException if a parse error occurred
     */
    private Lease parseLeasePropertyValueString(String value,
	byte [] leaseID) throws AddressAllocationException {

        int rparenIndex = value.indexOf(')');

        if ((rparenIndex <= 0) || 
	    (rparenIndex >= (value.length() - 1))) {
	    throw new AddressAllocationException(
		myResources.getString("config"));
        } 

        AddressSet addressSet = parseAddressSetString(
            value.substring(0, rparenIndex+1));

        try {
  	    StringTokenizer st = new StringTokenizer(
                value.substring(rparenIndex+2));

	    Date startTime = new Date(Long.parseLong(st.nextToken()));
	    int duration = Integer.parseInt(st.nextToken());
	    AddressRange firstRange =
	        (AddressRange) addressSet.getAddressRanges().nextElement();
	    Scope scope = findScope(firstRange.getFirstAddress());
	    return (new StaticAllocatorLease(leaseID, scope, addressSet,
			       startTime, duration, this));
        } catch (NoSuchElementException e) {
	    throw new AddressAllocationException(
		myResources.getString("config"));
        } 
    }

    /**
     * Finds the Scope that contains a given Address.
     * 
     * @param address the Address to find
     * @return the Scope that contains that Address (null if none)
     * @exception AddressAllocationException if a parse error occurred
     */
    private Scope findScope(Address address) throws AddressAllocationException {
	Enumeration scopes = sl.getScopes();
	while (scopes.hasMoreElements()) {
	    Scope scope = (Scope) scopes.nextElement();
	    if (scope.getAddresses().contains(address))
	        return (scope);
        }
        return (null);
    }

    /**
     * Parses an address range string to make an AddressRange.
     * 
     * @param value the string from which to make the AddressRange
     * @return the AddressRange created from the address range string
     * @exception AddressAllocationException if a parse error occurred
     */
    private static AddressRange parseAddressRangeString(String value)
	throws AddressAllocationException {

	int dashIndex = value.indexOf('-');

	if ((dashIndex <= 0) || (dashIndex >= (value.length() - 1))) {
	    throw new AddressAllocationException(
		myResources.getString("config"));
        } 

        try {
  	  Address firstAddress = new IPv4Address(
              InetAddress.getByName(value.substring(0, dashIndex)));
	  Address lastAddress = new IPv4Address(
              InetAddress.getByName(value.substring(dashIndex+1)));
	  return (new AddressRange(firstAddress, lastAddress));
        } catch (UnknownHostException e) {
	    throw new AddressAllocationException(
		myResources.getString("config"));
        }
    }

    /**
     * Parses an address set string to make an AddressSet.
     * 
     * @param value the String from which to make the AddressSet
     * @return the AddressSet
     * @exception AddressAllocationException if a parse error occurred
     */
    private static AddressSet parseAddressSetString(String value)
	throws AddressAllocationException {

	if (value.length() < 2) {
	    throw new AddressAllocationException(
		myResources.getString("config"));
	}
	value = value.substring(1, value.length() - 1);
	StringTokenizer st = new StringTokenizer(value);
	Vector ranges = new Vector();
	try {
	    while (st.hasMoreTokens())
		ranges.addElement(parseAddressRangeString(st.nextToken()));
	} catch (NoSuchElementException e) {
	    throw new AddressAllocationException(
		myResources.getString("config"));
        } 
        return (new AddressSet(ranges.elements()));
    }

    /**
     * Read the configuration file
     * @exception AddressAllocationException if the request could not be 
     * satisfied
     */
    private void readConfigFromFile() throws AddressAllocationException {
        try {

            // Read the properties from the file

            if (configFile == null) {
                throw new AddressAllocationException(
		    myResources.getString("noFile"));
            } 

            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(configFile);

            try {
                props.load(fis);
            }
            finally {
                fis.close();
            }

            setConfiguration(props);
        } catch (AddressAllocationException e) {
            throw e;
        } catch (Exception e) {

            // Remap exceptions to AddressAllocationInternalException

            throw new AddressAllocationInternalException(e);
        }
    }

    /**
     * Read the configuration from the savedState.
     * @exception AddressAllocationException if the request could not be 
     * satisfied
     */
    private void readConfig() throws AddressAllocationException {
        setConfiguration(savedState);
    }

    /**
     * Write the configuration file.
     * @exception AddressAllocationException if the request could not be 
     * satisfied
     */
    private boolean firstTime = true;

    private void writeConfig() throws AddressAllocationException {
        try {
            savedState = getConfiguration();

            if (configFile != null) {

                // Write the properties to the file
                // @@@ Should probably rename the old version and replace it 
		// if we have trouble (like out of disk space)

                FileOutputStream fos = new FileOutputStream(configFile);

                try {

                    // 
                    // Assume jdk1.2
                    // 

                    savedState.save(fos, 
			"JRMS StaticAllocator Configuration File");
                } catch (NoSuchMethodError e) {

                    // 
                    // Try pre-jdk1.2 method name
                    // 

                    if (firstTime) {
                        firstTime = false;

                        System.out.println(
			    "jdk1.2 not installed.  Trying savedState.save");
                    }

                    savedState.save(fos, 
			"JRMS StaticAllocator Configuration File");
                }
                finally {
                    fos.close();
                }
            }
        } catch (AddressAllocationException e) {
            throw e;
        } catch (Exception e) {

            // Remap exceptions to AddressAllocationInternalException

            throw new AddressAllocationInternalException(e);
        }
    }

    /**
     * A File referring to the configuration file (if any).
     * This field may be null if there is no config file.
     */
    private File configFile;

    /**
     * A Properties object that holds the last committed state.
     * This field may be null during the constructor. After that, it will be 
     * valid.
     */
    private Properties savedState;

    /**
     * Random number generator
     */
    private Random random;

    /**
     * The current ScopeList.
     * This field may be null during the constructor. After that, it will be 
     * valid.
     */
    private ScopeList sl = null;

    /**
     * A Hashtable containing free addresses. The key is the Scope. The value
     * is a Vector of free AddressRanges.
     * This field may be null during the constructor. After that, it will be 
     * valid.
     */
    private Hashtable free = null;

    /**
     * A Hashtable containing leases. The key is the lease id (a byte array).
     * The value is the Lease. This makes it easy to find a given Lease and 
     * modify it.
     * This field may be null during the constructor. After that, it will be 
     * valid.
     */
    private Hashtable leases = null;

    /**
     * Recovery flag. This prevents an infinite loop of failed recovery.
     */
    private boolean inRecovery = false;
    
    /**
     * Internationalization strings
     */
    private static ResourceBundle myResources;
}
