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

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IJavaScriptType;
import com.servoy.j2db.scripting.IScriptable;

/**
 * @author lvostinar
 *
 */
@ServoyDocumented
@ServoyClientSupport(ng = true, wc = false, sc = false)
public class FormPopup implements IScriptable, IJavaScriptType
{

	public int js_width()
	{
		return 0;
	}

	public void js_width(int width)
	{

	}

	public int js_height()
	{
		return 0;
	}

	public void js_height(int height)
	{

	}

	public int js_x()
	{
		return 0;
	}

	public void js_x(int x)
	{

	}

	public int js_y()
	{
		return 0;
	}

	public void js_y(int y)
	{

	}

	public boolean js_showBackdrop()
	{
		return false;
	}

	public void js_showBackdrop(boolean showBackdrop)
	{

	}
}
