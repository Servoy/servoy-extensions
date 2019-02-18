/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2019 Servoy BV

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

package com.servoy.extensions.plugins.lesscompiler;

import java.nio.charset.Charset;

import org.mozilla.javascript.annotations.JSFunction;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.server.ngclient.less.LessCompiler;
import com.servoy.j2db.solutionmodel.ISMMedia;

/**
 * @author jcompagner
 * @since 2019.3
 */
@SuppressWarnings("nls")
@ServoyDocumented(publicName = LessCompilerPlugin.PLUGIN_NAME, scriptingName = "plugins." + LessCompilerPlugin.PLUGIN_NAME)
public class LessProvider implements IScriptable
{

	private final IClientPluginAccess pluginAccess;

	/**
	 * @param pluginAccess
	 */
	public LessProvider(IClientPluginAccess pluginAccess)
	{
		this.pluginAccess = pluginAccess;
	}

	/**
	 * This function compiles the less contents of the given media file.
	 * It checks if the name ends with ".less" and will return a JSMedia with the same name but then with the extendsion ".css" in the same media folder.
	 *
	 *  @sample
	 *  var lessString = "@import 'solution.less';";
	 *  var lessMedia = solutionModel.newMedia('tenant.less', lessString);
	 *  var cssMedia = plugins.lesscompiler.compileLess(lessMedia);
	 *  application.overrideStyle('solution.less', cssMedia.getName()); // == tenant.css
	 *
	 * @param media The JSMedia file that is a less file that nees to be compiled to css
	 * @return The converted less file as a css media file or null if it wasn't a less file..
	 */
	@JSFunction
	public ISMMedia compileLess(ISMMedia media)
	{
		// this will use internal api on purpose.
		String name = media != null ? media.getName() : null;
		if (name != null && name.toLowerCase().endsWith(".less"))
		{
			String cssAsString = LessCompiler.compileLessWithNashorn(new String(media.getBytes(), Charset.forName("UTF-8")),
				(mediaName) -> pluginAccess.getSolutionModel().getMedia(mediaName), name);
			cssAsString = cssAsString.replaceAll("##last-changed-timestamp##", Long.toHexString(System.currentTimeMillis()));
			String cssName = name.substring(0, name.length() - 4) + "css";
			return pluginAccess.getSolutionModel().newMedia(cssName, cssAsString.getBytes(Charset.forName("UTF-8")));
		}
		return null;
	}
}
