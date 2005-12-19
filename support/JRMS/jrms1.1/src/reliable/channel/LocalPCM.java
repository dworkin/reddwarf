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
 * LocalPCM.java
 */
package com.sun.multicast.reliable.channel;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.Vector;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnimplementedOperationException;
import com.sun.multicast.advertising.Advertisement;
import com.sun.multicast.advertising.InvalidAdvertisementException;
import com.sun.multicast.advertising.Listener;

/**
 * A local primary channel manager. This is the simplest implementation of 
 * the PrimaryChannelManager interface.  It should be used by applications 
 * that want a simple PCM on their machine for advertising channels and 
 * receiving channel advertisements via SAP.
 * 
 * <P>The list of features that are implemented by remote PCMs, but not local 
 * ones is long.  Several of the key items on that list are access control and 
 * receiver registration.  These unimplemented methods are clearly marked.
 * 
 * <P>This class should only be accessed through the
 * methods of the <code>ChannelManagerFinder</code> class and the 
 * <code>ChannelManager</code> and <code>PrimaryChannelManager</code> 
 * interfaces.
 * 
 * <P>There is only one LocalPCM object per Java VM. To get yours, use
 * <code>ChannelManagerFinder.findPrimaryChannelManager(null)</code>.
 * 
 * @see                         ChannelManager
 * @see                         ChannelManagerFinder
 * @see                         PrimaryChannelManager
 */
class LocalPCM implements PrimaryChannelManager {

    /**
     * Get the LocalPCM object for this Java VM.
     * @return the LocalPCM object for this Java VM
     */
    public static synchronized LocalPCM getLocalPCM() {
        if (theLocalPCM == null) {
            theLocalPCM = new LocalPCM();
        } 

        return (theLocalPCM);
    }

    /**
     * Create a new LocalPCM. This constructor is protected because it should 
     * only be used by getLocalPCM() and possible future subclasses of 
     * LocalPCM().
     */
    protected LocalPCM() {
        ownedChannels = new Hashtable();
        sapChannels = new Hashtable();

        // @@@ Perhaps I should postpone this.

        try {
            listener = Listener.getListener();

            listener.startListening();
        } catch (UnknownHostException e) {
            throw new NullPointerException();
        } catch (IOException e) {
            throw new NullPointerException();
        }
    }

    /**
     * Authenticate with the ChannelManager  *** unimplemented ***
     *
     * @param identity the identity to be established
     * @param key the authorization key to be used
     * @return <code>true</code> if authentication succeeded;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public boolean authenticate(String identity, 
                                String key) throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Get the number of channels accessible to this principal on this 
     * channel manager.
     * @return the number of channels accessible to this principal on this 
     * channel manager
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public int getChannelCount() throws UnauthorizedUserException {
        int channelCount;

        // Must make sure there are no duplicate channel IDs here!

        channelCount = ownedChannels.size() + sapChannels.size();

        // Look for ads that are channels and aren't already in one of 
	// our lists.

        Advertisement adList[] = listener.getAllAdvertisements();

        for (int i = 0; i < adList.length; i++) {
            Advertisement ad = adList[i];

            try {
                if (SAPChannel.adIsChannel(ad) 
                        && (findChannel(SAPChannel.adGetChannelID(ad)) 
                            == null)) {
                    channelCount++;
                } 
            } catch (InvalidAdvertisementException e) {}
        }

        return (channelCount);
    }

    /**
     * Tests if a channel matches a given channel and application name pair.
     * This method is not public because it should only be used by 
     * LocalPCM.getChannelList.
     * @param channelName the actual channel name
     * @param matchChannelName the channel name to match (null matches any)
     * @param applicationName the actual application name
     * @param matchApplicationName the application name to match 
     * (null matches any)
     * @return <code>true</code> if the channel matches the given names;
     * <code>false</code> otherwise
     */
    boolean channelNamesMatch(String channelName, String matchChannelName, 
                              String applicationName, 
                              String matchApplicationName) {
        if ((matchChannelName != null) 
                &&!matchChannelName.equals(channelName)) {
            return (false);
        } 
        if ((matchApplicationName != null) 
                &&!matchApplicationName.equals(applicationName)) {
            return (false);
        } 

        return (true);
    }

    /**
     * Create a SAPChannel based on an Advertisement.
     * This method is not public because it should only be used by the LocalPCM.
     * @param ad the Advertisement
     * @return the channel
     * @exception 
     * com.sun.multicast.reliable.channel.InvalidAdvertisementException if the
     * advertisement is not a valid channel advertisement
     */
    Channel createSAPChannel(Advertisement ad) 
            throws InvalidAdvertisementException {
        SAPChannel schan = SAPChannel.createSAPChannel(ad);

        sapChannels.put(schan.getChannelIDObject(), schan);

        return (schan);
    }

    /**
     * Get an array of channel IDs for all the channels on this
     * channel manager that match the channel and application names given.
     * @param channelName channel name to match (null to match any)
     * @param applicationName application name to match (null to match any)
     * @return an array of channel IDs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs 
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform this 
     * action
     */
    public long[] getChannelList(String channelName, String applicationName) 
            throws RemoteException, RMException, UnauthorizedUserException {
        Hashtable owned;
        Hashtable sap;

        synchronized (this) {
            owned = (Hashtable) ownedChannels.clone();
            sap = (Hashtable) sapChannels.clone();
        }

        Vector matches = new Vector();
        Enumeration ownedEnum = owned.elements();

        while (ownedEnum.hasMoreElements()) {
            LocalChannel c = (LocalChannel) ownedEnum.nextElement();

            if (channelNamesMatch(c.getChannelName(), channelName, 
                                  c.getApplicationName(), applicationName)) {
                matches.addElement(c.getChannelIDObject());
            } 
        }

        Enumeration sapEnum = sap.elements();

        while (sapEnum.hasMoreElements()) {
            SAPChannel c = (SAPChannel) sapEnum.nextElement();

            if (channelNamesMatch(c.getChannelName(), channelName, 
                                  c.getApplicationName(), applicationName)) {
                matches.addElement(c.getChannelIDObject());
            } 
        }

        Advertisement adList[] = listener.getAllAdvertisements();

        for (int i = 0; i < adList.length; i++) {
            Advertisement ad = adList[i];

            if (SAPChannel.adIsChannel(ad)) {
                try {
                    long adChannelID = SAPChannel.adGetChannelID(ad);

                    if (channelNamesMatch(ad.getName(), channelName, 
			ad.getOwner(), applicationName) && 
			(findChannel(adChannelID) == null)) {

                        matches.addElement(new Long(adChannelID));
                    } 
                } catch (InvalidAdvertisementException e) {}
            }
        }

        long[] array = new long[matches.size()];
        int i = 0;

        for (Enumeration matchesEnum = matches.elements(); 
                matchesEnum.hasMoreElements(); /* void */) {
            array[i++] = ((Long) matchesEnum.nextElement()).longValue();
        }

        return (array);
    }

    /**
     * Find the channel with a given channel ID (not looking at ads!).
     * @param the channel ID
     * @return the channel
     */
    synchronized Channel findChannel(long channelID) {
        Long channelIDObject = new Long(channelID);

        if (ownedChannels.containsKey(channelIDObject)) {
            return ((Channel) ownedChannels.get(channelIDObject));
        } 
        if (sapChannels.containsKey(channelIDObject)) {
            return ((Channel) sapChannels.get(channelIDObject));
        } 

        return (null);
    }

    /**
     * Choose an unused channel ID.
     * @return the new channel ID
     */
    long chooseChannelID() {
        long channelID = 0;
        boolean done = false;

        while (!done) {
            channelID = rand.nextLong();

            if (findChannel(channelID) == null) {
                done = true;
            } 
        }

        return (channelID);
    }

    /**
     * Get the Channel that goes with a given channel ID.
     * @param channelID channel ID whose Channel is requested
     * @return the Channel that was requested
     * @exception com.sun.multicast.reliable.channel.ChannelNotFoundException 
     * if there is no Channel with the given ID
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform this 
     * action
     */
    public synchronized Channel getChannel(long channelID) 
            throws UnauthorizedUserException, ChannelNotFoundException, 
                   java.rmi.RemoteException {
        Channel c = findChannel(channelID);

        // First, see if we already have such a channel.

        if (c != null) {
            return (c);
        } 

        // If not, see if we should make one (based on SAP advertisements).

        Advertisement adList[] = listener.getAllAdvertisements();

        for (int i = 0; i < adList.length; i++) {
            Advertisement ad = adList[i];

            try {
                if (SAPChannel.adIsChannel(ad) 
                        && (SAPChannel.adGetChannelID(ad) == channelID)) {
                    return (createSAPChannel(ad));
                } 
            } catch (InvalidAdvertisementException e) {}
        }

        throw new ChannelNotFoundException();
    }

    /**
     * Create a new Channel. The channel is initially disabled, with all fields
     * set to null, 0, or false (except the channel ID, which is initialized to
     * a random value; and
     * the creation time, which is set to the current time).
     * @return the new Channel
     * @exception com.sun.multicast.reliable.channel.LimitExceededException 
     * if the PCM's channel limit has been reached
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform this 
     * action
     */
    public synchronized Channel createChannel() 
            throws LimitExceededException, UnauthorizedUserException {
        if (ownedChannels.size() >= channelLimit) {
            throw new LimitExceededException();
        } 

        long channelID = chooseChannelID();
        LocalChannel ch = new LocalChannel(this, channelID);

        ownedChannels.put(ch.getChannelIDObject(), ch);
        addedChannel(channelID);

        return (ch);
    }
    
    /**
     * Serialize and store a channel in a file.
     * @param channel the channel to be stored in a file
     * @param fileName the name of the file to store the channel
     * @exception java.io.IOException is raised if failure to write to file
     */
    public void fileChannel(Channel channel, String fileName)
    	    throws IOException {
    	FileOutputStream ch = new FileOutputStream(fileName);
        ObjectOutputStream s = new ObjectOutputStream(ch);
        s.writeObject(channel);
        s.flush();
	ch.close();
    } 
    
    /**
     * Read a serialized channel from a file.
     * @param fileName the name of the file to store the channel
     * @return channel read from file
     * @exception java.io.IOException is raised if failure to read from file
     */
    public Channel readChannel(String fileName)
    	    throws java.io.IOException, ClassNotFoundException {
    	FileInputStream in = new FileInputStream(fileName);
        ObjectInputStream s = new ObjectInputStream(in);
        LocalChannel ch = (LocalChannel) s.readObject();
	return (ch);
    } 

    /**
     * Set the maximum number of channels that this PCM can own.
     * @param limit the new channel limit
     * @exception com.sun.multicast.reliable.channel.LimitExceededException 
     * if the PCM already has more channels than the new limit
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform this 
     * action
     */
    public synchronized void setChannelLimit(int limit) 
            throws LimitExceededException, UnauthorizedUserException {
        if (limit < ownedChannels.size()) {
            throw new LimitExceededException();
        }

        channelLimit = limit;
    }

    /**
     * Gets the maximum number of channels that this PCM can own.
     * @returns the channel limit
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform this 
     * action
     */
    public synchronized int getChannelLimit() 
            throws UnauthorizedUserException {
        return (channelLimit);
    }

    /**
     * Adds a principal to the list of secondary channel managers (SCMs) 
     * authorized to communicate with this channel manager *** unimplemented ***
     * 
     * @param principal the principal name to be added
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public void addSCM(String principal) throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Removes a principal from the list of secondary channel managers (SCMs) 
     * authorized to communicate with this channel manager *** unimplemented ***
     * 
     * @param principal the principal name to be removed
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public void removeSCM(String principal) throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Gets the number of secondary channel managers (SCMs) authorized
     * to communicate with this channel manager  *** unimplemented ***
     * 
     * @return the number of secondary channel managers (SCMs) authorized
     * to communicate with this channel manager.
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public int getSCMCount() throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Gets a copy of the list of secondary channel managers (SCMs) authorized
     * to communicate with this channel manager.
     * This list is a snapshot, so it will not be updated if authorizations 
     * change  *** unimplemented ***
     *   
     * @return the list of secondary channel managers (SCMs) authorized
     * to communicate with this channel manager.
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public String[] getSCMList() throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Gets the number of channels owned by this channel manager 
     * *** unimplemented ***
     * 
     * @return the number of channels owned by this channel manager.
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public int getOwnedChannelCount() throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Gets a list of channel IDs for channels owned by this channel manager;
     * this list is a snapshot, so it will not be updated if the channel list 
     * changes  *** unimplemented ***
     * 
     * @return the list of channel ID for channels owned by this channel manager
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public long[] getOwnedChannelList() throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Remove a LocalChannel from the LocalPCM. This package local method 
     * is used by LocalChannel objects when they are being destroyed.
     * @param channel the channel to remove
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform this 
     * action
     */
    synchronized void removeChannel(LocalChannel channel) 
            throws UnauthorizedUserException {
        ownedChannels.remove(channel.getChannelIDObject());
        removedChannel(channel.getChannelID());
    }

    /**
     * Duplicates a <code>LocalChannel</code> on the PCM. Creates a new channel
     * exactly like this one, but with a new channel ID. This package local 
     * method is used by LocalChannel objects when they are asked to duplicate 
     * themselves.
     * @exception com.sun.multicast.reliable.channel.LimitExceededException if 
     * the PCM's channel limit has been reached 
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform this 
     * action
     */
    synchronized LocalChannel duplicateChannel(LocalChannel channel) 
            throws LimitExceededException, UnauthorizedUserException {
        if (ownedChannels.size() >= channelLimit) {
            throw new LimitExceededException();
        } 

        long channelID = chooseChannelID();
        LocalChannel dup = channel.dupYourself(channelID);

        ownedChannels.put(dup.getChannelIDObject(), dup);
        addedChannel(channelID);

        return (dup);
    }

    /**
     * Get a ResourceBundle for channel resources in the default locale.
     * This package local method is used by LocalChannel and LocalPCM objects
     * when they need to get resources.
     * @return a ResourceBundle
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform this 
     * action
     */
    static synchronized ResourceBundle getResources() {
        if (channelResources == null) {
            channelResources = ResourceBundle.getBundle(
		"com.sun.multicast.reliable.channel.resources." +
		"ChannelResources");
        } 

        return (channelResources);
    }

    /**
     * Get the number of registered receivers for the ChannelManager 
     * *** unimplemented ***
     * 
     * @return the number of registered receivers for this ChannelManager
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public int getRegisteredReceiverCount() throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Get an array of principal names for all the registered receivers
     * on this channel manager  *** unimplemented ***
     * 
     * @return an array of principal names
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public String[] getRegisteredReceiverList() throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Get the number of registration failures for the ChannelManager 
     * *** unimplemented ***
     * 
     * @return the number of registration failures for the ChannelManager
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public int getRegistrationFailureCount() throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Sets whether the channel manager is enabled. Disabled channel managers 
     * are not accessible to anyone without administrator access 
     * *** unimplemented ***
     * 
     * @param b if <code>true</code>, enable the channel manager;
     * otherwise, disable it
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public void setEnabled(boolean b) throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Tests whether the channel manager is enabled. Disabled channel managers 
     * are not accessible to anyone without administrator access 
     * *** unimplemented ***
     * 
     * @returns <code>true</code> if the channel manager is enabled;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public boolean isEnabled() throws RMException {
        throw new UnimplementedOperationException();
    }

    /**
     * Add a ChannelListChangeListener to the listener list.
     * @param listener the listener to be added
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform this 
     * action
     */
    public synchronized void addChannelListChangeListener(
	ChannelListChangeListener listener) throws UnauthorizedUserException {

        if (listeners == null) {
            listeners = new Vector();
        } 
        if (!listeners.contains(listener)) {
            listeners.addElement(listener);
        } 
    }

    /**
     * Remove a ChannelListChangeListener from the listener list.
     * @param listener the listener to be removed
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform this 
     * action
     */
    public synchronized void removeChannelListChangeListener(
        ChannelListChangeListener listener) throws UnauthorizedUserException {

        if (listeners == null) {
            return;
        } 

        listeners.removeElement(listener);
    }

    /**
     * Tell listeners about an added channel.
     * @param channelID the channel ID of the new channel
     */
    private void addedChannel(long channelID) {
        if ((listeners == null) || (listeners.isEmpty())) {
            return;
        } 

        ChannelAddEvent cae = new ChannelAddEvent(this, channelID);
        Enumeration le = listeners.elements();

        while (le.hasMoreElements()) {
            ((ChannelListChangeListener) le.nextElement()).channelAdd(cae);
        }
    }

    /**
     * Tell listeners about a removed channel.
     * @param channelID the channel ID of the removed channel
     */
    private void removedChannel(long channelID) {
        if ((listeners == null) || (listeners.isEmpty())) {
            return;
        } 

        ChannelRemoveEvent cre = new ChannelRemoveEvent(this, channelID);
        Enumeration le = listeners.elements();

        while (le.hasMoreElements()) {
            ((ChannelListChangeListener) le.nextElement()).channelRemove(cre);
        }
    }

    private int channelLimit = 256;
    private Hashtable ownedChannels;    // Channels owned by this PCM, 
					// keyed by channelIDObject
    private Hashtable sapChannels;      // Channels received via SAP. 
					// Same keying.
    private Listener listener;
    private static LocalPCM theLocalPCM;
    private static transient ResourceBundle channelResources;
    private static Random rand = new Random();
    private Vector listeners;       // Vector of ChannelListChangeListeners
}
