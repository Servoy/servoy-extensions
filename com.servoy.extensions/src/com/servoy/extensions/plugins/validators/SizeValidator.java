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
package com.servoy.extensions.plugins.validators;

import java.util.HashMap;
import java.util.Map;

import com.servoy.j2db.dataprocessing.IColumnValidator2;
import com.servoy.j2db.dataprocessing.IRecordMarkers;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.util.ILogLevel;
import com.servoy.j2db.util.Utils;

@SuppressWarnings("nls")
public class SizeValidator implements IColumnValidator2
{
	private static final String LENGTH_PROPERTY = "length";

	public Map<String, String> getDefaultProperties()
	{
		Map<String, String> props = new HashMap<>();
		props.put(LENGTH_PROPERTY, "");
		return props;
	}

	public String getName()
	{
		return "servoy.SizeValidator";
	}

	public int[] getSupportedColumnTypes()
	{
		return new int[] { IColumnTypes.TEXT, IColumnTypes.MEDIA };
	}

	@Override
	public void validate(Map<String, String> props, Object value, String dataprovider, IRecordMarkers validationObject, Object state)
	{
		int length = Utils.getAsInteger(props.get(LENGTH_PROPERTY));
		int valueLength = (value instanceof byte[]) ? ((byte[])value).length : (value instanceof String) ? ((String)value).length() : 0;
		if (valueLength > length)
		{
			if (validationObject != null)
				validationObject.report("i18n:servoy.validator.size", dataprovider, ILogLevel.ERROR, state,
					new Object[] { dataprovider, Integer.valueOf(valueLength), Integer.valueOf(length) });
			else throw new IllegalArgumentException();
		}
	}

	public void validate(Map<String, String> props, Object arg) throws IllegalArgumentException
	{
		validate(props, props, null, null, null);
	}
}
