package com.sun.gi.comm.routing.test;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.impl.RouterImpl;
import com.sun.gi.framework.interconnect.impl.LRMPTransportManager;

//@SuppressWarnings("serial")
public class RouterChat extends JFrame{

	private Router router;
	private JDesktopPane desktop;
	
	public RouterChat(){
		Container c = getContentPane();
		c.setLayout(new BorderLayout());	
		desktop = new JDesktopPane();
		desktop.setSize(550,480);
		c.add(desktop,BorderLayout.CENTER);
		JPanel controlPanel = new JPanel();
		controlPanel.setLayout(new GridLayout(0,1));
		JButton newUserButton = new JButton("New User");
		newUserButton.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				newLocalUser();
				
			}
			
		});
		controlPanel.add(newUserButton);
		c.add(controlPanel,BorderLayout.SOUTH);
		setSize(640,480);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
		// start router
		try {
			router = new RouterImpl(new LRMPTransportManager());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		
	}
	protected void newLocalUser() {
		String username = JOptionPane.showInputDialog(this,"Enter User Name");
		RCUser user = new RCUser(router,username);
		desktop.add(user);
		
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new RouterChat();

	}

}
