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
 * TreeCanvas
 * 
 * Module Description:
 * 
 * This module implements the TreeCanvas class for the
 * TreeTest application.
 */
package com.sun.multicast.reliable.applications.tree;

import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.awt.FileDialog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Properties;
import java.sql.Time;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
class TreeCanvas extends Canvas implements MouseListener, ItemListener, 
                                           ActionListener, 
                                           MouseMotionListener {
    public final static int SENDER = 1;
    public final static int MEMBER = 2;
    public final static int HEAD = 3;
    public final static int RELUCTANT_HEAD = 4;
    public final static int IDLE = 0;
    public final static int TREE = 1;
    public final static int DONE = 3;
    Vector members = null;
    Vector messages = null;
    int member = SENDER;
    int state = IDLE;
    CollisionDetect cd;
    PaintThread pt;
    int assignedPort = 4321;
    Sender theSender = null;
    int potentialHeadCount = 0;
    int ttlInterval = 10;

    // default values for properties

    private static final String DEFAULT_PROPERTIES_FILE = "treeProperties";
    Properties properties = null;
    int ttl = 127;
    int msRate = 2000;
    int beaconRate = 4000;
    int haTTLIncrements = 10;
    int haTTLLimit = 127;
    int msTTLIncrements = 20;
    int haInterval = 4000;
    int maxMembers = 32;
    int helloInterval = 4000;
    int treeFormationPreference = TRAMTransportProfile.TREE_FORM_HAMTHA;

    // statistics

    String loadFileName = null;
    Date startTime = null;
    Date affiliationTime = null;
    long affiliationMinutes = 0;
    long affiliationSeconds = 0;
    long multicastMessageCount = 0;
    long discardedMulticastMessageCount = 0;
    long unicastMessageCount = 0;
    int treeDepth = 0;
    int activeHeadCount = 0;
    int unaffiliatedHeadCount = 0;
    boolean showCircles = true;
    boolean lanMode = false;
    Members lanLeader = null;
    Dimension d = new Dimension(10, 10);
    Label infoLabel;

    /**
     * Undocumented Class Constructor.
     * 
     * 
     * @param width
     * @param height
     * @param infoLabel
     * @param loadFileName
     *
     * @see
     */
    TreeCanvas(int width, int height, Label infoLabel, String loadFileName) {
        setSize(width, height);

        members = new Vector();
        messages = new Vector();

        addMouseListener(this);

        cd = new CollisionDetect(this);
        this.infoLabel = infoLabel;
        this.loadFileName = loadFileName;

        // pt = new PaintThread(this, 100);

        if (loadFileName != null) {
            try {
                loadFile(loadFileName);
            } catch (FileNotFoundException e) {
                System.out.println("File not found: " + loadFileName);
            }
        } 
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param m
     *
     * @see
     */
    void addMessage(MulticastMessages m) {

        // if there's already a message from this node
        // on the queue, drop this one

        int msgType = m.getType();

        if ((messages.size() > 20) && (msgType != MulticastMessages.DATA) 
                && (m.getSource().hasAMessageEnqueued())) {
            discardedMulticastMessageCount++;

            return;
        }

        multicastMessageCount++;

        messages.addElement(m);

        if ((msgType != MulticastMessages.DATA) 
                && (msgType != MulticastMessages.BEACON)) {
            m.getSource().messageEnqueued(true);
        } 

        cd.wake();
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param m
     *
     * @see
     */
    void removeMessage(MulticastMessages m) {
        try {
            messages.removeElement(m);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param setting
     *
     * @see
     */
    void showCircles(boolean setting) {
        showCircles = setting;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param g
     *
     * @see
     */
    public void update(Graphics g) {
        paint(g);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param g
     *
     * @see
     */
    public void paint(Graphics g) {
        Image bufferedImage = createImage(getSize().width, getSize().height);
        Vector m = (Vector) members.clone();
        Vector e = (Vector) messages.clone();
        Graphics bg = bufferedImage.getGraphics();

        for (int i = 0; i < m.size(); i++) {
            Members member = (Members) m.elementAt(i);

            member.draw(bg);
        }

        if (showCircles) {
            for (int i = 0; i < e.size(); i++) {
                MulticastMessages message = 
                    (MulticastMessages) e.elementAt(i);

                message.draw(bg);
            }
        }
        if (m.size() > 0) {
            drawStats(bg, e.size());
        } 

        drawKey(bg);

        try {
            g.drawImage(bufferedImage, 0, 0, getSize().width, 
                        getSize().height, null);
        } catch (NullPointerException ex) {}

        bg.dispose();

        bufferedImage = null;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param bg
     * @param queueDepth
     *
     * @see
     */
    void drawStats(Graphics bg, int queueDepth) {
        try {
            int xOffset = 40;
            int yOffset = 170;

            bg.setColor(Color.black);

            if (loadFileName != null) {
                bg.drawString(loadFileName, xOffset, yOffset);
            } 

            yOffset += 15;

            if (startTime != null) {
                Date currentTime = new Date();
                long elapsedTime = 
                    (currentTime.getTime() - startTime.getTime()) / 1000;
                long minutes = elapsedTime / 60;
                long seconds = elapsedTime % 60;

                if (seconds < 10) {
                    bg.drawString(new String("Elapsed time: " + minutes + 
			":0" + seconds), xOffset, yOffset += 15);
                } else {
                    bg.drawString(new String("Elapsed time: " + minutes + ":" +
		        seconds), xOffset, yOffset += 15);
                }
            } else {
                bg.drawString("Elapsed time: ", xOffset, yOffset += 15);
            }
            if (affiliationTime != null) {
                if (affiliationSeconds < 10) {
                    bg.drawString(new String("Affiliation time: " + 
			affiliationMinutes + ":0" + 
			affiliationSeconds), xOffset, yOffset += 15);
                } else {
                    bg.drawString(new String("Affiliation time: " + 
			affiliationMinutes + ":" + affiliationSeconds), 
			xOffset, yOffset += 15);
                }
            } else {
                bg.drawString("Affiliation time: ", xOffset, yOffset += 15);
            }

            yOffset += 15;

            bg.drawString(new String("Nodes: " + members.size()), xOffset, 
		yOffset += 15);
            bg.drawString(new String("Active Heads: " + activeHeadCount), 
                xOffset, yOffset += 15);
            bg.drawString(new String("Tree Depth: " + treeDepth), xOffset, 
                yOffset += 15);
            bg.drawString(new String("Unaffiliated Members: " + 
		unaffiliatedHeadCount), xOffset, yOffset += 15);

            yOffset += 15;

            bg.drawString(new String("Multicast Messages: " + 
		multicastMessageCount), xOffset, yOffset += 15);
            bg.drawString(new String("Unicast Messages:   " + 
		unicastMessageCount), xOffset, yOffset += 15);
            bg.drawString(new String("Discarded Messages:   " + 
		discardedMulticastMessageCount), xOffset, yOffset += 15);
            bg.drawString(new String("Queue Depth: " + queueDepth), xOffset, 
		yOffset += 15);

            xOffset = 520;
            yOffset = 210;

            bg.drawString(new String("ttl = " + ttl), xOffset, yOffset);
            bg.drawString(new String("msRate = " + msRate), xOffset, 
                          yOffset += 15);
            bg.drawString(new String("helloRate = " + helloInterval), 
                          xOffset, yOffset += 15);
            bg.drawString(new String("beaconRate = " + beaconRate), xOffset, 
                          yOffset += 15);
            bg.drawString(new String("haInterval = " + haInterval), xOffset, 
                          yOffset += 15);
            bg.drawString(new String("maxMembers = " + maxMembers), xOffset, 
                          yOffset += 15);
            bg.drawString(new String("haTTLIncrements = " + haTTLIncrements), 
                          xOffset, yOffset += 15);
            bg.drawString(new String("haTTLLimit = " + haTTLLimit), xOffset, 
                          yOffset += 15);
            bg.drawString(new String("msTTLIncrements = " + msTTLIncrements), 
                          xOffset, yOffset += 15);
            bg.drawString(new String("TTLInterval = " + ttlInterval), 
                          xOffset, yOffset += 15);

            switch (treeFormationPreference) {

            case TRAMTransportProfile.TREE_FORM_HA: 
                bg.drawString(new String("Tree Formation = HA"), xOffset, 
                              yOffset += 15);

                break;

            case TRAMTransportProfile.TREE_FORM_HAMTHA: 
                bg.drawString(new String("Tree Formation = HAMTHA"), xOffset, 
                              yOffset += 15);

                break;

            case TRAMTransportProfile.TREE_FORM_MTHA: 
                bg.drawString(new String("Tree Formation = MTHA"), xOffset, 
                              yOffset += 15);

                break;
            }
        } catch (ArrayIndexOutOfBoundsException ex) {}
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param bg
     *
     * @exception ArrayIndexOutOfBoundsException
     *
     * @see
     */
    void drawKey(Graphics bg) throws ArrayIndexOutOfBoundsException {
        int xOffset = 200;
        int yOffset = 300;

        bg.setColor(Color.green.darker());
        bg.drawString("---- ", xOffset, yOffset += 15);
        bg.setColor(Color.black);
        bg.drawString("Beacon", xOffset + 36, yOffset);
        bg.setColor(Color.blue);
        bg.drawString("---- ", xOffset, yOffset += 15);
        bg.setColor(Color.black);
        bg.drawString("HA", xOffset + 36, yOffset);
        bg.setColor(Color.red);
        bg.drawString("---- ", xOffset, yOffset += 15);
        bg.setColor(Color.black);
        bg.drawString("MS", xOffset + 36, yOffset);
        bg.setColor(Color.orange);
        bg.drawString("---- ", xOffset, yOffset += 15);
        bg.setColor(Color.black);
        bg.drawString("Hello", xOffset + 36, yOffset);
        bg.drawString("---- Data", xOffset, yOffset += 15);

        xOffset = 310;
        yOffset = 285;

        bg.setColor(Color.red);
        bg.fillOval(xOffset - d.width / 2, (yOffset + 10) - d.height / 2, 
                    d.width, d.height);
        bg.setColor(Color.black);
        bg.drawString("Sender", xOffset + 10, yOffset += 15);
        bg.fillOval(xOffset - d.width / 2, (yOffset + 10) - d.height / 2, 
                    d.width, d.height);
        bg.drawString("Head", xOffset + 10, yOffset += 15);
        bg.setColor(Color.darkGray);
        bg.fillOval(xOffset - d.width / 2, (yOffset + 10) - d.height / 2, 
                    d.width, d.height);
        bg.setColor(Color.black);
        bg.drawString("Head w/ members", xOffset + 10, yOffset += 15);
        bg.setColor(Color.blue);
        bg.fillOval(xOffset - d.width / 2, (yOffset + 10) - d.height / 2, 
                    d.width, d.height);
        bg.setColor(Color.black);
        bg.drawString("Reluctant Head", xOffset + 10, yOffset += 15);
        bg.setColor(Color.cyan);
        bg.fillOval(xOffset - d.width / 2, (yOffset + 10) - d.height / 2, 
                    d.width, d.height);
        bg.setColor(Color.black);
        bg.drawString("Reluctant Head w/ members", xOffset + 10, 
                      yOffset += 15);
        bg.setColor(Color.green.darker());
        bg.fillOval(xOffset - d.width / 2, (yOffset + 10) - d.height / 2, 
                    d.width, d.height);
        bg.setColor(Color.black);
        bg.drawString("Member", xOffset + 10, yOffset += 15);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     *
     * @see
     */
    public void mouseReleased(MouseEvent e) {}

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     *
     * @see
     */
    public void itemStateChanged(ItemEvent e) {
        String s = (String) e.getItem();

        if (s == "Sender") {
            member = SENDER;
        } else if (s == "Head") {
            member = HEAD;
        } else if (s == "Reluctant Head") {
            member = RELUCTANT_HEAD;
        } else if (s == "Member") {
            member = MEMBER;
        } else if (s == "Circles Hidden") {
            showCircles(false);
            repaint();
        } else if (s == "Circles Shown") {
            showCircles(true);
            repaint();
        } else if (s == "LAN Mode") {
            lanMode = true;
        } else if (s == "WAN Mode") {
            lanLeader = null;
            lanMode = false;
        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     *
     * @see
     */
    public void actionPerformed(ActionEvent e) {
        String s = e.getActionCommand();

        if (s == "Start Tree Formation") {
            unaffiliatedHeadCount = members.size() - 1;

            // note the time

            startTime = new Date();

            if (theSender != null) {
                Button b = (Button) e.getSource();

                b.setLabel("Send Data");

                int i;

                for (i = 0; i < members.size(); i++) {
                    Members member = (Members) members.elementAt(i);

                    member.startTree();
                }

                cd.setTTLIncrement(ttlInterval);
            }
        } else if (s == "Send Data") {
            if (theSender != null) {
                theSender.startData();
            }
        } else {
            System.out.println(s);
        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    int getTTLIncrement() {
        return ttlInterval;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    Vector getMembers() {
        Vector m = (Vector) members.clone();

        return m;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    Vector getMessages() {
        Vector m = (Vector) messages.clone();

        return m;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    void clear() {
        reset();
        members.removeAllElements();
        paint(this.getGraphics());
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    void reset() {
        int i;

        for (i = 0; i < members.size(); i++) {
            Members member = (Members) members.elementAt(i);

            member.reset();
        }

        messages.removeAllElements();

        potentialHeadCount = 0;
        affiliationTime = null;
        startTime = null;
        theSender = null;
        loadFileName = null;
        affiliationMinutes = 0;
        affiliationSeconds = 0;
        multicastMessageCount = 0;
        unicastMessageCount = 0;
        activeHeadCount = 0;
        treeDepth = 0;

        paint(this.getGraphics());
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param b
     *
     * @see
     */
    void showRange(boolean b) {
        Vector m = (Vector) members.clone();

        for (int i = 0; i < m.size(); i++) {
            Members member = (Members) m.elementAt(i);

            member.showRange(b);
        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param port
     *
     * @return
     *
     * @see
     */
    Point findPoint(int port) {
        Vector m = (Vector) members.clone();

        for (int i = 0; i < m.size(); i++) {
            Members member = (Members) m.elementAt(i);

            if (member.getPort() == port) {

                // found it

                return (member.getLocation());
            }
        }

        return null;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param port
     *
     * @return
     *
     * @see
     */
    Members findMember(int port) {
        Vector m = (Vector) members.clone();

        for (int i = 0; i < m.size(); i++) {
            Members member = (Members) m.elementAt(i);

            if (member.getPort() == port) {

                // found it

                return (member);
            }
        }

        return null;
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param frame
     *
     * @see
     */
    void includeFromFile(Frame frame) {

        // save the lan/wan mode setting

        boolean currentLanMode = lanMode;

        // add contents of a file

        FileDialog fileDialog;

        try {
            fileDialog = new FileDialog(frame, "Tree Configuration File");

            fileDialog.setDirectory("~/tree");
            fileDialog.setVisible(true);
            fileDialog.setMode(fileDialog.LOAD);
            fileDialog.setSize(500, 300);

            if ((loadFileName = fileDialog.getFile()) == null) {
                return;
            } 

            String fileName = 
                fileDialog.getDirectory().concat(fileDialog.getFile());

            fileDialog.dispose();
            loadFile(fileName);
        } catch (FileNotFoundException ex) {
            System.out.println("file not found");
        }

        // restore the lan/wan mode

        if ((lanMode = currentLanMode) == false) {
            lanLeader = null;
        } 
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param fileName
     *
     * @exception FileNotFoundException
     *
     * @see
     */
    void loadFile(String fileName) throws FileNotFoundException {
        FileReader configFileReader = null;
        BufferedReader configFile = null;

        configFileReader = new FileReader(fileName);
        configFile = new BufferedReader(configFileReader);

        findProperties(fileName);

        try {
            configFile.ready();

            // read a line of config

            String configLine;

            while ((configLine = configFile.readLine()) != null) {
                int member = 0;
                int x = 0;
                int y = 0;

                // pick up the type character

                switch (configLine.charAt(0)) {

                case 'S': {
                    member = SENDER;

                    break;
                }

                case 'H': {
                    member = HEAD;

                    break;
                }

                case 'M': {
                    member = MEMBER;

                    break;
                }

                case 'R': {
                    member = RELUCTANT_HEAD;

                    break;
                }

                case 'L': {
                    if (lanMode == true) {
                        lanMode = false;
                        lanLeader = null;
                    } else {
                        lanMode = true;
                    }
                }
                }

                if (configLine.charAt(0) == 'L') {
                    continue;
                } 
                if (member == 0) {
                    break;
                } 
                if (configLine.length() < 5) {
                    break;
                } 

                // skip the space

                if (configLine.charAt(1) != ' ') {
                    return;
                } 

                // now pick up x and y in strings

                char xArray[] = new char[10];
                char yArray[] = new char[10];
                int yIndex;

                if (((yIndex = configLine.indexOf(" ", 2)) > 5) 
                        || (yIndex == -1)) {
                    break;
                } 

                configLine.getChars(2, yIndex, xArray, 0);
                configLine.getChars(yIndex + 1, configLine.length(), yArray, 
                                    0);

                String xString = new String(xArray, 0, yIndex - 2);
                String yString = new String(yArray, 0, 
                                            (configLine.length() 
                                             - (yIndex + 1)));

                x = Integer.parseInt(xString);
                y = Integer.parseInt(yString);

                addNode(member, x, y);

            // paint(this.getGraphics());

            }

            configFile.close();
        } catch (IOException ex) {
            System.out.println("file not ready to be read");
        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     *
     * @see
     */
    public void mouseClicked(MouseEvent e) {}

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     *
     * @see
     */
    public void mouseEntered(MouseEvent e) {}

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     *
     * @see
     */
    public void mouseDragged(MouseEvent e) {}

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     *
     * @see
     */
    public void mouseMoved(MouseEvent e) {

        // find the closest member we just passed over

        Point mouseLocation = e.getPoint();
        Vector m = (Vector) members.clone();
        int distance = 0;
        Point memberLocation = null;
        Members closestMember = null;
        Point closestMemberLocation = null;

        for (int i = 0; i < m.size(); i++) {
            Members member = (Members) m.elementAt(i);

            memberLocation = member.getLocation();

            if (memberLocation.equals(mouseLocation)) {
                setInfo(member, memberLocation);

                return;
            } else {
                int newDistance = Math.abs(memberLocation.x - mouseLocation.x) 
                                  + Math.abs(memberLocation.y 
                                             - mouseLocation.y);

                // see if it is the closest so far

                if ((distance == 0) || (newDistance < distance)) {
                    distance = newDistance;
                    closestMember = member;
                    closestMemberLocation = memberLocation;
                }
            }
        }

        setInfo(closestMember, closestMemberLocation);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param member
     * @param location
     *
     * @see
     */
    void setInfo(Members member, Point location) {
        String infoString = new String();

        switch (member.getType()) {

        case Members.SENDER: {
            infoString = infoString.concat("Sender  ");

            break;
        }

        case Members.HEAD: {
            infoString = infoString.concat("Head  ");

            break;
        }

        case Members.MEMBER: {
            infoString = infoString.concat("Member  ");

            break;
        }

        case Members.RELUCTANT_HEAD: {
            infoString = infoString.concat("Reluctant Head  ");

            break;
        }
        }

        if (member.isLanLeader()) {
            infoString = infoString.concat(" LAN leader  ");
        } else if (member.getLanLeader() != null) {
            infoString = infoString.concat(" Lan member  ");
        } 

        infoString = infoString.concat(new String(" Level " 
                                                  + member.getLevel()));
        infoString = infoString.concat(new String("   " 
                                                  + member.getMemberCount() 
                                                  + " members  "));
        infoString = infoString.concat(" x = " + location.x + " y = " 
                                       + location.y);

        infoLabel.setText(infoString);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     *
     * @see
     */
    public void mouseExited(MouseEvent e) {}

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param e
     *
     * @see
     */
    public void mousePressed(MouseEvent e) {
        addNode(member, e.getX(), e.getY());
        paint(this.getGraphics());
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param type
     * @param x
     * @param y
     *
     * @see
     */
    private void addNode(int type, int x, int y) {

        // check that this member is not right on top of a previous one

        boolean duplicate = false;
        Point xyPoint = new Point(x, y);

        for (int i = 0; i < members.size(); i++) {
            Members member = (Members) members.elementAt(i);

            if (member.getLocation().equals(xyPoint)) {
                duplicate = true;

                System.out.println("duplicate found:  x = " + x + "  y = " 
                                   + y);

                break;
            }
        }

        if (++assignedPort < 0) {
            System.out.println("that's enough nodes!!!");
        } 
        if ((!duplicate) && (assignedPort > 0)) {
            Members thisMember = null;

            switch (type) {

            case SENDER: {
                theSender = new Sender(this, x, y, assignedPort, ttl, msRate, 
                                       beaconRate, haTTLIncrements, 
                                       haTTLLimit, haInterval, helloInterval, 
                                       maxMembers, treeFormationPreference);
                thisMember = theSender;

                members.addElement(theSender);

                potentialHeadCount++;

                addMouseMotionListener(this);

                break;
            }

            case HEAD: {
                thisMember = new Head(this, x, y, assignedPort, ttl, msRate, 
                                      haTTLIncrements, haTTLLimit, 
                                      msTTLIncrements, haInterval, 
                                      helloInterval, maxMembers);

                members.addElement(thisMember);

                potentialHeadCount++;

                break;
            }

            case MEMBER: {
                thisMember = new Member(this, x, y, assignedPort, ttl, 
                                        msRate, msTTLIncrements, 
                                        helloInterval);

                members.addElement(thisMember);

                break;
            }

            case RELUCTANT_HEAD: {
                thisMember = new ReluctantHead(this, x, y, assignedPort, ttl, 
                                               msRate, haTTLIncrements, 
                                               haTTLLimit, msTTLIncrements, 
                                               haInterval, helloInterval, 
                                               maxMembers);

                members.addElement(thisMember);

                potentialHeadCount++;

                break;
            }
            }

            if ((lanMode) && (lanLeader == null)) {
                thisMember.setLanLeader(true);

                lanLeader = thisMember;
            }
        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param fileName
     *
     * @see
     */
    void findProperties(String fileName) {
        properties = new Properties();

        int dotIndex = fileName.indexOf('.');

        if (dotIndex != -1) {
            String propFileName = new String(fileName.substring(0, 
                    dotIndex).concat(".tpr"));

            if (new File(propFileName).exists()) {
                loadProperties(propFileName);

                return;
            }
        }

        // try for the default file

        if (new File(DEFAULT_PROPERTIES_FILE).exists()) {
            loadProperties(DEFAULT_PROPERTIES_FILE);
        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param propFileName
     *
     * @see
     */
    void loadProperties(String propFileName) {
        File propFile = new File(propFileName);

        if (propFile.exists() &&!propFile.isFile()) {
            System.out.println(propFileName + " not a normal file.");

            return;
        }

        try {
            properties.load(new FileInputStream(propFileName));
        } catch (FileNotFoundException e) {
            System.out.println("Properties file " + propFileName 
                               + " not available or readable.");

            return;
        } catch (SecurityException e) {
            System.out.println("Properties file " + propFileName 
                               + " not readable.");

            return;
        } catch (IOException e) {
            System.out.println(
		"IO error while trying to read Properties file " 
                + propFileName + ".");

            return;
        }

        // now, fill in the fields

        try {
            ttl = Integer.parseInt(properties.getProperty("tree.ttl"));
        } catch (java.lang.NumberFormatException e) {}
        try {
            beaconRate = 
                Integer.parseInt(properties.getProperty("tree.beaconRate"));
        } catch (java.lang.NumberFormatException e) {}
        try {
            haTTLIncrements = 
                Integer.parseInt(properties.getProperty(
		"tree.haTTLIncrements"));
        } catch (java.lang.NumberFormatException e) {}
        try {
            msTTLIncrements = 
                Integer.parseInt(properties.getProperty(
		"tree.msTTLIncrements"));
        } catch (java.lang.NumberFormatException e) {}
        try {
            haInterval = 
                Integer.parseInt(properties.getProperty("tree.haInterval"));
        } catch (java.lang.NumberFormatException e) {}
        try {
            haTTLLimit = 
                Integer.parseInt(properties.getProperty("tree.haTTLLimit"));
        } catch (java.lang.NumberFormatException e) {}
        try {
            maxMembers = 
                Integer.parseInt(properties.getProperty("tree.maxMembers"));
        } catch (java.lang.NumberFormatException e) {}
        try {
            msRate = Integer.parseInt(properties.getProperty("tree.msRate"));
        } catch (java.lang.NumberFormatException e) {}
        try {
            helloInterval = 
                Integer.parseInt(properties.getProperty(
		"tree.helloInterval"));
        } catch (java.lang.NumberFormatException e) {}

        if (properties.getProperty("tree.treeFormationPreference").equals(
	    "MTHA")) {

            treeFormationPreference = TRAMTransportProfile.TREE_FORM_MTHA;
        } 
        if (properties.getProperty("tree.treeFormationPreference").equals(
	    "HA")) {

            treeFormationPreference = TRAMTransportProfile.TREE_FORM_HA;
        } 
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    void countAffiliation() {

        // see if all members are affiliated

        Vector m = (Vector) members.clone();

        for (int i = 0; i < m.size(); i++) {
            Members member = (Members) m.elementAt(i);

            if (member.getHead() == null) {

                // not done yet

                return;
            } 
        }

        if (affiliationTime == null) {
            affiliationTime = new Date();

            long elapsedTime = 
                (affiliationTime.getTime() - startTime.getTime()) / 1000;

            affiliationMinutes = elapsedTime / 60;
            affiliationSeconds = elapsedTime % 60;

        // now stop the HAs by sending a data packet
        // theSender.startData();

        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    void recordTreeDepth() {

        // figure out the tree depth

        Vector m = (Vector) members.clone();

        treeDepth = 0;
        activeHeadCount = 0;
        unaffiliatedHeadCount = 0;

        for (int i = 0; i < m.size(); i++) {
            Members member = (Members) m.elementAt(i);

            if (member.getLevel() > treeDepth) {
                treeDepth = member.getLevel();
            } 
            if (member.getMemberCount() != 0) {
                activeHeadCount++;
            } 
            if (member.getHead() == null) {
                unaffiliatedHeadCount++;
            } 
        }
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @see
     */
    void incUnicastMessageCount() {
        unicastMessageCount++;
    }

}

