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
package com.servoy.extensions.plugins.window.menu;

import java.util.Arrays;

import org.mozilla.javascript.Function;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.extensions.plugins.window.util.DescendingNumberComparator;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.PluginException;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * Base class for Menus. Wraps IMenu client-specific menu implementation, is exposed in scripting.
 *
 * @author rgansevles
 *
 */
@ServoyClientSupport(ng = true, wc = true, sc = true)
public abstract class AbstractMenu implements IScriptable, IJavaScriptType
{
	private final IClientPluginAccess pluginAccess;
	private final IMenuHandler menuHandler;
	private IMenu menu;

	private IButtonGroup buttonGroup;

	public AbstractMenu()
	{
		this(null, null, null); // used for scripting
	}

	public AbstractMenu(IClientPluginAccess pluginAccess, IMenuHandler menuHandler, IMenu menu)
	{
		this.pluginAccess = pluginAccess;
		this.menuHandler = menuHandler;
		this.menu = menu;
	}

	public IMenuHandler getMenuHandler()
	{
		return menuHandler;
	}

	public IMenu getMenu()
	{
		return menu;
	}

	protected void setMenu(IMenu menu)
	{
		this.menu = menu;
	}

	public String getText()
	{
		return menu == null ? null : menu.getText();
	}

	/**
	 * Add a checkbox.
	 *
	 * @sampleas js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 *
	 * @return checkbox
	 */
	public CheckBox js_addCheckBox() throws PluginException
	{
		return js_addCheckBox((String)null);
	}

	/**
	 * Add a checkbox with given name.
	 *
	 * @sampleas js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the checkbox text; this can be also html if enclosed between html tags
	 *
	 * @return checkbox
	 */
	public CheckBox js_addCheckBox(String name) throws PluginException
	{
		return js_addCheckBox(name, null, null, null, null, null);
	}

	/**
	 * @clonedesc js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 *
	 * @param index the index at which to add the checkbox
	 *
	 * @return checkbox
	 */
	public CheckBox js_addCheckBox(Number index) throws PluginException
	{
		IMenuItem menuItem = menuHandler.createMenuItem(menu, IMenuItem.MENU_ITEM_CHECK);
		int ind = (index == null ? -1 : index.intValue());
		menu.addMenuItem(menuItem, ind);
		return (CheckBox)AbstractMenuItem.createmenuItem(pluginAccess, getMenuHandler(), menuItem, null, true);
	}

	/**
	 * @clonedesc js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the checkbox text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 *
	 * @return checkbox
	 */
	public CheckBox js_addCheckBox(String name, Function feedback_item) throws PluginException
	{
		return js_addCheckBox(name, feedback_item, null, null, null, null);
	}

	/**
	 * @clonedesc js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the checkbox text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the checkbox icon (can be an image URL or the image content byte array)
	 *
	 * @return checkbox
	 */
	public CheckBox js_addCheckBox(String name, Function feedback_item, Object icon) throws PluginException
	{
		return js_addCheckBox(name, feedback_item, icon, null, null, null);
	}

	/**
	 * @clonedesc js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the checkbox text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the checkbox icon (can be an image URL or the image content byte array)
	 * @param mnemonic the checkbox mnemonic
	 *
	 * @return checkbox
	 */
	public CheckBox js_addCheckBox(String name, Function feedback_item, Object icon, String mnemonic) throws PluginException
	{
		return js_addCheckBox(name, feedback_item, icon, mnemonic, null, null);
	}

	/**
	 * @clonedesc js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addCheckBox(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the checkbox text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the checkbox icon (can be an image URL or the image content byte array)
	 * @param mnemonic the checkbox mnemonic
	 * @param enabled the enabled state of the checkbox
	 *
	 * @return checkbox
	 */
	public CheckBox js_addCheckBox(String name, Function feedback_item, Object icon, String mnemonic, Boolean enabled) throws PluginException
	{
		return js_addCheckBox(name, feedback_item, icon, mnemonic, enabled, null);
	}

	/**
	 * Add a checkbox at the selected index (starting at 0) or at the end.
	 *
	 * @sample
	 * // add a new menu to the menubar
	 * var menubar = plugins.window.getMenuBar();
	 * var menu = menubar.addMenu();
	 * menu.text = "New Menu";
	 * // alternatively create a popup menu
	 * //var menu = plugins.window.createPopupMenu();
	 *
	 * // when you don't define an index the checkbox will be added at the last position
	 * // this is what you usually do to build a new menu
	 * // minimum settings are the text and method
	 * // the method can be a global or form method
	 * // be sure to enter the method WITHOUT '()' at the end
	 * menu.addCheckBox("checkbox", feedback_checkbox);
	 * // add a checkbox with an icon
	 * menu.addCheckBox("checkbox with icon", feedback_checkbox, "media:///yourimage.gif");
	 * //var pic_bytes = plugins.file.readFile("/path/to/image.jpg");
	 * //menu.addCheckBox("checkbox with icon", feedback_checkbox, pic_bytes);
	 * // add a checkbox with a mnemonic
	 * menu.addCheckBox("checkbox with mnemonic", feedback_checkbox, "media:///yourimage.gif", "c");
	 * // add a disabled checkbox
	 * menu.addCheckBox("checkbox disabled", feedback_checkbox, "media:///yourimage.gif", "d", false);
	 * // add a checkbox with text aligned to the right
	 * menu.addCheckBox("align right", feedback_checkbox, null, null, true, MenuItem.ALIGN_RIGHT);
	 *
	 * // add a checkbox at a given index (checkbox properties must be configured after creation)
	 * // indexes start at 0 (zero) so index 2 is in fact position 3
	 * var chk = menu.addCheckBox(2);
	 * chk.text = "checkbox at index";
	 * chk.setMethod(feedback_checkbox);
	 *
	 * @param name the checkbox text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the checkbox icon (can be an image URL or the image content byte array)
	 * @param mnemonic the checkbox mnemonic
	 * @param enabled the enabled state of the checkbox
	 * @param align the alignment type
	 *
	 * @return checkbox
	 */
	public CheckBox js_addCheckBox(String name, Function feedback_item, Object icon, String mnemonic, Boolean enabled, Number align) throws PluginException
	{
		try
		{
			IMenuItem menuItem = menuHandler.createMenuItem(menu, IMenuItem.MENU_ITEM_CHECK);
			menu.addMenuItem(menuItem, -1);

			MenuItemArgs menuItemArgs = getMenuItemArgs(name, feedback_item, icon, mnemonic, enabled, align);
			return (CheckBox)AbstractMenuItem.createmenuItem(pluginAccess, getMenuHandler(), menuItem, menuItemArgs, true);
		}
		catch (Exception e)
		{
			Debug.error("ERROR ADDING CHECKBOX", e);
			throw new PluginException(e);
		}
	}

	private MenuItemArgs getMenuItemArgs(String name, Object feedback_item, Object icon, String mnemonic, Boolean enabled_state, Number alignment)
	{
		String text = name;
		if (name == null) text = "noname"; //$NON-NLS-1$
		else if (name.startsWith("i18n:")) text = pluginAccess.getI18NMessage(name, null); //$NON-NLS-1$

		MenuItemArgs menuItemArgs = null;

		Function function = null;
		if (feedback_item instanceof Function)
		{
			function = (Function)feedback_item;
		}

		char mnemo = 0;
		if (mnemonic != null && mnemonic.length() >= 1)
		{
			mnemo = mnemonic.charAt(0);
		}

		int align = (alignment == null ? -1 : alignment.intValue());

		boolean enabled = (enabled_state == null ? true : enabled_state.booleanValue());

		if (icon instanceof String && ((String)icon).length() > 0)
		{
			menuItemArgs = new MenuItemArgs(text, null, function, (String)icon, null, mnemo, align, enabled);
		}
		else
		{
			menuItemArgs = new MenuItemArgs(text, null, function, null, (icon instanceof byte[]) ? (byte[])icon : null, mnemo, align, enabled);
		}

		return menuItemArgs;
	}

	/**
	 * @deprecated Replaced by {@link #addMenuItem(String,Function,Object,String,Boolean,Integer)}.
	 */
	@Deprecated
	public MenuItem js_addItem(Object[] vargs) throws PluginException
	{
		if (vargs != null && vargs.length == 1 && vargs[0] instanceof Number) return js_addMenuItem((Number)vargs[0]);
		else
		{
			MenuItemArgs mia = parseMenuItemArgs(pluginAccess, vargs);
			return js_addMenuItem(mia.name, mia.method, mia.imageURL == null ? mia.imageBytes : mia.imageURL, String.valueOf(mia.mnemonic),
				Boolean.valueOf(mia.enabled), Integer.valueOf(mia.align));
		}
	}

	/**
	 * Add a menu item.
	 *
	 * @sampleas js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 *
	 * @return menu item
	 */
	public MenuItem js_addMenuItem() throws PluginException
	{
		return js_addMenuItem((String)null);
	}

	/**
	 * Add a menu item with given name.
	 *
	 * @sampleas js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the menu item text; this can be also html if enclosed between html tags
	 *
	 * @return menu item
	 */
	public MenuItem js_addMenuItem(String name) throws PluginException
	{
		return js_addMenuItem(name, null, null, null, null, null);
	}

	/**
	 * @clonedesc js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 *
	 * @param index the index at which to add the menu item
	 *
	 * @return menu item
	 */
	public MenuItem js_addMenuItem(Number index) throws PluginException
	{
		IMenuItem menuItem = menuHandler.createMenuItem(menu, IMenuItem.MENU_ITEM_BUTTON);
		int ind = (index == null ? -1 : index.intValue());
		menu.addMenuItem(menuItem, ind);
		return (MenuItem)AbstractMenuItem.createmenuItem(pluginAccess, getMenuHandler(), menuItem, null, true);
	}

	/**
	 * @clonedesc js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the menu item text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 *
	 * @return menu item
	 */
	public MenuItem js_addMenuItem(String name, Function feedback_item) throws PluginException
	{
		return js_addMenuItem(name, feedback_item, null, null, null, null);
	}

	/**
	 * @clonedesc js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the menu item text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the menu item icon (can be an image URL or the image content byte array)
	 *
	 * @return menu item
	 */
	public MenuItem js_addMenuItem(String name, Function feedback_item, Object icon) throws PluginException
	{
		return js_addMenuItem(name, feedback_item, icon, null, null, null);
	}

	/**
	 * @clonedesc js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the menu item text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the menu item icon (can be an image URL or the image content byte array)
	 * @param mnemonic the menu item mnemonic
	 *
	 * @return menu item
	 */
	public MenuItem js_addMenuItem(String name, Function feedback_item, Object icon, String mnemonic) throws PluginException
	{
		return js_addMenuItem(name, feedback_item, icon, mnemonic, null, null);
	}

	/**
	 * @clonedesc js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addMenuItem(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the menu item text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the menu item icon (can be an image URL or the image content byte array)
	 * @param mnemonic the menu item mnemonic
	 * @param enabled the enabled state of the menu item
	 *
	 * @return menu item
	 */
	public MenuItem js_addMenuItem(String name, Function feedback_item, Object icon, String mnemonic, Boolean enabled) throws PluginException
	{
		return js_addMenuItem(name, feedback_item, icon, mnemonic, enabled, null);
	}

	/**
	 * Add a menu item at the selected index (starting at 0) or at the end.
	 *
	 * @sample
	 * // add a new menu to the menubar
	 * var menubar = plugins.window.getMenuBar();
	 * var menu = menubar.addMenu();
	 * menu.text = "New Menu";
	 * // alternatively create a popup menu
	 * //var menu = plugins.window.createPopupMenu();
	 *
	 * // when you don't define an index the item will be added at the last position
	 * // this is what you usually do to build a new menu
	 * // minimum settings are the text and method
	 * // the method can be a global or form method
	 * // be sure to enter the method WITHOUT '()' at the end
	 * menu.addMenuItem("item", feedback_item);
	 * // add an item with an icon
	 * menu.addMenuItem("item with icon", feedback_item, "media:///yourimage.gif");
	 * //var pic_bytes = plugins.file.readFile("/path/to/image.jpg");
	 * //menu.addMenuItem("item with icon", feedback_item, pic_bytes);
	 * // add an item with a mnemonic
	 * menu.addMenuItem("item with mnemonic", feedback_item, "media:///yourimage.gif", "i");
	 * //add an entry with fontawesome icon. Only supported in NGClient!
	 * menu.addMenuItem("an entry", this.feedback, 'fas fa-trash-alt');
	 * // add a disabled item
	 * menu.addMenuItem("disabled item", feedback_item, "media:///yourimage.gif", "d", false);
	 * // add an item with text aligned to the right
	 * menu.addMenuItem("align right", feedback_item, null, null, true, SM_ALIGNMENT.RIGHT);
	 *
	 * // add an item at a given index (item properties must be configured after creation)
	 * // indexes start at 0 (zero) so index 2 is in fact position 3
	 * var item = menu.addMenuItem(2);
	 * item.text = "item at index";
	 * item.setMethod(feedback_item);
	 *
	 * @param name the menu item text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the menu item icon (can be an image URL or the image content byte array).In NGClient we also support fontawesome class.
	 * @param mnemonic the menu item mnemonic
	 * @param enabled the enabled state of the menu item
	 * @param align the alignment type
	 *
	 * @return menu item
	 */
	public MenuItem js_addMenuItem(String name, Function feedback_item, Object icon, String mnemonic, Boolean enabled, Number align) throws PluginException
	{
		IMenuItem menuItem = menuHandler.createMenuItem(menu, IMenuItem.MENU_ITEM_BUTTON);
		menu.addMenuItem(menuItem, -1);

		MenuItemArgs menuItemArgs = getMenuItemArgs(name, feedback_item, icon, mnemonic, enabled, align);
		return (MenuItem)AbstractMenuItem.createmenuItem(pluginAccess, getMenuHandler(), menuItem, menuItemArgs, true);
	}

	/**
	 * Add a radio button.
	 * @sampleas js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 *
	 * @return a radio button menu item
	 */
	public RadioButton js_addRadioButton() throws PluginException
	{
		return js_addRadioButton((String)null);
	}

	/**
	 * Add a radio button with given name.
	 *
	 * @sampleas js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the radio button text; this can be also html if enclosed between html tags
	 *
	 * @return a radio button menu item
	 */
	public RadioButton js_addRadioButton(String name) throws PluginException
	{
		return js_addRadioButton(name, null, null, null, null, null);
	}

	/**
	 * @clonedesc js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 *
	 * @param index the index at which to add the radio button
	 *
	 * @return a radio button menu item
	 */
	public RadioButton js_addRadioButton(Number index) throws PluginException
	{
		IRadioButtonMenuItem menuItem = (IRadioButtonMenuItem)menuHandler.createMenuItem(menu, IMenuItem.MENU_ITEM_RADIO);
		if (buttonGroup == null)
		{
			buttonGroup = menuHandler.createButtonGroup();
		}
		buttonGroup.add(menuItem);
		int ind = (index == null ? -1 : index.intValue());
		menu.addMenuItem(menuItem, ind);
		return (RadioButton)AbstractMenuItem.createmenuItem(pluginAccess, getMenuHandler(), menuItem, null, true);
	}

	/**
	 * @clonedesc js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the radio button text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 *
	 * @return a radio button menu item
	 */
	public RadioButton js_addRadioButton(String name, Function feedback_item) throws PluginException
	{
		return js_addRadioButton(name, feedback_item, null, null, null, null);
	}

	/**
	 * @clonedesc js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the radio button text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the radio button icon (can be an image URL or the image content byte array)
	 *
	 * @return a radio button menu item
	 */
	public RadioButton js_addRadioButton(String name, Function feedback_item, Object icon) throws PluginException
	{
		return js_addRadioButton(name, feedback_item, icon, null, null, null);
	}

	/**
	 * @clonedesc js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the radio button text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the radio button icon (can be an image URL or the image content byte array)
	 * @param mnemonic the radio button mnemonic
	 *
	 * @return a radio button menu item
	 */
	public RadioButton js_addRadioButton(String name, Function feedback_item, Object icon, String mnemonic) throws PluginException
	{
		return js_addRadioButton(name, feedback_item, icon, mnemonic, null, null);
	}

	/**
	 * @clonedesc js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 * @sampleas js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 *
	 * @param name the radio button text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the radio button icon (can be an image URL or the image content byte array)
	 * @param mnemonic the radio button mnemonic
	 * @param enabled the enabled state of radio button
	 *
	 * @return a radio button menu item
	 */
	public RadioButton js_addRadioButton(String name, Function feedback_item, Object icon, String mnemonic, Boolean enabled) throws PluginException
	{
		return js_addRadioButton(name, feedback_item, icon, mnemonic, enabled, null);
	}

	/**
	 * Add a radiobutton at the selected index (starting at 0) or at the end.
	 *
	 * @sample
	 * // add a new menu to the menubar
	 * var menubar = plugins.window.getMenuBar();
	 * var menu = menubar.addMenu();
	 * menu.text = "New Menu";
	 * // alternatively create a popup menu
	 * //var menu = plugins.window.createPopupMenu();
	 *
	 * // when you don't define an index the radiobutton will be added at the last position
	 * // this is what you usually do to build a new menu
	 * // minimum settings are the text and method
	 * // the method can be a global or form method
	 * // be sure to enter the method WITHOUT '()' at the end
	 * menu.addRadioButton("radio", feedback_radiobutton);
	 * // add a radiobutton with an icon
	 * menu.addRadioButton("radio with icon", feedback_radiobutton, "media:///yourimage.gif");
	 * //var pic_bytes = plugins.file.readFile("/path/to/image.jpg");
	 * //menu.addRadioButton("radio with icon", feedback_radiobutton, pic_bytes);
	 *
	 * // add a new radiobutton group
	 * // a group will 'bind' all added radiobuttons after the group together
	 * // as a result checking one item will uncheck the other
	 * // if no group is added, a group is created automatically when the first radiobutton is added to the menu
	 * // so in this case we will have two groups, one with the radiobuttons added until now and one with the ones added from now on
	 * menu.addRadioGroup();
	 *
	 * // add a radiobutton with a mnemonic
	 * menu.addRadioButton("radio with mnemonic", feedback_radiobutton, "media:///yourimage.gif", "i");
	 * // add a disabled radiobutton
	 * menu.addRadioButton("disabled radio", feedback_radiobutton, "media:///yourimage.gif", "d", false);
	 * // add a radiobutton with text aligned to the right
	 * menu.addRadioButton("align right", feedback_radiobutton, null, null, true, SM_ALIGNMENT.RIGHT);
	 * // add a radiobutton at a given index (item properties must be configured after creation)
	 * // indexes start at 0 (zero) so index 2 is in fact position 3
	 * var rd = menu.addRadioButton(2);
	 * rd.text = "radio at index";
	 * rd.setMethod(feedback_item);
	 *
	 * @param name the radio button text; this can be also html if enclosed between html tags
	 * @param feedback_item the feedback function
	 * @param icon the radio button icon (can be an image URL or the image content byte array)
	 * @param mnemonic the radio button mnemonic
	 * @param enabled the enabled state of radio button
	 * @param align the alignment type
	 *
	 * @return a radio button menu item
	 */
	public RadioButton js_addRadioButton(String name, Function feedback_item, Object icon, String mnemonic, Boolean enabled, Number align)
		throws PluginException
	{
		try
		{
			IRadioButtonMenuItem menuItem = (IRadioButtonMenuItem)menuHandler.createMenuItem(menu, IMenuItem.MENU_ITEM_RADIO);
			if (buttonGroup == null)
			{
				buttonGroup = menuHandler.createButtonGroup();
			}
			buttonGroup.add(menuItem);
			menu.addMenuItem(menuItem, -1);
			MenuItemArgs menuItemArgs = getMenuItemArgs(name, feedback_item, icon, mnemonic, enabled, align);
			return (RadioButton)AbstractMenuItem.createmenuItem(pluginAccess, getMenuHandler(), menuItem, menuItemArgs, true);
		}
		catch (Exception e)
		{
			Debug.error("ERROR ADDING RADIO BUTTON", e);
			throw new PluginException(e);
		}

	}

	/**
	 * Add a radiogroup for radiobuttons. A radiogroup groups together all radiobuttons that are added
	 * after the group is added. From all radiobuttons that belong to the same radiogroup only one can be
	 * checked at a time.
	 *
	 * If no radiogroup is added, one is created automatically when the first radiobutton is added.
	 *
	 * @sampleas js_addRadioButton(String,Function,Object,String,Boolean,Number)
	 */
	public void js_addRadioGroup() throws PluginException
	{
		buttonGroup = menuHandler.createButtonGroup();
	}

	/**
	 * Add the separator at the selected index (starting at 0) or at the end (empty).
	 *
	 * @sample
	 * // add a new menu to the menubar
	 * var menubar = plugins.window.getMenuBar();
	 * var menu = menubar.addMenu();
	 * menu.text = "New Menu";
	 * // alternatively create a popup menu
	 * //var menu = plugins.window.createPopupMenu();
	 *
	 * // add an item and a checkbox
	 * menu.addMenuItem("item", feedback_item);
	 * menu.addCheckBox("checkbox", feedback_checkbox);
	 * // add a separator
	 * menu.addSeparator();
	 * // add a radiobutton. it will be separated from the rest of the control by the separator
	 * menu.addRadioButton("radio", feedback_radiobutton);
	 * // add another separator between the item and the checkbox
	 * menu.addSeparator(1);
	 *
	 */
	public void js_addSeparator()
	{
		menu.addSeparator(-1);
	}

	/**
	 * @sampleas js_addSeparator()
	 * @clonedesc js_addSeparator()
	 *
	 * @param index the index at which to add the separator
	 */
	public void js_addSeparator(int index)
	{
		menu.addSeparator(index);
	}

	// submenu
	/**
	 * @deprecated Replaced by {@link #addMenu(String)}.
	 */
	@Deprecated
	public Menu js_addSubMenu() throws PluginException
	{
		return js_addMenu((String)null);
	}

	/**
	 * @param index the index at which to add the submenu
	 *
	 * @deprecated Replaced by {@link #addMenu(Number)}.
	 */
	@Deprecated
	public Menu js_addSubMenu(int index) throws PluginException
	{
		return js_addMenu(Integer.valueOf(index));
	}

	/**
	 *
	 * Add a submenu with given name.
	 *
	 * @sample
	 * // add a new menu to the menubar
	 * var menubar = plugins.window.getMenuBar();
	 * var menu = menubar.addMenu();
	 * menu.text = "New Menu";
	 * // alternatively create a popup menu
	 * //var menu = plugins.window.createPopupMenu();
	 *
	 * // add a first submenu
	 * var submenu1 = menu.addMenu("submenu 1");
	 * submenu1.addMenuItem("sub item 1 - 1", feedback_item);
	 * // add a submenu as child of the first submenu
	 * var submenu1_2 = submenu1.addMenu("submenu 1 - 2");
	 * submenu1_2.addMenuItem("sub item 1 - 2 - 1", feedback_item);
	 * // add another submenu as a child of the first submenu
	 * var submenu1_3 = submenu1.addMenu("submenu 1 - 3");
	 * submenu1_3.addMenuItem("sub item 1 - 3 - 1", feedback_item);
	 * // add a submenu to the second submenu of the first submenu
	 * var submenu1_3_2 = submenu1_2.addMenu("submenu 1 - 2 - 2");
	 * submenu1_3_2.addMenuItem("sub item 1 - 2 - 2 - 1", feedback_item);
	 * // add a submenu directly to the menu, at the first position
	 * var submenu0 = menu.addMenu(0);
	 * submenu0.text = "submenu 0";
	 * submenu0.addMenuItem("sub item 0 - 1", feedback_item);
	 *
	 * @param name the text of the submenu; this can be also html if enclosed between html tags
	 *
	 * @return the submenu
	 */
	public Menu js_addMenu(String name) throws PluginException
	{
		String text = (name == null ? "noname" : name); //$NON-NLS-1$
		if (text.startsWith("i18n:")) text = pluginAccess.getI18NMessage(text, null); //$NON-NLS-1$
		MenuItemArgs menuItemArgs = new MenuItemArgs(text, null, null, null, null, (char)0, -1, true);
		IMenu subMenu = menuHandler.createMenu(menu);
		menu.addMenuItem(subMenu, -1);
		if (menuItemArgs != null && menuItemArgs.name != null)
		{
			subMenu.setText(menuItemArgs.name);
		}
		return new Menu(pluginAccess, menuHandler, subMenu);
	}

	/**
	 * Add a submenu at the selected index (starting at 0).
	 *
	 * @sampleas js_addMenu(String)
	 *
	 * @param index the index at which to add the submenu
	 *
	 * @return the submenu
	 */
	public Menu js_addMenu(Number index) throws PluginException
	{
		IMenu subMenu = menuHandler.createMenu(menu);
		int indx = (index == null ? -1 : index.intValue());
		menu.addMenuItem(subMenu, indx);
		return new Menu(pluginAccess, menuHandler, subMenu);
	}

	/**
	 * Add a submenu at the end.
	 *
	 * @sampleas js_addMenu(String)
	 *
	 * @return the submenu
	 */
	public Menu js_addMenu() throws PluginException
	{
		return js_addMenu((String)null);
	}

	/**
	 * Get the checkbox at the selected index (starting at 0).
	 *
	 * @sample
	 * // add a new menu to the menubar
	 * var menubar = plugins.window.getMenuBar();
	 * var menu = menubar.addMenu();
	 * menu.text = "New Menu";
	 * // alternatively create a popup menu
	 * //var menu = plugins.window.createPopupMenu();
	 *
	 * // add two radiobuttons
	 * menu.addRadioButton("radio one", feedback_radiobutton);
	 * menu.addRadioButton("radio two", feedback_radiobutton);
	 * // add a menu item, with a separator before it
	 * menu.addSeparator();
	 * menu.addMenuItem("item", feedback_item);
	 * // add a checkbox, with a separator before it
	 * menu.addSeparator();
	 * menu.addCheckBox("check", feedback_checkbox);
	 * // add a submenu with an item under it
	 * var submenu = menu.addMenu("submenu");
	 * submenu.addMenuItem("subitem", feedback_item);
	 *
	 * // depending on some state, update the entries in the menu
	 * var some_state = true;
	 * if (some_state) {
	 * 	// select the first radiobutton
	 * 	menu.getRadioButton(0).selected = true;
	 * } else {
	 * 	// select the first radiobutton
	 * 	menu.getRadioButton(1).selected = true;
	 * }
	 * // enable/disable the menu item
	 * // remember to include the separators also when counting the index
	 * menu.getItem(3).enabled = !some_state;
	 * // select/unselect the checkbox
	 * // remember to include the separators also when counting the index
	 * menu.getCheckBox(5).selected = some_state;
	 * // change the text of the submenu and its item
	 * application.output(menu.getItemCount());
	 * if (some_state) {
	 * 	menu.getMenu(6).text = "some state";
	 * 	menu.getMenu(6).getItem(0).text = "some text";
	 * }
	 * else {
	 * 	menu.getMenu(6).text = "not some state";
	 * 	menu.getMenu(6).getItem(0).text = "other text";
	 * }
	 *
	 * @param index
	 */
	public CheckBox js_getCheckBox(int index)
	{
		AbstractMenuItem item = js_getItem(index);
		if (item instanceof CheckBox)
		{
			return (CheckBox)item;
		}
		return null;
	}

	/**
	 * Get the item at the selected index (starting at 0).
	 *
	 * @sampleas js_getCheckBox(int)
	 *
	 * @param index
	 */
	public AbstractMenuItem js_getItem(int index)
	{
		IMenuItem menuItem = menu.getMenuItem(index);
		if (menuItem != null)
		{
			AbstractMenuItem scriptObjectWrapper = menuItem.getScriptObjectWrapper();
			if (scriptObjectWrapper == null)
			{
				scriptObjectWrapper = AbstractMenuItem.createmenuItem(pluginAccess, getMenuHandler(), menuItem, null, true);
				menuItem.setScriptObjectWrapper(scriptObjectWrapper);
			}

			return scriptObjectWrapper;
		}
		return null;
	}

	/**
	 * Get the number of items in the menu.
	 *
	 * @sample
	 * // REMARK: indexes start at 0, disabled items, non visible items and seperators are counted also
	 * // REMARK: this is especially important when getting items by the index
	 * // add a new menu to the menubar
	 * var menubar = plugins.window.getMenuBar();
	 * var menu = menubar.addMenu();
	 * menu.text = "New Menu";
	 * // alternatively create a popup menu
	 * //var menu = plugins.window.createPopupMenu();
	 *
	 * // add two radiobuttons
	 * menu.addRadioButton("radio one", feedback_radiobutton);
	 * menu.addRadioButton("radio two", feedback_radiobutton);
	 * // add a checkbox
	 * menu.addCheckBox("check", feedback_checkbox);
	 * // add a menu item
	 * menu.addMenuItem("item", feedback_item);
	 * // add another menu item
	 * menu.addMenuItem("item 2", feedback_item);
	 *
	 * // remove the last item
	 * menu.removeItem(menu.getItemCount() - 1);
	 */
	public int js_getItemCount()
	{
		return menu.getMenuItemCount();
	}

	/**
	 * Retrieve the index of the item by text.
	 *
	 * @sample
	 * // add a new menu to the menubar
	 * var menubar = plugins.window.getMenuBar();
	 * var menu = menubar.addMenu();
	 * menu.text = "New Menu";
	 * // alternatively create a popup menu
	 * //var menu = plugins.window.createPopupMenu();
	 *
	 * // add two radiobuttons
	 * menu.addRadioButton("radio one", feedback_radiobutton);
	 * menu.addRadioButton("radio two", feedback_radiobutton);
	 * // add a checkbox
	 * menu.addCheckBox("check", feedback_checkbox);
	 * // add a menu item
	 * menu.addMenuItem("item", feedback_item);
	 * // add another menu item
	 * menu.addMenuItem("item 2", feedback_item);
	 *
	 * // find the index of the checkbox
	 * var idx = menu.getItemIndexByText("check");
	 * // remove the checkbox by its index
	 * menu.removeItem(idx);
	 * // remove both radiobuttons by their indices
	 * menu.removeItem([0, 1]);
	 * // remove all remaining entries
	 * menu.removeAllItems();
	 * // add back an item
	 * menu.addMenuItem("new item", feedback_item);
	 *
	 * @param text
	 */
	public int js_getItemIndexByText(String text)
	{
		if (text == null || "".equals(text)) //$NON-NLS-1$
		{
			Debug.error("You can not search for a text with a null or empty value."); //$NON-NLS-1$
			return -1;
		}

		for (int i = 0; i < menu.getMenuItemCount(); i++)
		{
			IMenuItem menuItem = menu.getMenuItem(i);
			if (menuItem != null && text.equals(menuItem.getText()))
			{
				return i;
			}
		}
		return -1;
	}


	public int getItemIndex(IMenuItem menuItem)
	{
		for (int i = 0; menuItem != null && i < menu.getMenuItemCount(); i++)
		{
			if (menuItem.equals(menu.getMenuItem(i)))
			{
				return i;
			}
			if (menuItem.getMenuComponent() != null && menu.getMenuItem(i) != null &&
				menuItem.getMenuComponent().equals(menu.getMenuItem(i).getMenuComponent()))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Get the radiobutton at the selected index (starting at 0).
	 *
	 * @sampleas js_getCheckBox(int)
	 *
	 * @param index
	 */
	public RadioButton js_getRadioButton(int index)
	{
		AbstractMenuItem item = js_getItem(index);
		if (item instanceof RadioButton)
		{
			return (RadioButton)item;
		}
		return null;
	}

	/**
	 * @deprecated Replaced by {@link #getMenu(int)}.
	 */
	@Deprecated
	public Menu js_getSubMenu(int index)
	{
		return js_getMenu(index);
	}

	/**
	 * Get the submenu at the selected index (starting at 0).
	 *
	 * @sampleas js_getCheckBox(int)
	 *
	 * @param index
	 */
	public Menu js_getMenu(int index)
	{
		IMenuItem menuItem = menu.getMenuItem(index);
		if (menuItem instanceof IMenu)
		{
			return new Menu(pluginAccess, menuHandler, (IMenu)menuItem);
		}
		return null;
	}

	/**
	 * Remove all items from the menu.
	 *
	 * @sampleas js_getItemIndexByText(String)
	 */
	public void js_removeAllItems()
	{
		menu.removeAllItems();
	}

	/**
	 * Remove the item(s) at the selected index/indices.
	 *
	 * @sampleas js_getItemIndexByText(String)
	 *
	 * @param index array of one or moe indexes corresponding to items to remove
	 */
	public void js_removeItem(Object[] index) throws PluginException
	{
		if (index == null)
		{
			return;
		}

		Arrays.sort(index, new DescendingNumberComparator());

		for (Object element : index)
		{
			int idx = Utils.getAsInteger(element);

			if (idx < 0 || idx >= menu.getMenuItemCount())
			{
				throw new PluginException("The item with index " + idx + " doesn't exist."); //$NON-NLS-1$ //$NON-NLS-2$
			}

			menu.removeMenuItem(idx);
		}
	}

	/**
	 * Sets the value for the specified element client property key.
	 *
	 * @sampleas js_getClientProperty(Object)
	 *
	 * @param key
	 * @param value
	 */
	public void js_putClientProperty(Object key, Object value)
	{
		menu.putClientProperty(key, value);
	}

	/**
	 * Gets the specified client property for the element based on a key.
	 *
	 * @sample
	 * // NOTE: Depending on the operating system, a user interface property name may be available.
	 * // add a new menu to the menubar
	 * var menubar = plugins.window.getMenuBar();
	 * var menu = menubar.addMenu();
	 * menu.text = "New Menu";
	 * // alternatively create a popup menu
	 * //var menu = plugins.window.createPopupMenu();
	 *
	 * // add an item to the menu
	 * menu.addMenuItem("item", feedback_item);
	 *
	 * // set the tooltip of the menu via client properties
	 * // keep the original tooltip in a form or global variable
	 * originalTooltip = menu.getClientProperty("ToolTipText");
	 * menu.putClientProperty("ToolTipText", "changed tooltip");
	 *
	 * // later restore the original tooltip from the variable
	 * //var menubar = plugins.window.getMenuBar();
	 * //var menu = menubar.getMenu(menubar.getMenuCount()-1);
	 * //menu.putClientProperty("ToolTipText", originalTooltip);
	 *
	 * @param key
	 */
	public Object js_getClientProperty(Object key)
	{
		return menu.getClientProperty(key);
	}

	public IClientPluginAccess getPluginAccess()
	{
		return pluginAccess;
	}

	public static MenuItemArgs parseMenuItemArgs(IClientPluginAccess pluginAccess, Object[] args)
	{
		if (args == null) return null;

		String name = null;
		if (args.length >= 1 && args[0] != null)
		{
			name = args[0].toString();
		}
		if (name == null) name = "noname"; //$NON-NLS-1$
		if (name.startsWith("i18n:")) name = pluginAccess.getI18NMessage(name, null); //$NON-NLS-1$

		Object[] submenu = null;
		Function method = null;
		if (args.length >= 2 && args[1] != null)
		{
			if (args[1] instanceof Function)
			{
				method = (Function)args[1];
			}
			else if (args[1].getClass().isArray())
			{
				submenu = (Object[])args[1];
			}
		}

		String imageURL = null;
		byte[] imageBytes = null;
		if (args.length >= 3 && args[2] != null && !"".equals(args[2])) //$NON-NLS-1$
		{
			if (args[2] instanceof String && ((String)args[2]).length() > 0)
			{
				imageURL = (String)args[2];
			}
			else if (args[2] instanceof byte[])
			{
				imageBytes = (byte[])args[2];
			}
		}

		char mnemonic = 0;
		if (args.length >= 4 && args[3] != null)
		{
			if (args[3] instanceof Character)
			{
				mnemonic = ((Character)args[3]).charValue();
			}
			if (args[3] instanceof String && args[3].toString().length() >= 1)
			{
				mnemonic = args[3].toString().charAt(0);
			}
		}

		boolean enabled = true;
		if (args.length >= 5 && args[4] != null)
		{
			enabled = Utils.getAsBoolean(args[4]);
		}

		int align = -1;//use 2,0,4
		if (args.length >= 6 && args[5] != null)
		{
			align = Utils.getAsInteger(args[5]);
		}

		return new MenuItemArgs(name, submenu, method, imageURL, imageBytes, mnemonic, align, enabled);
	}

	public static class MenuItemArgs
	{
		public final String name;
		public final Object[] submenu;
		public final Function method;
		public final String imageURL;
		public final byte[] imageBytes;
		public final char mnemonic;
		public final int align;
		public final boolean enabled;

		public MenuItemArgs(String name, Object[] submenu, Function method, String imageURL, byte[] imageBytes, char mnemonic, int align, boolean enabled)
		{
			this.name = name;
			this.submenu = submenu;
			this.method = method;
			this.imageURL = imageURL;
			this.imageBytes = imageBytes;
			this.mnemonic = mnemonic;
			this.align = align;
			this.enabled = enabled;
		}
	}
}