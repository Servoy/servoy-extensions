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
package com.servoy.extensions.plugins.images;

import java.awt.Window;
import java.io.File;

import javax.imageio.ImageIO;

import com.servoy.extensions.plugins.file.JSFile;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IRuntimeWindow;
import com.servoy.j2db.plugins.ISmartRuntimeWindow;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.gui.SnapShot;

/**
 * Images plugin scripting object.
 * @author jcompagner
 */
@ServoyDocumented(publicName = ImagePlugin.PLUGIN_NAME, scriptingName = "plugins." + ImagePlugin.PLUGIN_NAME)
public class ImageProvider implements IScriptable, IReturnedTypesProvider
{
	private final ImagePlugin plugin;

	public ImageProvider(ImagePlugin plugin)
	{
		this.plugin = plugin;
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
			ImageIO.scanForPlugins();
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

	public ImageProvider()
	{
		this.plugin = null;
	}

	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { JSImage.class };
	}

	/**
	 * Creates a javascript image from the given bean/applet/form_element.
	 *
	 * @deprecated Replaced by {@link #getImage(Object)}
	 *
	 * @param obj bean/imageObj/element
	 */
	@Deprecated
	public JSImage js_createJPGImage(Object obj)
	{
		IClientPluginAccess access = plugin.getClientPluginAccess();
		IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
		Window currentWindow = null;
		if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
		return js_getImage(SnapShot.createJPGImage(currentWindow, obj, -1, -1));
	}


	/**
	 * @deprecated Replaced by {@link #getImage(Object)}
	 *
	 * @clonedesc js_createJPGImage(Object)
	 * @sampleas js_createJPGImage(Object)
	 *
	 * @param object bean/imageObj/element
	 * @param width
	 * @param height
	 */
	@Deprecated
	public JSImage js_createJPGImage(Object object, int width, int height)
	{
		IClientPluginAccess access = plugin.getClientPluginAccess();
		IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
		Window currentWindow = null;
		if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
		return js_getImage(SnapShot.createJPGImage(currentWindow, object, width, height));
	}

	/**
	 * Get a javascript image/resource object for the given file/bytearray/bean/applet/form_element.
	 *
	 * @sample
	 * var image = plugins.images.getImage(byteArray);
	 * var height = image.getHeight();
	 * var contentType = image.getContentType();
	 * var scaled_image = image.resize(30, 30);
	 *
	 * var snapshot_image = plugins.images.getImage(forms.companyReports.elements.employeesChartBean);
	 * var tempFile = plugins.file.createTempFile('bean_snapshot','.jpg')
	 * plugins.file.writeFile(tempFile, snapshot_image.getData())
	 * application.setStatusText('Wrote file: '+tempFile)
	 *
	 * @param object file/byte_array/bean/applet/form_element
	 */
	public JSImage js_getImage(Object object)
	{
		if (object instanceof JSFile)
		{
			object = ((JSFile)object).getFile();
		}
		else if (object instanceof String)
		{
			object = new File((String)object);
		}

		if (object instanceof File)
		{
			File file = (File)object;
			if (file.exists() && file.canRead() && file.length() > 0)
			{
				return new JSImage(file);
			}
		}

		if (object instanceof byte[])
		{
			return new JSImage((byte[])object);
		}

		IClientPluginAccess access = plugin.getClientPluginAccess();
		IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
		Window currentWindow = null;
		if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
		return new JSImage(SnapShot.createJPGImage(currentWindow, object, -1, -1));
	}
}
