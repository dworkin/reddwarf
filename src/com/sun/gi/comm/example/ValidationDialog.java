package com.sun.gi.comm.example;

import java.util.*;
import javax.security.auth.callback.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ValidationDialog extends JDialog {
  JPanel panel1 = new JPanel();
  BorderLayout borderLayout1 = new BorderLayout();
  Callback[] cbs;
  Component[] values;

  public ValidationDialog(Frame frame, String title,
                          Callback[] cbs, boolean modal) {
    super(frame, title, modal);
    this.cbs = cbs;
    addWindowListener(new WindowAdapter() {
      public void windowClosed(WindowEvent e){
        setCallbacksFromValues();
      }
    });
    try {
      jbInit();
      pack();
    }
    catch(Exception ex) {
      ex.printStackTrace();
    }
  }

  public ValidationDialog() {
    this(null, "", new Callback[0], false);
  }

  private void jbInit() throws Exception {
    panel1.setLayout(new GridLayout(0,2));
    getContentPane().add(panel1);
    values = new Component[cbs.length];
    for(int i=0;i<cbs.length;i++){
      if (cbs[i] instanceof NameCallback) {
        panel1.add(new JLabel("User Name:"));
        values[i] = new JTextField();
        panel1.add(values[i]);
      } else if (cbs[i] instanceof PasswordCallback) {
        panel1.add(new JLabel("Password:"));
        values[i] = new JTextField();
        panel1.add(values[i]);
      } else if (cbs[i] instanceof TextInputCallback ){
        panel1.add(new JLabel(((TextInputCallback)cbs[i]).getPrompt()));
        values[i] = new JTextField();
        ((JTextField)values[i]).setText(
            ((TextInputCallback)cbs[i]).getDefaultText());
        panel1.add(values[i]);
      } else if (cbs[i] instanceof TextOutputCallback ){
        panel1.add(new JLabel("SYSTEM MESSAGE:"));
        values[i] = new JLabel();
        ((JLabel)values[i]).setText(
            ((TextInputCallback)cbs[i]).getDefaultText());
        panel1.add(values[i]);
      } else if (cbs[i] instanceof ChoiceCallback) {
        panel1.add(new JLabel(((ChoiceCallback)cbs[i]).getPrompt()));
        values[i] = new JList(((ChoiceCallback)cbs[i]).getChoices());
        if (((ChoiceCallback)cbs[i]).allowMultipleSelections()){
          ((JList)values[i]).setSelectionMode(
              ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        } else {
          ((JList)values[i]).setSelectionMode(
              ListSelectionModel.SINGLE_SELECTION);
        }
        panel1.add(values[i]);
      } else if (cbs[i] instanceof ConfirmationCallback) {
        panel1.add(new JLabel(((ConfirmationCallback)cbs[i]).getPrompt()));
        values[i] = new JComboBox(((ConfirmationCallback)cbs[i]).getOptions());
        ((JComboBox)values[i]).setSelectedIndex(
            ((ConfirmationCallback)cbs[i]).getDefaultOption());
        panel1.add(values[i]);
      } else if (cbs[i] instanceof LanguageCallback){
        // if thsi was intended for international use then this could be
        // repalced with a locale value passed in during construction
        ( (LanguageCallback) cbs[i]).setLocale(Locale.US);
        values[i] = null; // placeholder
      }
    }
  }

  public void setCallbacksFromValues(){
    for (int i = 0; i < cbs.length; i++) {
      if (cbs[i] instanceof NameCallback) {
        ( (NameCallback) cbs[i]).setName(
            ( (JTextField) values[i]).getText());
      }
      else if (cbs[i] instanceof PasswordCallback) {
        ( (PasswordCallback) cbs[i]).setPassword(
            ( (JTextField) values[i]).getText().toCharArray());
      }
      else if (cbs[i] instanceof TextInputCallback) {
        ( (TextInputCallback) cbs[i]).setText(
            ( (JTextField) values[i]).getText());
      }
      else if (cbs[i] instanceof TextOutputCallback) {
        // nothing to copy
      }
      else if (cbs[i] instanceof ChoiceCallback) {
        ( (ChoiceCallback) cbs[i]).setSelectedIndexes(
            ( (JList) values[i]).getSelectedIndices());
      }
      else if (cbs[i] instanceof ConfirmationCallback) {
        ( (ConfirmationCallback) cbs[i]).setSelectedIndex(
            ( (JComboBox) values[i]).getSelectedIndex());
      }
      else if (cbs[i] instanceof LanguageCallback) {
        // nothing to copy, already done
      }
    }
  }

  /**
   * getCallbacks
   *
   * @return Callback[]
   */
  public Callback[] getCallbacks() {
    return cbs;
  }
}
