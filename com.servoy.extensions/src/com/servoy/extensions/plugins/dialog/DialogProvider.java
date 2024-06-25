/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */
package com.servoy.extensions.plugins.dialog;

import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.base.plugins.IMobileDialogProvider;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.Messages;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IRuntimeWindow;
import com.servoy.j2db.plugins.ISmartRuntimeWindow;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.gui.KeyReleaseActionJButton;

/**
 * Scriptable object for dialog plugin
 *
 * @author jblok
 */
@ServoyDocumented(publicName = DialogPlugin.PLUGIN_NAME, scriptingName = "plugins." + DialogPlugin.PLUGIN_NAME)
public class DialogProvider implements IScriptable, IMobileDialogProvider
{
	private final DialogPlugin plugin;

	public DialogProvider(DialogPlugin plugin)
	{
		this.plugin = plugin;
	}

	/**
	 * @param dialogTitle Dialog title.
	 * @param dialogMessage Dialog message.
	 * @param buttonsText Array of button texts.
	 *
	 * @deprecated Replaced by {@link #showWarningDialog(String,String,String[])}.
	 */
	@Deprecated
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public String js_showDialog(String dialogTitle, String dialogMessage, String... buttonsText)//old one
	{
		return showWarningDialog(dialogTitle, dialogMessage, buttonsText);
	}

	/**
	 * @param dialogTitle Dialog title.
	 * @param dialogMessage Dialog message.
	 *
	 * @deprecated Replaced by {@link #showWarningDialog(String,String,String[])}.
	 */
	@Deprecated
	@ServoyClientSupport(ng = false, wc = true, sc = true)
	public String js_showDialog(String dialogTitle, String dialogMessage)//old one
	{
		return showWarningDialog(dialogTitle, dialogMessage, (String[])null);
	}

	private String[] getButtonTexts(Object buttonsText)
	{
		if (buttonsText == null) return null;
		if (buttonsText instanceof String)
		{
			return new String[] { buttonsText.toString() };
		}
		else if (buttonsText.getClass().isArray())
		{
			String[] text = new String[((Object[])buttonsText).length];
			for (int i = 0; i < ((Object[])buttonsText).length; i++)
			{
				text[i] = (String)((Object[])buttonsText)[i];
			}
			return text;
		}
		else
		{
			return new String[] { buttonsText.toString() };
		}
	}

	/**
	 * Shows a message dialog with the specified title, message and a customizable set of buttons.
	 * Returns pressed button text, in case window is closed without pressing any button return value depends on client type.
	 *
	 * @sample
	 * //show dialog
	 * var thePressedButton = plugins.dialogs.showWarningDialog('Title', 'Value not allowed');
	 *
	 * @param dialogTitle Dialog title.
	 * @param dialogMessage Dialog message.
	 *
	 * @return pressed button text
	 */
	public String showWarningDialog(String dialogTitle, String dialogMessage)
	{
		return showWarningDialog(dialogTitle, dialogMessage, (String[])null);
	}

	/**
	* Shows a message dialog with the specified title, message and a customizable set of buttons.
	* Returns pressed button text, in case window is closed without pressing any button return value depends on client type.
	*
	* @sample
	* //show dialog
	* var thePressedButton = plugins.dialogs.showWarningDialog('Title', 'Value not allowed','OK', 'Cancel');
	*
	* @param dialogTitle Dialog title.
	* @param dialogMessage Dialog message.
	* @param buttonsText variable arguments of button texts.
	*
	* @return pressed button text
	*/
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public String showWarningDialog(String dialogTitle, String dialogMessage, String... buttonsText)
	{
		if (plugin.getClientPluginAccess().getApplicationType() == IClientPluginAccess.WEB_CLIENT)
		{
			BrowserDialog.alert(plugin.getClientPluginAccess(), dialogTitle + '\n' + dialogMessage);
			return ((buttonsText != null && buttonsText.length > 0) ? getButtonTexts(buttonsText)[0] : "OK");
		}
		return showDialogEx(dialogTitle, dialogMessage, buttonsText, JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * @clonedesc showErrorDialog(String,String,String[])
	 *
	 * @sample
	 * //show dialog
	 * var thePressedButton = plugins.dialogs.showInfoDialog('Title', 'Value not allowed','OK');
	 *
	 * @param dialogTitle Dialog title.
	 * @param dialogMessage Dialog message.
	 * @param buttonsText variable arguments of button texts.
	 *
	 * @return pressed button text
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public String showInfoDialog(String dialogTitle, String dialogMessage, String... buttonsText)
	{
		if (plugin.getClientPluginAccess().getApplicationType() == IClientPluginAccess.WEB_CLIENT)
		{
			BrowserDialog.alert(plugin.getClientPluginAccess(), dialogTitle + '\n' + dialogMessage);
			return ((buttonsText != null && buttonsText.length > 0) ? getButtonTexts(buttonsText)[0] : "OK");
		}
		return showDialogEx(dialogTitle, dialogMessage, buttonsText, JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Shows a message dialog with the specified title, message and a customizable set of buttons.
	 * Returns pressed button text, in case window is closed without pressing any button return value depends on client type.
	 *
	 * @sample
	 * //show dialog
	 * var thePressedButton = plugins.dialogs.showErrorDialog('Title', 'Value not allowed','OK', 'Cancel');
	 *
	 * @param dialogTitle Dialog title.
	 * @param dialogMessage Dialog message.
	 * @param buttonsText variable arguments of button texts.
	 *
	 * @return pressed button text
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public String showErrorDialog(String dialogTitle, String dialogMessage, String... buttonsText)
	{
		if (plugin.getClientPluginAccess().getApplicationType() == IClientPluginAccess.WEB_CLIENT)
		{
			BrowserDialog.alert(plugin.getClientPluginAccess(), dialogTitle + '\n' + dialogMessage);
			return ((buttonsText != null && buttonsText.length > 0) ? getButtonTexts(buttonsText)[0] : "OK");
		}
		return showDialogEx(dialogTitle, dialogMessage, buttonsText, JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Shows a message dialog with the specified title, message and a customizable set of buttons.
	 * Returns pressed button text, in case window is closed without pressing any button return value depends on client type.
	 *
	 * @sample
	 * //show dialog
	 * var thePressedButton = plugins.dialogs.showQuestionDialog('Title', 'Value not allowed','OK', 'Cancel');
	 *
	 * @param dialogTitle Dialog title.
	 * @param dialogMessage Dialog message.
	 * @param buttonsText variable arguments of button texts.
	 *
	 * @return pressed button text
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public String showQuestionDialog(String dialogTitle, String dialogMessage, String... buttonsText)
	{
		return showDialogEx(dialogTitle, dialogMessage, buttonsText, JOptionPane.QUESTION_MESSAGE);
	}

	/**
	 * Shows a message dialog with the specified title, message and a customizable set of buttons.
	 * Returns pressed button text, in case window is closed without pressing any button return value depends on client type.
	 *
	 * @sample
	 * //show dialog
	 * var thePressedButton = plugins.dialogs.showQuestionDialog('Title', 'Value not allowed');
	 *
	 * @param dialogTitle Dialog title.
	 * @param dialogMessage Dialog message.
	 *
	 * @return pressed button text
	 */
	public String showQuestionDialog(String dialogTitle, String dialogMessage)
	{
		return showQuestionDialog(dialogTitle, dialogMessage, (String[])null);
	}

	private String showDialogEx(String dialogTitle, String dialogMessage, Object buttonsText, int type)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new RuntimeException("Can't use the dialog plugin in a none Swing thread/environment");
		}
		String title = Messages.getString("servoy.general.warning"); //$NON-NLS-1$
		if (dialogTitle != null)
		{
			title = Messages.getStringIfPrefix(dialogTitle);
		}
		String msg = Messages.getString("servoy.general.clickOk"); //$NON-NLS-1$
		if (dialogMessage != null) msg = Messages.getStringIfPrefix(dialogMessage);
		Vector<String> buttons = new Vector<String>();
		if (buttonsText != null)
		{
			for (String text : getButtonTexts(buttonsText))
			{
				if (text != null && !("".equals(text))) //$NON-NLS-1$
				{
					buttons.addElement(Messages.getStringIfPrefix(text));
				}
			}
		}
		Object[] options = new Object[buttons.size()];
		buttons.copyInto(options);
		if (options.length == 0) options = new Object[] { Messages.getString("servoy.button.ok") }; //$NON-NLS-1$

		for (int i = 0; i < options.length; i++)
		{
			KeyReleaseActionJButton b = new KeyReleaseActionJButton();
			b.setText((String)options[i]);
			options[i] = b;
		}

		final JOptionPane pane = new JOptionPane(msg, type, JOptionPane.DEFAULT_OPTION, null, options, options[0]);
		pane.setInitialValue(options[0]);

		ActionListener selectButtonActionListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				pane.setValue(e.getSource());
			}
		};
		for (Object option : options)
		{
			((JButton)option).addActionListener(selectButtonActionListener);
		}
		createAndShowDialog(title, pane);
		Object retValue = pane.getValue();
		if (retValue instanceof JButton)
		{
			retValue = ((JButton)retValue).getText();
		}
		if (retValue != null) return retValue.toString();
		return null;
	}

	/**
	 * @param title
	 * @param pane
	 * @return
	 */
	private void createAndShowDialog(String title, JOptionPane pane)
	{
		IClientPluginAccess access = plugin.getClientPluginAccess();
		IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
		Window currentWindow = null;
		if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
		pane.setComponentOrientation(((currentWindow == null) ? JOptionPane.getRootFrame() : currentWindow).getComponentOrientation());
		JDialog dialog = pane.createDialog(currentWindow, title);
		modalizeDialog(dialog);
		pane.selectInitialValue();
		dialog.setVisible(true);
		dialog.dispose();
	}

	/**
	 * @param dialog
	 */
	private void modalizeDialog(JDialog dialog)
	{
		Object setModalityTypeMethod = null;
		Object modalityDocumentModalValue = null;
		try
		{
			Class< ? > clz = Class.forName("java.awt.Dialog$ModalityType"); //$NON-NLS-1$
			modalityDocumentModalValue = clz.getField("DOCUMENT_MODAL").get(clz); //$NON-NLS-1$
			setModalityTypeMethod = Dialog.class.getMethod("setModalityType", clz); //$NON-NLS-1$
		}
		catch (Exception e)
		{
			setModalityTypeMethod = Boolean.FALSE;
		}

		if (setModalityTypeMethod instanceof Method)
		{
			try
			{
				((Method)setModalityTypeMethod).invoke(dialog, new Object[] { modalityDocumentModalValue });
			}
			catch (Exception e)
			{
				setModalityTypeMethod = Boolean.FALSE;
			}
		}
	}

	/**
	 * Shows a selection dialog, where the user can select an entry from a list of options. Returns the selected entry, or nothing when canceled.
	 *
	 * @sample
	 * //show select,returns nothing when canceled
	 * var selectedValue = plugins.dialogs.showSelectDialog('Select','please select a name','jan','johan','sebastiaan');
	 * //also possible to pass array with options
	 * //var selectedValue = plugins.dialogs.showSelectDialog('Select','please select a name', new Array('jan','johan','sebastiaan'));
	 * @param dialog_title
	 * @param msg
	 * @param options
	 *
	 * @return selected value or null
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public String showSelectDialog(String dialog_title, String msg, String... options)
	{
		Vector<String> buttons = new Vector<String>();
		if (options != null)
		{
			for (String element : options)
			{
				buttons.addElement(element == null ? "" : Messages.getStringIfPrefix(element)); //$NON-NLS-1$
			}
		}
		Object[] optionsCopy = new String[buttons.size()];
		buttons.copyInto(optionsCopy);
		return showSelectDialogEx(dialog_title, msg, optionsCopy);
	}

	/**
	 * @param dialog_title
	 * @param msg
	 * @param options
	 * @return
	 */
	private String showSelectDialogEx(String dialog_title, String msg, Object[] options)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new RuntimeException("Can't use the dialog plugin in a none Swing thread/environment");
		}
		String title = Messages.getString("servoy.general.warning"); //$NON-NLS-1$
		if (dialog_title != null) title = Messages.getStringIfPrefix(dialog_title);
		String message = Messages.getString("servoy.general.clickOk"); //$NON-NLS-1$
		if (msg != null) message = Messages.getStringIfPrefix(msg);
		if (options.length == 0) options = new Object[] { Messages.getString("servoy.button.ok") }; //$NON-NLS-1$
		JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, null, options[0]);
		pane.setWantsInput(true);
		pane.setSelectionValues(options);
		pane.setInitialSelectionValue(options[0]);
		createAndShowDialog(title, pane);
		Object value = pane.getInputValue();
		return (value != JOptionPane.UNINITIALIZED_VALUE ? value.toString() : null);
	}

	/**
	 * Shows a selection dialog, where the user can select an entry from a list of options. Returns the selected entry, or nothing when canceled.
	 *
	 * @sample
	 * //show select,returns nothing when canceled
	 * var selectedValue = plugins.dialogs.showSelectDialog('Select','please select a name','jan','johan','sebastiaan');
	 * //also possible to pass array with options
	 * //var selectedValue = plugins.dialogs.showSelectDialog('Select','please select a name', new Array('jan','johan','sebastiaan'));
	 *
	 * @param dialog_title
	 * @param msg
	 * @param optionArray
	 *
	 * @return selected value or null
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public String showSelectDialog(String dialog_title, String msg, Object[] optionArray)
	{
		Vector<String> buttons = new Vector<String>();
		if (optionArray != null)
		{
			for (Object element : optionArray)
			{
				buttons.addElement(element == null ? "" : Messages.getStringIfPrefix((String)element)); //$NON-NLS-1$
			}
		}
		Object[] options = new Object[buttons.size()];
		buttons.copyInto(options);
		return showSelectDialogEx(dialog_title, msg, options);
	}

	/**
	 * Shows an input dialog where the user can enter data. Returns the entered data, or nothing when canceled.
	 *
	 * @sample
	 * //show input dialog ,returns nothing when canceled
	 * var typedInput = plugins.dialogs.showInputDialog('Specify','Your name');
	 *
	 * @return input text or null
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public String showInputDialog()
	{
		return showInputDialog(null, null, null);
	}

	/**
	 * @clonedesc showInputDialog()
	 * @sampleas showInputDialog()
	 * @param dialog_title
	 * @return input text or null
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public String showInputDialog(String dialog_title)
	{
		return showInputDialog(dialog_title, null, null);
	}

	/**
	 * @clonedesc showInputDialog()
	 * @sampleas showInputDialog()
	 * @param dialog_title
	 * @param msg
	 * @return input text or null
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public String showInputDialog(String dialog_title, String msg)
	{
		return showInputDialog(dialog_title, msg, null);
	}

	/**
	 * @clonedesc showInputDialog()
	 * @sampleas showInputDialog()
	 * @param dialog_title
	 * @param msg
	 * @param initialValue
	 * @return input text or null
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public String showInputDialog(String dialog_title, String msg, String initialValue)
	{
		return showInputDialog(dialog_title, msg, initialValue, "text");
	}

	/**
	 * @clonedesc showInputDialog()
	 * @sampleas showInputDialog()
	 * @param dialog_title
	 * @param msg
	 * @param initialValue
	 * @param inputType the type of the input field, one of: color, date, datetime-local, email, month, number, password, tel, text, time, url, week
	 * @return input text or null
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = false, sc = false)
	public String showInputDialog(String dialog_title, String msg, String initialValue, String inputType)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new RuntimeException("Can't use the dialog plugin in a none Swing thread/environment");
		}
		String title = Messages.getString("servoy.general.warning"); //$NON-NLS-1$
		if (dialog_title != null) title = Messages.getStringIfPrefix(dialog_title);
		String message = Messages.getString("servoy.general.clickOk"); //$NON-NLS-1$
		if (msg != null) message = Messages.getStringIfPrefix(msg);
		String val = null;
		if (initialValue != null) val = initialValue;
		JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, null, val);
		pane.setWantsInput(true);
		pane.setInitialSelectionValue(val);
		createAndShowDialog(title, pane);
		Object value = pane.getInputValue();
		return (value != JOptionPane.UNINITIALIZED_VALUE ? value.toString() : null);
	}
}
