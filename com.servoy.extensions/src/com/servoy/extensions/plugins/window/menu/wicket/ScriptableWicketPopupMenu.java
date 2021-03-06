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
package com.servoy.extensions.plugins.window.menu.wicket;

import java.util.HashMap;
import java.util.Map;

import org.apache.wicket.Component;

import com.servoy.extensions.plugins.window.menu.IMenuItem;
import com.servoy.extensions.plugins.window.menu.IPopupMenu;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.server.headlessclient.IPageContributor;
import com.servoy.j2db.ui.IComponent;
import com.servoy.j2db.util.Utils;

/**
 * Popupmenu in webclient.
 * 
 * @author jblok
 */

public class ScriptableWicketPopupMenu extends ScriptableWicketMenu implements IPopupMenu
{
	public ScriptableWicketPopupMenu(IClientPluginAccess access)
	{
		super(null, access);
	}

	private final Map<Integer, IMenuItem> callableMenuItems = new HashMap<Integer, IMenuItem>();

	public void showPopup(Object comp, int x, int y)
	{
		boolean useAJAX = Utils.getAsBoolean(app.getRuntimeProperties().get("useAJAX")); //$NON-NLS-1$ 
		if (useAJAX)
		{
			IPageContributor pc = app.getPageContributor();
			if (pc != null)
			{
				String jsComp = null;
				if (comp instanceof IComponent && comp instanceof Component)
				{
					IComponent c = (IComponent)comp;
					jsComp = "document.getElementById('" + ((Component)c).getMarkupId() + "');"; //$NON-NLS-1$ //$NON-NLS-2$ 
				}

				StringBuilder js = new StringBuilder();

				js.append("oMenu.clearContent();"); //$NON-NLS-1$ 
				js.append(generateMenuJS(this, "oMenu")); //$NON-NLS-1$				
				js.append("setTimeout(\""); //$NON-NLS-1$ 

				if (jsComp != null)
				{
					js.append("var jsComp = ").append(jsComp).append(";"); //$NON-NLS-1$ //$NON-NLS-2$ 
					js.append("var parentReg = YAHOO.util.Dom.getRegion(jsComp.offsetParent);"); //$NON-NLS-1$ 
					js.append("var jsCompReg = YAHOO.util.Dom.getRegion(jsComp);"); //$NON-NLS-1$
					js.append("oMenu.render(document.body);"); //$NON-NLS-1$
					js.append("var oMenuReg = YAHOO.util.Dom.getRegion(document.getElementById(oMenu.id));"); //$NON-NLS-1$  

					String popupHeight = "(oMenuReg.bottom - oMenuReg.top)"; //$NON-NLS-1$ 
					String render_parentY = "parentReg.top"; //$NON-NLS-1$ 
					String render_parentHeight = "(parentReg.bottom - parentReg.top)"; //$NON-NLS-1$ 
					String jsCompY = "jsCompReg.top + " + y; //$NON-NLS-1$ 


					js.append("if(jsComp.offsetParent && ").append(jsCompY).append("+").append(popupHeight).append(" > ").append(render_parentY).append(" + ").append( //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						render_parentHeight).append(" && ").append(jsCompY).append(" - ").append(popupHeight).append(" > ").append(render_parentY).append("){"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

					js.append("oMenu.moveTo(jsCompReg.left  + ").append(x).append(",jsCompReg.top - ").append(popupHeight).append(");"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 

					js.append("} else {"); //$NON-NLS-1$ 

					js.append("oMenu.moveTo(jsCompReg.left  + ").append(x).append(",jsCompReg.top + ").append(y).append(");"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 

					js.append("}"); //$NON-NLS-1$ 
				}
				else
				{
					js.append("oMenu.render(document.body);"); //$NON-NLS-1$
					js.append("oMenu.moveTo(").append(x).append(",").append(y).append(");"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				js.append("oMenu.show();\", 0);"); //$NON-NLS-1$

				MenuExecutorBehavior menuExecutor = (MenuExecutorBehavior)pc.getBehavior("PopMenuExecutor"); //$NON-NLS-1$
				if (menuExecutor != null)
				{
					menuExecutor.setCallableMenuItems(callableMenuItems);
				}

				pc.addDynamicJavaScript(js.toString());
			}
		}
	}

	public void hidePopup()
	{
		// Not implemented in webclient
		// TODO
	}

	CharSequence getCallBackUrl(IMenuItem item)
	{
		callableMenuItems.put(new Integer(item.hashCode()), item);

		boolean useAJAX = Utils.getAsBoolean(app.getRuntimeProperties().get("useAJAX")); //$NON-NLS-1$
		if (useAJAX)
		{
			IPageContributor pc = app.getPageContributor();
			if (pc != null)
			{
				MenuExecutorBehavior menuExecutor = (MenuExecutorBehavior)pc.getBehavior("PopMenuExecutor"); //$NON-NLS-1$
				if (menuExecutor == null)
				{
					menuExecutor = new MenuExecutorBehavior(app);
					pc.addBehavior("PopMenuExecutor", menuExecutor); //$NON-NLS-1$
				}
				return menuExecutor.getUrlForMenuItem(item.hashCode());
			}
		}
		return ""; //$NON-NLS-1$
	}
}
