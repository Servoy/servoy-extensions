/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2018 Servoy BV

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

package com.servoy.extensions.plugins.window.popupform;


import org.mozilla.javascript.Function;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.ui.IComponent;

/**
 * @author lvostinar
 *
 */
@ServoyDocumented
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class FormPopup implements IScriptable, IJavaScriptType
{

	/**
	 * Get the component form popup will be shown relative to.
	 *
	 * @sample
	 * popupform.component();
	 *
	 * @return IComponent
	 */
	public IComponent js_component()
	{
		return null;
	}

	/**
	 * Set component form popup will be shown relative to. If null, will use coordinates or show at screen center.
	 *
	 * @sample
	 * plugins.window.createFormPopup(forms.orderPicker).component(elements.myelement).show();
	 *
	 * @param component the form to show
	 *
	 * @return this The FormPopup itself
	 */
	public FormPopup js_component(IComponent component)
	{
		return this;
	}

	/**
	 * Get the popup form width (which was set using setter).
	 *
	 * @sample
	 * popupform.width();
	 *
	 * @return int
	 */
	public int js_width()
	{
		return 0;
	}

	/**
	 * Set form popup width. If not set, form design width will be used.
	 *
	 * @sample
	 * plugins.window.createFormPopup(forms.orderPicker).width(100).show();
	 *
	 * @param width form popup width
	 *
	 * @return this The FormPopup itself
	 *
	 */
	public FormPopup js_width(int width)
	{
		return this;
	}

	/**
	 * Get the popup form height (which was set using setter).
	 *
	 * @sample
	 * popupform.height();
	 *
	 * @return int
	 */
	public int js_height()
	{
		return 0;
	}

	/**
	 * Set form popup height. If not set, form design height will be used.
	 *
	 * @sample
	 * plugins.window.createFormPopup(forms.orderPicker).height(100).show();
	 *
	 * @param height form popup height
	 * @return
	 *
	 * @return this The FormPopup itself
	 */
	public FormPopup js_height(int height)
	{
		return this;
	}

	/**
	 * Get the popup form x location (which was set using setter).
	 *
	 * @sample
	 * popupform.x();
	 *
	 * @return int
	 */
	public int js_x()
	{
		return 0;
	}

	/**
	 * Set form popup x location. The priority sequence for location is: related element, set location, center of screen.
	 *
	 * @sample
	 * plugins.window.createFormPopup(forms.orderPicker).x(100).show();
	 *
	 * @param x form popup x location
	 * @return
	 *
	 * @return this The FormPopup itself
	 *
	 */
	public FormPopup js_x(int x)
	{
		return this;
	}

	/**
	 * Get the popup form y location (which was set using setter).
	 *
	 * @sample
	 * popupform.y();
	 *
	 * @return int
	 */
	public int js_y()
	{
		return 0;
	}

	/**
	 * Set form popup y location. The priority sequence for location is: related element, set location, center of screen.
	 *
	 * @sample
	 * plugins.window.createFormPopup(forms.orderPicker).y(100).show();
	 *
	 * @param y form popup y location
	 *
	 * @return this The FormPopup itself
	 *
	 */
	public FormPopup js_y(int y)
	{
		return this;
	}

	/**
	 * Get the popup form show backdrop (which was set using setter).
	 *
	 * @sample
	 * popupform.showBackdrop();
	 *
	 * @return boolean
	 */
	public boolean js_showBackdrop()
	{
		return false;
	}


	/**
	 * Set whether backdrop will be shown. Default value is false.
	 *
	 * @sample
	 * plugins.window.createFormPopup(forms.orderPicker).showBackdrop(true).show();
	 *
	 * @param showBackdrop form popup showBackdrop
	 *
	 * @return this The FormPopup itself
	 *
	 */
	public FormPopup js_showBackdrop(boolean showBackdrop)
	{
		return this;
	}

	/**
	 * Get the popup form dataprovider (which was set using setter).
	 *
	 * @sample
	 * popupform.dataprovider();
	 *
	 * @return String
	 */
	public String js_dataprovider()
	{
		return null;
	}

	/**
	 * Set form popup dataprovider that will be set. If this is set, also scope needs to be specified.
	 *
	 * @sample
	 * plugins.window.createFormPopup(forms.orderPicker).dataprovider('myid').scope(foundset.getSelectedRecord()).show();
	 *
	 * @param dataprovider form popup dataprovider
	 *
	 * @return this The FormPopup itself
	 *
	 */
	public FormPopup js_dataprovider(String dataprovider)
	{
		return this;
	}

	/**
	 * Get/Set the onclose function that is called when the closeFormPopup is called.
	 * This onClose will get a JSEvent as the first argument, and the return value that is given to the closeFormPopup(retvalue) call.
	 *
	 * @sample
	 * popupform.onClose();
	 *
	 * @return Function
	 */
	public Function js_onClose()
	{
		return null;
	}

	/**
	 * Get/Set the onclose function that is called when the closeFormPopup is called.
	 * This onClose will get a JSEvent as the first argument, and the return value that is given to the closeFormPopup(retvalue) call.
	 *
	 * @sample
	 * plugins.window.createFormPopup(forms.orderPicker).dataprovider('myid').onClose(closePopupFunction).show();
	 *
	 * @param function function that needs to be called when closed
	 *
	 * @return this The FormPopup itself
	 *
	 */
	public FormPopup js_onClose(Function function)
	{
		return this;
	}

	/**
	 * Get the popup form scope (which was set using setter).
	 *
	 * @sample
	 * popupform.scope();
	 *
	 * @return Object
	 */
	public Object js_scope()
	{
		return null;
	}

	/**
	 * Set form popup scope that will be modified. If this is set, also dataprovider needs to be specified.
	 *
	 * @sample
	 * plugins.window.createFormPopup(forms.orderPicker).dataprovider('myid').scope(foundset.getSelectedRecord()).show();
	 *
	 * @param scope form popup scope to modify
	 *
	 * @return this The FormPopup itself
	 *
	 */
	public FormPopup js_scope(Object scope)
	{
		return this;
	}

	/**
	 * Show form popup using parameters that were set
	 *
	 * @sample
	 * plugins.window.createFormPopup(forms.orderPicker).x(100).y(100).width(100).height(100).showBackdrop(true).show();
	 *
	 *
	 */
	public void js_show()
	{

	}
}
