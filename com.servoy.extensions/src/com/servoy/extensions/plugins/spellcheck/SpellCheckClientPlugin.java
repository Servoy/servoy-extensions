/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2011 Servoy BV

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

package com.servoy.extensions.plugins.spellcheck;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.JFrame;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.JTextComponent;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IIconProvider;
import com.servoy.j2db.plugins.IRuntimeWindow;
import com.servoy.j2db.plugins.ISmartRuntimeWindow;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.preference.PreferencePanel;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.smart.dataui.DataField;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;
import com.servoy.j2db.util.toolbar.IToolbarPanel;
import com.servoy.j2db.util.toolbar.Toolbar;
import com.servoy.j2db.util.toolbar.ToolbarButton;

public class SpellCheckClientPlugin implements IClientPlugin, ActionListener, IIconProvider
{
	public static final String PLUGIN_NAME = "spellcheck"; //$NON-NLS-1$

	private IClientPluginAccess application;
	private JButton check;
	private SpellCheckerGUI gui = null;
	private Window lastWindow = null;
	private String lastUsedLanguage = null;
	private SpellCheckClientProvider spellCheckProvider;
	private SpellResult spellResponse = null;
	private JTextComponent checkedComponent = null;

	/*
	 * @see IPlugin#load()
	 */
	public void load() throws PluginException
	{
		spellCheckProvider = new SpellCheckClientProvider(this);
	}

	/*
	 * @see IPlugin#initialize(IApplication)
	 */
	public void initialize(IClientPluginAccess app) throws PluginException
	{
		application = app;

		IToolbarPanel tbp = app.getToolbarPanel();
		if (tbp != null)
		{
			Toolbar toolBar = null;
			Toolbar textToolBar = tbp.getToolBar("text"); //$NON-NLS-1$
			if (textToolBar == null)
			{
				toolBar = tbp.createToolbar("spellcheck", "spellcheck"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			else
			//else add to text toolbar if possible
			{
				textToolBar.addSeparator();
				toolBar = textToolBar;
			}

			if (toolBar != null)
			{
				Icon ico = getImage();
				check = new ToolbarButton(null, ico);
				check.addActionListener(this);
				toolBar.add(check);
				check.setEnabled(false);
			}
			application.getSettings().setProperty("plugin.spellcheck.googleServiceProvider", "false"); //$NON-NLS-1$//$NON-NLS-2$
			SpellCheckerPreferencePanel.setDesiredLanguage(SpellCheckerUtils.DEFAULT);
		}
	}

	/*
	 * @see IPlugin#unload()
	 */
	public void unload() throws PluginException
	{
		spellCheckProvider = null;
		application = null;
		lastWindow = null;
		if (gui != null)
		{
			gui.dispose();
			gui = null;
		}
	}


	public void actionPerformed(ActionEvent e)
	{
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		check(c, null);
	}


	void check(Component c, String language)
	{
		if (c instanceof JTextComponent)
		{
			IRuntimeWindow runtimeWindow = application.getCurrentRuntimeWindow();
			Window currentWindow = null;
			if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
			if (currentWindow != null)
			{
				if (gui == null || currentWindow != lastWindow || language != lastUsedLanguage)
				{
					lastWindow = currentWindow;
					ResourceBundle messages = getMessages(language);
					lastUsedLanguage = language;
					if (gui != null) gui.dispose();
					if (currentWindow instanceof JDialog)
					{
						gui = new SpellCheckerGUI(this, (JDialog)currentWindow, messages, true);
					}
					else if (currentWindow instanceof JFrame)
					{
						gui = new SpellCheckerGUI(this, (JFrame)currentWindow, messages, true);
					}
					else
					{
						return;//no valid window parent
					}
					gui.setName("SpellingCheckDialog"); //$NON-NLS-1$
					if (!Settings.getInstance().loadBounds(gui))
					{
						gui.setLocationRelativeTo(currentWindow);
					}
					gui.addWindowListener(new WindowAdapter()
					{
						@Override
						public void windowClosing(WindowEvent e)
						{
							if (e.getWindow() == gui)
							{
								Settings.getInstance().saveBounds(gui);
							}
						}
					});
				}

				checkedComponent = (JTextComponent)c;
				this.check(checkedComponent, language);
				//if we don't have to check we don't pop-up at all
				//NOTE: if we put this code before the check, the GUI unnecessarily shows up (has no real thing to display)
				if (gui.hasAtLeastOneSpellEvent())
				{
					checkedComponent.requestFocus(); // to make sure servoy sees the new values
					gui.toFront();
					gui.setVisible(true);
				}
			}
		}
	}

	private ResourceBundle getMessages(String language)
	{
		ResourceBundle messages;
		if (language != null)
		{
			Locale locale = Locale.ENGLISH;
			if (language.equals(LANGUAGES.DUTCH))
			{
				locale = new Locale("nl"); //$NON-NLS-1$
			}
			else if (language.equals(LANGUAGES.GERMAN))
			{
				locale = Locale.GERMAN;
			}
			else if (language.equals(LANGUAGES.ITALIAN))
			{
				locale = Locale.ITALIAN;
			}
			else if (language.equalsIgnoreCase(LANGUAGES.SPANISH))
			{
				locale = new Locale("es"); //$NON-NLS-1$
			}
			messages = ResourceBundle.getBundle(SpellCheckerUtils.MESSAGES, locale);
		}
		else
		{
			messages = ResourceBundle.getBundle(SpellCheckerUtils.MESSAGES);
		}
		return messages;
	}

	public void check(JTextComponent c, String optLang)
	{
		setTheEditFormatter();

		gui.removeEventsAndGuiForm();

		//reset caret position
		checkedComponent.setCaretPosition(0);

		String text = c.getText();
		String strUrl = null;
		try
		{
			String selectedLang = (optLang != null) ? optLang : SpellCheckerPreferencePanel.getDesiredLanguage();
			if (Utils.getAsBoolean(application.getSettings().getProperty("plugin.spellcheck.googleServiceProvider"))) //$NON-NLS-1$
			{
				String language = GoogleSpellUtils.getBasicLanguageName(selectedLang);
				strUrl = "https://www.google.com/tbproxy/spell?lang=" + language + "&hl=" + language; //$NON-NLS-1$ //$NON-NLS-2$
			}
			else
			{
				URL url = getPluginAccess().getServerURL();
				URL serviceURL = new URL(url, "servoy-service/" + SpellCheckServerPlugin.WEBSERVICE_NAME); //$NON-NLS-1$
				strUrl = serviceURL.toString();
			}

			ServiceHandler serviceHandler = new ServiceHandler(strUrl);

			String[] words = text.split("[\\p{Space}\\p{Punct}]+"); //$NON-NLS-1$
			for (String eachWord : words)
			{
				String xmlString = serviceHandler.handleTextSpellChecking(eachWord, selectedLang);
				ResponseSAXParser.getInstance().parseXMLString(xmlString);
				spellResponse = ResponseSAXParser.getInstance().getResponse();
				if (spellResponse.getSpellCorrections().size() <= 0) continue;
				SpellCheckEvent spellEvent = new SpellCheckEvent(eachWord, spellResponse.getSpellCorrections());
				gui.check(spellEvent);
			}
			gui.setTheFirstSpellEvent();
		}
		catch (MalformedURLException err)
		{
			Debug.error(err.getMessage());
		}

	}

	public boolean fireAndHandleEvent(SpellCheckEvent event)
	{
		//fireSpellCheckEvent(event);
		String word = event.getInvalidWord();
		String t = checkedComponent.getText();
		DataField df = null;

		if (checkedComponent instanceof DataField) df = (DataField)checkedComponent;

		//Work out what to do in response to the event.
		switch (event.getAction())
		{
			case SpellCheckEvent.INITIAL :
				break;
			case SpellCheckEvent.IGNORE : //SKIP
				break;
			case SpellCheckEvent.REPLACE :
				checkedComponent.replaceSelection(event.getReplaceWord());
				if (checkedComponent.getCaretPosition() >= t.length() - 1) checkedComponent.setCaretPosition(0);
				break;
			case SpellCheckEvent.REPLACEALL :
				//just a check to make sure we don't loop around forever
				if (word.equals(event.getReplaceWord())) break;
				//replace all occurences of word, that is why we start from the begining of the text
				checkedComponent.replaceSelection(event.getReplaceWord());
				t = checkedComponent.getText();
				int start = 0;
				int end = word.length();
				while ((start = t.indexOf(word)) >= 0)
				{
					checkedComponent.setSelectionStart(start);
					checkedComponent.setSelectionEnd(start + end);
					checkedComponent.replaceSelection(event.getReplaceWord());
					if (start + end >= t.length() - 1) checkedComponent.setCaretPosition(0);
					else checkedComponent.setCaretPosition(start + end);
					t = checkedComponent.getText();
				}
				if (checkedComponent.getCaretPosition() >= t.length() - 1) checkedComponent.setCaretPosition(0);
				break;
			case SpellCheckEvent.ADDTODICT :
				break;
			case SpellCheckEvent.CANCEL :
				break;
			default :
				throw new IllegalArgumentException("Unhandled case."); //$NON-NLS-1$
		}

		if (df != null) try
		{
			df.commitEdit();
		}
		catch (ParseException parseErr)
		{
			Debug.error(parseErr.getMessage());
			return false;
		}

		return true;
	}

	public SpellResult getSpellResponse()
	{
		return this.spellResponse;
	}

	public Properties getProperties()
	{
		Properties props = new Properties();
		props.put(DISPLAY_NAME, "Spellcheck"); //$NON-NLS-1$
		return props;
	}

	/*
	 * @see IPlugin#getPreferencePanels()
	 */
	public PreferencePanel[] getPreferencePanels()
	{
		return new PreferencePanel[] { new SpellCheckerPreferencePanel(application) };
	}

	/*
	 * @see IPlugin#getName()
	 */
	public String getName()
	{
		return PLUGIN_NAME;
	}

	public IScriptable getScriptObject()
	{
		return spellCheckProvider;
	}

	public Icon getImage()
	{
		URL iconUrl = this.getClass().getResource("images/spellcheck.png"); //$NON-NLS-1$
		if (iconUrl != null)
		{
			return new ImageIcon(iconUrl);
		}
		else
		{
			return null;
		}
	}

	public void propertyChange(PropertyChangeEvent evt)
	{
		if (evt.getPropertyName().equals("solution") && check != null) //$NON-NLS-1$
		{
			check.setEnabled(evt.getNewValue() != null);
		}
	}

	public IClientPluginAccess getPluginAccess()
	{
		return application;
	}

	public JTextComponent getCheckedComponent()
	{
		return checkedComponent;
	}

	private AbstractFormatter initialFormatter;

	public void setTheEditFormatter()
	{
		if (checkedComponent instanceof DataField)
		{
			DataField df = (DataField)checkedComponent;
			initialFormatter = df.getFormatter();
			DefaultFormatterFactory dff = (DefaultFormatterFactory)df.getFormatterFactory();
			if (dff != null && (initialFormatter == null || !initialFormatter.equals(dff.getEditFormatter())))
			{
				df.setFormatter(dff.getEditFormatter());
			}
		}
	}

	public void unsetTheEditFormatter()
	{
		if (checkedComponent instanceof DataField)
		{
			DataField df = (DataField)checkedComponent;
			if (!(df.getFormatter().equals(initialFormatter))) df.setFormatter(initialFormatter);
		}
	}

	@Override
	public URL getIconUrl()
	{
		return this.getClass().getResource("images/spellcheck.png"); //$NON-NLS-1$
	}

}
