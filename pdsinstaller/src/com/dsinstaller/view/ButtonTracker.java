package com.dsinstaller.view;

import java.awt.event.ActionEvent;

import javax.swing.JButton;

public class ButtonTracker {
	
	/**
	 * Determines whether or not the mouse was really clicked.
	 * 
	 * @param e The ActionEvent in question.
	 * @param button The JButton in question.
	 * @return
	 */
	static boolean mouseClicked(ActionEvent e, JButton button)
	{
		return button.isEnabled() && e.getSource().equals(button) && button.isVisible();
	}
	
//	static boolean mouseClicked(MouseEvent e, JButton button)
//	{
//		return button.isEnabled() && e.getSource().equals(button) && button.isVisible();
//	}
	

}
