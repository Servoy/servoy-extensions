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

import java.util.Map;

import com.servoy.j2db.dataprocessing.ITypedColumnConverter;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.serialize.JSONSerializerWrapper;

public class StringSerializer implements ITypedColumnConverter
{
	private final SerializePlugin plugin;

	public StringSerializer(SerializePlugin p)
	{
		plugin = p;
	}

	public Object convertFromObject(Map<String, String> props, int column_type, Object obj) throws Exception
	{
		if (obj == null) return null;
		try
		{
			return plugin.getJSONSerializer().toJSON(obj).toString();
		}
		catch (Exception e)
		{
			Debug.error("Error converting object '" + obj + "' to a json value"); //$NON-NLS-1$ //$NON-NLS-2$
			throw e;
		}
	}

	public Object convertToObject(Map<String, String> props, int column_type, Object dbvalue) throws Exception
	{
		if (dbvalue == null) return null;
		try
		{
			return plugin.getJSONSerializer().fromJSON(plugin.getClientPluginAccess().getDatabaseManager(), dbvalue.toString());
		}
		catch (Exception e)
		{
			Debug.error("Error converting json value '" + dbvalue + "'"); //$NON-NLS-1$ //$NON-NLS-2$
			throw e;
		}
	}

	public Map getDefaultProperties()
	{
		return null;
	}

	public String getName()
	{
		return JSONSerializerWrapper.STRING_SERIALIZER_NAME;
	}

	public int[] getSupportedColumnTypes()
	{
		return new int[] { IColumnTypes.TEXT };
	}

	/**
	 * @see com.servoy.j2db.dataprocessing.IColumnConverter#getToObjectType(java.util.Map)
	 */
	public int getToObjectType(Map props)
	{
		return IColumnTypes.MEDIA;
	}
}
