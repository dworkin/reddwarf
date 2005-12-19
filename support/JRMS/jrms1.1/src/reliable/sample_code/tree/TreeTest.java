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
 * TreeTest
 * 
 * Module Description:
 * 
 * This module implements the TreeTest class of the TreeTest application.
 */
package com.sun.multicast.reliable.applications.tree;

import java.awt.*;
import java.awt.event.*;

/**
 * Undocumented Class Declaration.
 * 
 * 
 * @see
 *
 * @author
 */
class TreeTest extends Frame implements ActionListener, ItemListener {
    Button range;
    Button reset;
    Button clear;
    Button startTreeFormation;
    Choice circles;
    TreeCanvas tc;
    Menu menu;
    Choice memberType;
    Choice lanModeChoice;
    boolean lanMode = false;
    Label infoLabel;
    String fileName = null;

    /**
     * Undocumented Class Constructor.
     * 
     * 
     * @param args
     *
     * @see
     */
    TreeTest(String[] args) {
        setTitle("TreeTest");
        setLayout(new FlowLayout());
        setSize(700, 550);

        MenuBar mb = new MenuBar();

        menu = new Menu("File");

        MenuItem m = new MenuItem("Include");

        m.addActionListener(this);
        menu.add(m);

        m = new MenuItem("Exit");

        m.addActionListener(this);
        menu.add(m);
        mb.add(menu);
        setMenuBar(mb);

        range = new Button("Show Range");

        range.addActionListener(this);

        // add(range);

        reset = new Button("Reset");

        reset.addActionListener(this);
        add(reset);

        clear = new Button("Clear");

        clear.addActionListener(this);
        add(clear);

        circles = new Choice();

        circles.add("Circles Shown");
        circles.add("Circles Hidden");
        add(circles);

        lanModeChoice = new Choice();

        lanModeChoice.add("WAN Mode");
        lanModeChoice.add("LAN Mode");
        add(lanModeChoice);

        memberType = new Choice();

        memberType.add("Sender");
        memberType.add("Head");
        memberType.add("Reluctant Head");
        memberType.add("Member");
        add(memberType);

        startTreeFormation = new Button("Start Tree Formation");

        add(startTreeFormation);

        infoLabel = 
            new Label(
		"                                " +
		"                                " +
		"                                            ", 
		Label.LEFT);

        add(infoLabel);
        checkArgs(args);

        tc = new TreeCanvas(700, 550, infoLabel, fileName);

        tc.setBackground(Color.white);
        add(tc);
        setVisible(true);
        lanModeChoice.addItemListener(tc);
        circles.addItemListener(tc);
        memberType.addItemListener(tc);
        startTreeFormation.addActionListener(tc);
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param args
     *
     * @see
     */
    public static void main(String args[]) {
        TreeTest bt = new TreeTest(args);
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

        if (s == "Reset") {
            startTreeFormation.setLabel("Start Tree Formation");
            tc.reset();
            repaint();
        } else if (s == "Clear") {
            startTreeFormation.setLabel("Start Tree Formation");
            tc.clear();
            repaint();
        } else if (s == "Exit") {
            System.exit(1);
        } else if (s == "Show Range") {
            tc.showRange(true);
            range.setLabel("Remove Range");
        } else if (s == "Remove Range") {
            tc.showRange(false);
            range.setLabel("Show Range");
        } else if (s == "Include") {
            tc.includeFromFile(this);
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
    public void itemStateChanged(ItemEvent e) {
        String s = (String) e.getItem();
    }

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @param args
     *
     * @see
     */
    private void checkArgs(String[] args) {

        // look for a file name

        if (args.length > 0) {
            fileName = args[0];
        } 
    }

}

