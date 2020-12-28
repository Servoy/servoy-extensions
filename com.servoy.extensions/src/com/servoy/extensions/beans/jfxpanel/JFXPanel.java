/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2013 Servoy BV

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

package com.servoy.extensions.beans.jfxpanel;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import com.servoy.j2db.IServoyBeanFactory;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.ui.IComponent;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.ExtendableURLClassLoader;

/**
 * @author lvostinar
 *
 */
public class JFXPanel implements IServoyBeanFactory
{
	@Override
	public String getName()
	{
		return "JFXPanel";
	}

	@Override
	public IComponent getBeanInstance(int servoyApplicationType, IClientPluginAccess access, Object[] cargs)
	{
		if (servoyApplicationType == IClientPluginAccess.WEB_CLIENT || servoyApplicationType == IClientPluginAccess.HEADLESS_CLIENT)
		{
			return new EmptyWicketFxPanel((String)cargs[0]);
		}
		else if (access.isInDeveloper() && System.getProperty("os.name").contains("nux")) return new EmptyJFXPanel("");
		else
		{
			if (isJavaFXAvailable(getClass().getClassLoader()))
			{
				try
				{
					return new ServoyJFXPanel();
				}
				catch (Exception ex)
				{
					Debug.error(ex);
				}
				catch (NoClassDefFoundError e)
				{
					Debug.error(e);
				}
			}
			else
			{
				URL fxUrl = getJavaFXURL();
				if (fxUrl != null && getClass().getClassLoader() instanceof ExtendableURLClassLoader)
				{
					((ExtendableURLClassLoader)getClass().getClassLoader()).addURL(fxUrl);
					try
					{
						return new ServoyJFXPanel();
					}
					catch (Exception ex)
					{
						Debug.error(ex);
					}
					catch (NoClassDefFoundError e)
					{
						Debug.error(e);
					}
				}
			}
			return new EmptyJFXPanel();
		}

	}

	private static boolean isJavaFXAvailable(ClassLoader classLoader)
	{
		try
		{
			Class.forName("javafx.embed.swing.JFXPanel", false, classLoader); //$NON-NLS-1$
			return true;
		}
		catch (Exception e)
		{
			// ignore
		}
		return false;
	}

	private static URL getJavaFXURL()
	{
		String jrePath = System.getProperty("java.home"); //$NON-NLS-1$
		File javaFXJar = new File(jrePath, "lib/jfxrt.jar"); //$NON-NLS-1$
		if (!javaFXJar.exists()) javaFXJar = new File(jrePath, "lib/ext/jfxrt.jar"); //$NON-NLS-1$
		if (javaFXJar.exists())
		{
			try
			{
				return javaFXJar.toURI().toURL();
			}
			catch (MalformedURLException e)
			{
				Debug.error(e);
			}
		}
		return null;
	}
}
