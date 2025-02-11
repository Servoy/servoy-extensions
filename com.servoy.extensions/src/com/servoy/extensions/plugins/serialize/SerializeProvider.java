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
package com.servoy.extensions.plugins.serialize;

import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IScriptable;

/**
 * <p>The <code>serialize</code> plugin provides essential tools for working with JSON data, allowing objects
 * to be serialized into JSON text and deserialized back into JavaScript objects.</p>
 *
 * <p>The <code>toJSON</code> method converts a JavaScript object into a JSON string, enabling data to be
 * stored or transmitted in a lightweight format.</p>
 *
 * <p>Conversely, the <code>fromJSON</code> method transforms a JSON string back into its original
 * JavaScript object representation.</p>
 */
@ServoyDocumented(publicName = SerializePlugin.PLUGIN_NAME, scriptingName = "plugins." + SerializePlugin.PLUGIN_NAME)
public class SerializeProvider implements IScriptable
{
	private final SerializePlugin plugin;

	public SerializeProvider(SerializePlugin p)
	{
		plugin = p;
	}

	/**
	 * Serialize an object to JSON text.
	 *
	 * @sample
	 * var org_array = new Array('A1','F1','Paris-Dakar');
	 * var string_data = plugins.serialize.toJSON(org_array);
	 * var new_array = plugins.serialize.fromJSON(string_data);
	 * application.output(new_array.join('#'));
	 *
	 * @param obj
	 *
	 * @return The JSON string representation of the serialized object.
	 */
	public String js_toJSON(Object obj) throws Exception
	{
		return plugin.getJSONSerializer().toJSON(obj).toString();
	}

	/**
	 * Deserialize from JSON text to an object.
	 *
	 * @sampleas js_toJSON(Object)
	 *
	 * @param data
	 *
	 * @return The deserialized object from the provided JSON string.
	 */
	public Object js_fromJSON(String data) throws Exception
	{
		return plugin.getJSONSerializer().fromJSON(plugin.getClientPluginAccess().getDatabaseManager(), data);
	}

}