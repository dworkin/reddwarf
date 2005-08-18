package com.sun.gi.comm.routing.test;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import javax.swing.JLabel;
import com.sun.gi.comm.routing.Router;

public class RouterChat extends JFrame{

	private Router router;
	
	private JList foreignUserList;
	private JList localUserList;
	private JDesktopPane desktop;
	
	public RouterChat(){
		Container c = getContentPane();
		c.setLayout(new BorderLayout());
		localUserList = new JList();
		JScrollPane localUserScroll = new JScrollPane(localUserList);
		foreignUserList = new JList();
		JScrollPane foreignUserScroll = new JScrollPane(foreignUserList);
		JPanel listPanel = new JPanel();
		FlowLayout fl = new FlowLayout();
		listPanel.setLayout(fl);
		JPanel localPanel = new JPanel();
		localPanel.setLayout(new BorderLayout());
		localPanel.add(new JLabel("Local Users"),BorderLayout.NORTH);
		localPanel.add(localUserScroll,BorderLayout.CENTER);
		JPanel foreignPanel = new JPanel();
		foreignPanel.setLayout(new BorderLayout());
		foreignPanel.add(new JLabel("Foreign Users"),BorderLayout.NORTH);
		foreignPanel.add(foreignUserScroll,BorderLayout.CENTER);
		listPanel.add(localPanel);
		listPanel.add(foreignPanel);
		c.add(listPanel,BorderLayout.EAST);	
		desktop = new JDesktopPane();
		desktop.setSize(550,480);
		c.add(desktop,BorderLayout.CENTER);
		setSize(640,480);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
		// start router
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new RouterChat();

	}

}
