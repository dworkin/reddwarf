package com.dsinstaller.view;

import java.awt.event.ActionListener;

import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

public class TextTimer implements CaretListener {

	Timer myTimer = null;
	
	public TextTimer(int milliseconds, ActionListener listener) {
		myTimer = new Timer(milliseconds, listener);
		myTimer.setRepeats(false);
	}
	
	public void Stop() {
		myTimer.stop();
	}

	public void caretUpdate(CaretEvent ce) {
		myTimer.stop();
		myTimer.start();
	}
}