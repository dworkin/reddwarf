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
 * ChannelTester.java
 */
package com.sun.multicast.reliable.channel;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.Random;
import java.util.Vector;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.BadBASE64Exception;
import com.sun.multicast.util.BASE64Encoder;
import com.sun.multicast.util.TestFailedException;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.SessionDoneException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.RMStreamSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.transport.um.UMTransportProfile;

/**
 * A tester for the channel code.  Feel free to expand upon it.
 * 
 * This is not part of the public interface of the channel code. 
 * It's a package-local class.
 */
class ChannelTester implements ChannelChangeListener, 
                               ChannelListChangeListener {

    /**
     * Create a new ChannelTester object. This should only be called 
     * from ChannelTester.main().
     */
    ChannelTester() throws RMException {
        pcm = ChannelManagerFinder.getPrimaryChannelManager(null);
    }

    /**
     * Check the PCM's channel count against our internal count.
     * 
     * @exception java.lang.Exception if the test fails
     */
    void checkCount() throws Exception {

    // if (pcm.getChannelCount() != ourCount)
    // throw new TestFailedException("Channel count wrong");

    }

    /**
     * Get channel with supplied channel name.
     * 
     * @param channelName channel names
     * @return the channel found
     * @exception java.lang.Exception if the test fails
     */
    Channel getChannel(String channelName) throws Exception {
        long[] clist = pcm.getChannelList(channelName, appName);

        if (clist.length > 1) {
            throw new TestFailedException(
		"Found more than one channel with channel name " + channelName);
        } 
        if (clist.length < 1) {
            throw new TestFailedException(
		"Found less than one channel with channel name " + channelName);
        } 

        return (pcm.getChannel(clist[0]));
    }

    /**
     * Create channels with supplied channel names.
     * 
     * @param channelNames channel names
     * @exception java.lang.Exception if the test fails
     */
    void createChannels(String[] channelNames) throws Exception {
        checkCount();

        for (int i = 0; i < channelNames.length; i++) {
            Channel c = pcm.createChannel();

            c.setChannelName(channelNames[i]);
            c.setApplicationName(appName);

            ourCount++;

            checkCount();
        }
    }

    /**
     * Check for channels with supplied channel names.
     * 
     * @param channelNames channel names
     * @exception java.lang.Exception if the test fails
     */
    void checkChannels(String[] channelNames) throws Exception {
        for (int i = 0; i < channelNames.length; i++) {
            Channel c = getChannel(channelNames[i]);

            if (!channelNames[i].equals(c.getChannelName())) {
                throw new TestFailedException("Channel name doesn't match");
            } 
            if (!appName.equals(c.getApplicationName())) {
                throw new TestFailedException("Application name doesn't match");
            } 
        }
    }

    /**
     * Destroy channels with supplied channel names.
     * 
     * @param channelNames channel names
     * @exception java.lang.Exception if the test fails
     */
    void destroyChannels(String[] channelNames) throws Exception {
        checkCount();

        for (int i = 0; i < channelNames.length; i++) {
            getChannel(channelNames[i]).destroy();

            ourCount--;

            checkCount();
        }
    }

    /**
     * Test BASE64 encoding and decoding. If the test fails, throw an exception.
     * 
     * <P> Create a random byte array of length between 0 and 10,000. 
     * Encode and decode it. See if it's the same. Try decoding the random 
     * bytes to see if anything bad happens. Repeat 12 times.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testBASE64() throws Exception {
        Random rand = new Random();

        for (int i = 1; i <= 12; i++) {
            int arraySize = rand.nextInt() & 32767;
            byte bytes[] = new byte[arraySize];
            int j;

            for (j = 0; j < arraySize; j++) {
                bytes[j] = (byte) ((rand.nextInt() & 255) - 128);
            }

            byte processed[] = 
                BASE64Encoder.decode(BASE64Encoder.encode(bytes));

            if (processed.length != arraySize) {
                throw new NullPointerException();
            } 

            for (j = 0; j < arraySize; j++) {
                if (bytes[j] != processed[j]) {
                    throw new NullPointerException();
                } 
            }

            try {
                BASE64Encoder.decode(bytes);
            } catch (BadBASE64Exception e) {}
        }
    }

    /**
     * Test channel limits. If the test fails, print a diagnostic message and 
     * throw an exception.
     * 
     * <P> First, make sure that setting the channel limit below the number of
     * active channels throws a LimitExceededException. Then make sure that 
     * trying to create a channel when the limit has been reached raises a 
     * LimitExceededException.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testChannelLimit() throws Exception {
        Channel c;
        int oldLimit = pcm.getChannelLimit();
        int count = pcm.getChannelCount();

        try {
            pcm.setChannelLimit(count - 1);

            throw new TestFailedException("setting channel limit too low " +
	        "didn't throw an exception");
        } catch (LimitExceededException e) {}

        pcm.setChannelLimit(count);

        try {
            c = pcm.createChannel();

            throw new TestFailedException("creating more channels than the " +
		"limit didn't throw an exception");
        } catch (LimitExceededException e) {}

        pcm.setChannelLimit(oldLimit);
    }

    /**
     * Test channel creation and lookup. If the test fails, throw an exception.
     * 
     * <P> Create two channels. Get them back with getChannelList. 
     * Finally, destroy them.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testChannelCreateAndLookup() throws Exception {
        String[] names = {
            "Happiness", "Sadness"
        };

        createChannels(names);
        checkChannels(names);
        destroyChannels(names);
    }

    /**
     * Test channel duplication. If the test fails, throw an exception.
     * 
     * <P> Create a channel. Duplicate it. Check that the duplicate has a 
     * different channel ID. Remove one and see if the other is still there.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testChannelDuplicate() throws Exception {
        String[] names = {
            "Rhubarb"
        };

        createChannels(names);
        checkChannels(names);

        Channel c = getChannel(names[0]);

        /*
         * This sleep gives some systems time to bump their current
         * system time; otherwise, the test fails (behavior discovered on
         * NT systems.
         */
        try {
            Thread.sleep(100);
        } catch (Exception e) {}

        Channel d = c.duplicate();

        ourCount++;

        checkCount();

        if (c.getChannelID() == d.getChannelID()) {
            throw new TestFailedException("Duplicate channel has same ID");
        } 
        if (c.getCreationTime().equals(d.getCreationTime())) {
            throw new TestFailedException("Duplicate channel has same " +
		"creation time");
        } 

        d.destroy();

        ourCount--;

        checkCount();
        destroyChannels(names);
    }

    /**
     * Test channel advertising. If the test fails, throw an exception.
     * 
     * <P> Create a channel. Set it up. Advertise it. Loop forever (for now).
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testChannelAdvertising() throws Exception {
        String[] names = {
            "Rutabaga\n\r"
        };

        createChannels(names);
        checkChannels(names);

        Channel c = getChannel(names[0]);
        InetAddress address = InetAddress.getByName("224.10.10.10");
        UMTransportProfile tp = new UMTransportProfile(address, 12435);

        tp.setTTL((byte) 1);
        tp.setDataRate((long) 25000);
        c.setTransportProfile(tp);
        c.setAdditionalAdvertisedData("Hi!\n\rHo there!?");
        c.setAdvertisingRequested(true);
        c.setEnabled(true);
        Thread.sleep(30000);
        destroyChannels(names);
    }

    /**
     * Test channel ad reception. If the test fails, throw an exception.
     * 
     * <P> Loop until you see a channel named Rutabaga. Then check that its 
     * additional advertised data is getting through properly.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testChannelAdReceive() throws Exception {
        String[] names = {
            "Rutabaga"
        };

        while (true) {
            long[] clist = pcm.getChannelList(names[0], appName);

            if (clist.length != 0) {
                Channel c = getChannel(names[0]);
                String aad = c.getAdditionalAdvertisedData();

                if ((aad == null) || (!aad.equals("Hi!\n\rHo there!?"))) {
                    throw new TestFailedException(
			"Additional advertised data not getting through");
                } 

                return;
            }

            Thread.sleep(1000);
        }
    }

    /**
     * Test channel resources. If the test fails, throw an exception.
     * 
     * <P> Create a channel. Try to set its advertising address to a 
     * non-multicast address.  This should throw an IOException. 
     * If the channel resource bundle cannot be found (to create the detail 
     * string for the error message), a MissingResourceException will be thrown.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testChannelResources() throws Exception {
        String[] names = {
            "Vanilla"
        };

        createChannels(names);
        checkChannels(names);

        Channel c = getChannel(names[0]);
        InetAddress address = InetAddress.getByName("192.2.2.3");

        try {
            c.setAdvertisementAddress(address);
        } catch (IOException e) {}

        destroyChannels(names);
    }

    /**
     * Test Object methods of LocalChannel and LocalPCM. If the test fails, 
     * throw an exception.
     * 
     * <P> Create a channel. Make sure that the String returned by 
     * Channel.toString includes the channel name. Object.toString doesn't 
     * do this.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testChannelObjectMethods() throws Exception {
        String[] names = {
            "Vanilla"
        };

        createChannels(names);
        checkChannels(names);

        Channel c = getChannel(names[0]);

        if (c.toString().indexOf(names[0]) == -1) {
            throw new TestFailedException(
		"Channel toString does not contain channel name.");
        } 

        destroyChannels(names);
    }

    /**
     * Test channel event notification. If the test fails, throw an exception.
     * 
     * <P> Create a channel. Add a ChannelChangeListener to it. Change the 
     * channel a few times and make sure that the listener is notified properly.
     * If not, throw an exception.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testChannelEvents() throws Exception {
        String[] names = {
            "Strawberry"
        };

        createChannels(names);

        Channel c = getChannel(names[0]);

        changeCount = 0;

        c.addChannelChangeListener(this);
        c.setChannelName("Chocolate");

        if ((changeCount != 1) || 
	    (changeEvent.getChangedField() != 
	    Channel.CHANNEL_FIELD_CHANNEL_NAME)) {

            throw new TestFailedException(
		"Channel events not triggered properly.");
        } 

        c.setChannelName(names[0]);

        if ((changeCount != 2) || (changeEvent.getChangedField() != 
	    Channel.CHANNEL_FIELD_CHANNEL_NAME)) {

            throw new TestFailedException(
		"Channel events not triggered properly.");
        } 

        destroyChannels(names);
    }

    /**
     * Function called when a channel changes and we asked to be notified.
     * @param event a description of the changes
     */
    public void channelChange(ChannelChangeEvent event) {
        changeCount++;
        changeEvent = event;
    }

    /**
     * Test channel list event notification. If the test fails, throw 
     * an exception.
     * 
     * <P> Add a ChannelListChangeListener to the local PCM. Create a channel 
     * and then destroy it. Make sure that the listener is notified properly. 
     * If not, throw an exception.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testChannelListEvents() throws Exception {
        String[] names = {
            "Strawberry"
        };

        addCount = 0;
        removeCount = 0;

        pcm.addChannelListChangeListener(this);
        createChannels(names);

        long chID = pcm.getChannelList(names[0], appName)[0];

        if ((addCount != 1) || (addEvent.getChannelID() != chID)) {
            throw new TestFailedException(
		"Channel add event not triggered properly.");
        } 

        destroyChannels(names);

        if ((removeCount != 1) || (removeEvent.getChannelID() != chID)) {
            throw new TestFailedException(
		"Channel remove event not triggered properly.");
        } 
    }

    /**
     * Function called when a channel is added and we asked to be notified.
     * @param event a description of the addition
     */
    public void channelAdd(ChannelAddEvent event) {
        addCount++;
        addEvent = event;
    }

    /**
     * Function called when a channel is removed and we asked to be notified.
     * @param event a description of the removal
     */
    public void channelRemove(ChannelRemoveEvent event) {
        removeCount++;
        removeEvent = event;
    }

    /**
     * This function tests the Channel Packet IO interface. It creates
     * a channel named "testChannelPacketIO. It then creates an
     * TRAMTransportProfile setting the multicast address, port, ttl,
     * ordered data (don't
     * really care about this parameter except it's the default on a
     * channel and the transport and channel must match on this). The
     * transport profile is then loaded into the channel. The channel
     * start time is set, advertising is enabled, and a new receiver
     * thread is created. Once the receiver is up, the sender socket
     * is created and a single data packet is sent. The sender waits
     * at this point for the receiver to get the packet and resond as
     * to whether the data was correct or not. The sender then cleans
     * up and throws an exception if the test failed.
     */
    public synchronized void testChannelPacketIO() throws Exception {
        String[] names = {
            "testChannelPacketIO"
        };

        System.out.println("Begin Channel Packet IO test");
        createChannels(names);

        Channel channel = getChannel(names[0]);
        InetAddress ia = InetAddress.getByName("224.25.25.25");
        int port = 4321;
        TRAMTransportProfile tp = new TRAMTransportProfile(ia, port);

        tp.setTTL((byte) 1);
        tp.setOrdered(true);

        Date startTime = new Date();

        startTime.setTime(startTime.getTime() + 30000);
        channel.setDataStartTime(startTime);
        channel.setTransportProfile(tp);
        channel.setAdvertisingRequested(true);
        channel.setEnabled(true);

        if (remote) {
	    // Wait for local PCM to hear about the channel thru SAP
            Thread.sleep(10000);      
        } 

        ChannelTestReceiver receiver = new ChannelTestReceiver(appName, 
	    names[0], this);
        RMPacketSocket so = 
            channel.createRMPacketSocket(TransportProfile.SENDER);
        DatagramPacket dp = new DatagramPacket(testData.getBytes(), 
            testData.length());

        so.send(dp);
        wait();
        so.close();
        destroyChannels(names);

        if (!receiverTest) {
            throw new Exception("testChannelPacketIO FAILED");
        } 
    }

    /**
     * The receiver thread calls this method after it receives a
     * packet. The data from the packet is passed in and tested
     * against what was sent, if they match all is well and the test
     * succeeds. If it doesn't match the sender will throw an
     * exception indicating this.
     * 
     * @param data the data received in the packet.
     */
    public synchronized void channelReceiverTestDone(String data) {
        if (data.equals(testData)) {
            receiverTest = true;
        } else {
            System.out.println("ChannelReceiverTest: " + data + " expected " 
                               + testData);
        }

        this.notify();
    }

    /**
     * This function tests the Channel Stream IO interface. It creates
     * a channel named "testChannelStreamIO. It then creates an
     * TRAMTransportProfile setting the multicast address, port, ttl,
     * ordered data (don't
     * really care about this parameter except it's the default on a
     * channel and the transport and channel must match on this). The
     * transport profile is then loaded into the channel. The channel
     * start time is set, advertising is enabled, and a new receiver
     * thread is created. Once the receiver is up, the sender socket
     * is created and a single data buffer is sent. The sender waits
     * at this point for the receiver to get the data and respond as
     * to whether the data was correct or not. The sender then cleans
     * up and throws an exception if the test failed.
     */
    public synchronized void testChannelStreamIO() throws Exception {
        String[] names = {
            "testChannelStreamIO"
        };

        System.out.println("Begin Channel Stream IO test");
        createChannels(names);

        Channel channel = getChannel(names[0]);
        InetAddress ia = InetAddress.getByName("224.25.25.25");
        int port = 4321;
        TRAMTransportProfile tp = new TRAMTransportProfile(ia, port);

        tp.setTTL((byte) 1);
        tp.setOrdered(true);

        Date startTime = new Date();

        startTime.setTime(startTime.getTime() + 30000);
        channel.setDataStartTime(startTime);
        channel.setTransportProfile(tp);
        channel.setAdvertisingRequested(true);
        channel.setEnabled(true);

        if (remote) {
	    // Wait for local PCM to hear about the channel thru SAP
            Thread.sleep(10000);      
        } 

        ChannelTestStreamReceiver receiver = 
            new ChannelTestStreamReceiver(appName, names[0], this);
        ChannelRMStreamSocket so = 
            channel.createRMStreamSocket(TransportProfile.SENDER);
        OutputStream os = so.getOutputStream();

        os.write(testData.getBytes());
        os.flush();
        wait();
        os.close();
        so.close();
        destroyChannels(names);

        if (!receiverTest) {
            throw new Exception("testChannelPacketIO FAILED");
        } 
    }

    /**
     * Test dynamic filters. If the test fails, throw an exception.
     * 
     * <P>Create a channel using unreliable multicast. Add a Byte0Filter to
     * it. Enable it. Create two sockets on it, one to send and one to receive.
     * Send a bunch of packets, some with the first byte equal to 0 and some
     * not. Make sure that most of the ones that don't have 0 arrive and none
     * of the others do.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public synchronized void testDynamicFilters() throws Exception {
        String[] names = {
            "testDynamicFilters"
        };

        System.out.println("Begin dynamic filters test");
        createChannels(names);

        Channel channel = getChannel(names[0]);
        InetAddress ia = InetAddress.getByName("224.25.25.25");
        int port = 4321;
        UMTransportProfile tp = new UMTransportProfile(ia, port);

        tp.setTTL((byte) 1);

        Date startTime = new Date();

        startTime.setTime(startTime.getTime() + 30000);
        channel.setDataStartTime(startTime);
        channel.setTransportProfile(tp);

        Vector filterVec = new Vector(1);

        filterVec.addElement(new Byte0Filter());
        channel.setDynamicFilterList(filterVec);
        channel.setEnabled(true);

        if (remote) {
	    // Wait for local PCM to hear about the channel thru SAP
            Thread.sleep(10000);      
        } 

        // Create the sockets and run the test

        ChannelTestReceiver receiver = new ChannelTestReceiver(appName, 
                names[0], this);
        RMPacketSocket so = 
            channel.createRMPacketSocket(TransportProfile.SENDER);
        byte[] dropData = new byte[10];

        for (int i = 0; i < dropData.length; i++) {
            dropData[i] = 0;
        }

        DatagramPacket dp = new DatagramPacket(dropData, dropData.length);

        so.send(dp);

        dp = new DatagramPacket(testData.getBytes(), testData.length());

        so.send(dp);
        wait();
        so.close();
        destroyChannels(names);

        if (!receiverTest) {
            throw new Exception("testDynamicFilters FAILED");
        } 
    }

    /**
     * Test remote channel management. If the test fails, throw an exception.
     * 
     * <P>Get a reference to a remote PCM. Try to use it. Expect an exception.
     * 
     * @exception java.lang.Exception if the test fails
     */
    public void testRemoteChannels() throws Exception {
        PrimaryChannelManager savedPCM = pcm;

        try {
            pcm = ChannelManagerFinder.getPrimaryChannelManager("");
            remote = true;

            testChannelLimit();
            testChannelCreateAndLookup();
            testChannelDuplicate();
            testChannelResources();

            // toString isn't fancy for remote channels.
            // testChannelObjectMethods();

            testChannelEvents();
            testChannelListEvents();
            testChannelPacketIO();
            testChannelStreamIO();
        }
        finally {
            pcm = savedPCM;
        }
    }

    PrimaryChannelManager pcm;
    int ourCount;
    String appName = "ChannelTester";
    int changeCount = 0;
    ChannelChangeEvent changeEvent;
    int addCount = 0;
    ChannelAddEvent addEvent;
    int removeCount = 0;
    ChannelRemoveEvent removeEvent;
    String testData = "This is the receiver test data";
    boolean receiverTest = false;
    boolean remote = false;

    /**
     * Perform the Channel Test.
     * @param args command line arguments (ignored for now).
     */
    public static void main(String[] args) {
        System.out.println("Channel Test starting.");

        boolean succeeded = true;

        try {
            ChannelTester ct = new ChannelTester();

            if ((args.length > 0) && (args[0].equals("-receive"))) {
                System.out.println("About to test advertising reception.");
                ct.testChannelAdReceive();
            } else if ((args.length > 0) && (args[0].equals("-send"))) {
                System.out.println("About to test advertising.");
                ct.testChannelAdvertising();
            } else if ((args.length > 0) && (args[0].equals("-remote"))) {
                System.out.println(
		    "About to test remote channel management.");
                ct.testRemoteChannels();
            } else {
                ct.testBASE64();
                ct.testChannelLimit();
                ct.testChannelCreateAndLookup();
                ct.testChannelDuplicate();
                ct.testChannelResources();
                ct.testChannelObjectMethods();
                ct.testChannelEvents();
                ct.testChannelListEvents();
                ct.testChannelPacketIO();
                ct.testChannelStreamIO();
                ct.testDynamicFilters();
            }
        } catch (Exception e) {
            e.printStackTrace();

            succeeded = false;
        }

        if (succeeded) {
            System.out.println("Channel Test succeeded.");
        } else {
            System.out.println("Channel Test failed.");
        }
    }

}

/**
 * This class implements a simple TRAM receiver thread. It locates the channel
 * manager, gets a list of channels, and locates the appropriate
 * channel for the given application name and channel name. The
 * transport profile is then retrieved from the channel and a
 * RECEIVE_ONLY socket is created. All of the above is performed in
 * the constructor. The thread is then started and listens for a
 * packet. When a packet is received, it is passed up to the
 * ChannelTester.channelReceiverTestDone method and this thread exits.
 */
class ChannelTestReceiver extends Thread {
    RMPacketSocket so;
    Channel channel;
    ChannelTester source;
    TRAMTransportProfile tp;

    /**
     * Create a new ChannelTestReceiver thread.
     * 
     * @param appName the name of the application
     * @param channelName the name of the channel
     * @param source the object creating this thread
     * 
     * @exception Exception all exceptions are returned to the source
     * prior to firing up the thread. The source must handle the
     * exceptions.
     */
    public ChannelTestReceiver(String appName, String channelName, 
                               ChannelTester source) throws Exception {

        /*
         * Initialize the thread and save the source object.
         */
        super("ChannelTestReceiver");

        this.source = source;

        boolean goodChannel = false;

        /*
         * Get the primary channel manager. Ask it for a list of
         * channel ids matching the channelName and appName specified.
         */
        PrimaryChannelManager pcm = 
            ChannelManagerFinder.getPrimaryChannelManager(null);
        long channelIDs[] = pcm.getChannelList(channelName, appName);

        /*
         * For each channelID in the list, check to see if its start
         * time is in the future. If one is found, use it. If none are
         * found, call the channelReceiverTestDone method with an
         * error String.
         */
        for (int i = 0; i < channelIDs.length; i++) {
            channel = pcm.getChannel(channelIDs[i]);

            if ((channel.getDataStartTime().getTime() 
                    - (new Date().getTime())) > 0) {
                goodChannel = true;

                break;
            }
        }

        if (!goodChannel) {
            source.channelReceiverTestDone("Can't find channel");

            return;
        }

        /*
         * Got a channel. From the channel, extract the transport
         * profile. Since we have no idea whether the transport
         * profile is set up for a receiver or sender, set these
         * fields and reset it in the channel (we must reset it
         * because the channel only gave us a cloned copy of the one
         * it will use for creating the socket.) Create a socket and
         * fire up the receiver thread.
         */
        so = channel.createRMPacketSocket(TransportProfile.RECEIVER);

        setPriority(10);
        start();
    }

    /*
     * This thread simply listens for a packet on the TRAM socket. When
     * the packet comes in, the data is handed up to the ChannelTester
     * for verification. This thread then closes the socket and exits.
     */

    public void run() {
        try {
            DatagramPacket dp = so.receive();

            source.channelReceiverTestDone(new String(dp.getData()));
            so.close();
        } catch (Exception e) {
            source.channelReceiverTestDone("Receiver Test Failed.");
            e.printStackTrace();
        }
    }

}

class ChannelTestStreamReceiver extends Thread {
    ChannelRMStreamSocket so;
    Channel channel;
    ChannelTester source;
    TRAMTransportProfile tp;
    InputStream is;

    /**
     * Create a new ChannelTestReceiver thread.
     * 
     * @param appName the name of the application
     * @param channelName the name of the channel
     * @param source the object creating this thread
     * 
     * @exception Exception all exceptions are returned to the source
     * prior to firing up the thread. The source must handle the
     * exceptions.
     */
    public ChannelTestStreamReceiver(String appName, String channelName, 
                                     ChannelTester source) throws Exception {

        /*
         * Initialize the thread and save the source object.
         */
        super("ChannelTestStreamReceiver");

        this.source = source;

        boolean goodChannel = false;

        /*
         * Get the primary channel manager. Ask it for a list of
         * channel ids matching the channelName and appName specified.
         */
        PrimaryChannelManager pcm = 
            ChannelManagerFinder.getPrimaryChannelManager(null);
        long channelIDs[] = pcm.getChannelList(channelName, appName);

        /*
         * For each channelID in the list, check to see if its start
         * time is in the future. If one is found, use it. If none are
         * found, call the channelReceiverTestDone method with an
         * error String.
         */
        for (int i = 0; i < channelIDs.length; i++) {
            channel = pcm.getChannel(channelIDs[i]);

            if ((channel.getDataStartTime().getTime() 
                    - (new Date().getTime())) > 0) {
                goodChannel = true;

                break;
            }
        }

        if (!goodChannel) {
            source.channelReceiverTestDone("Can't find channel");

            return;
        }

        /*
         * Got a channel. From the channel, extract the transport
         * profile. Since we have no idea whether the transport
         * profile is set up for a receiver or sender, set these
         * fields and reset it in the channel (we must reset it
         * because the channel only gave us a cloned copy of the one
         * it will use for creating the socket.) Create a socket and
         * fire up the receiver thread.
         */
        so = channel.createRMStreamSocket(TransportProfile.RECEIVER);
        is = so.getInputStream();

        setPriority(10);
        start();
    }

    /*
     * This thread simply listens for a packet on the TRAM socket. When
     * the packet comes in, the data is handed up to the ChannelTester
     * for verification. This thread then closes the socket and exits.
     */

    public void run() {
        try {
            byte b[] = new byte[100];
            int len = is.read(b);

            source.channelReceiverTestDone(new String(b, 0, len));
            is.close();
            so.close();
        } catch (Exception e) {
            source.channelReceiverTestDone("Receiver Test Failed.");
            e.printStackTrace();
        }
    }

}

