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

import java.util.Map;

import com.servoy.j2db.dataprocessing.IColumnValidator2;
import com.servoy.j2db.dataprocessing.IRecordMarkers;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.util.ILogLevel;
import com.servoy.j2db.util.Utils;

@SuppressWarnings("nls")
public class NumberRangeValidator implements IColumnValidator2
{
	private static final String FROM_PROPERTY = "from";
	private static final String TO_PROPERTY = "to";

	public Map<String, String> getDefaultProperties()
	{
		Map<String, String> props = new java.util.LinkedHashMap<>();
		props.put(FROM_PROPERTY, "");
		props.put(TO_PROPERTY, "");
		return props;
	}

	public String getName()
	{
		return "servoy.NumberRangeValidator";
	}

	public int[] getSupportedColumnTypes()
	{
		return new int[] { IColumnTypes.INTEGER, IColumnTypes.NUMBER };
	}

	@Override
	public void validate(Map<String, String> props, Object value, String dataprovider, IRecordMarkers validationObject, Object state)
	{
		if (value == null || value.toString().trim().length() == 0) return;

		double from = Utils.getAsDouble(props.get(FROM_PROPERTY));
		double to = Utils.getAsDouble(props.get(TO_PROPERTY));
		double val = Utils.getAsDouble(value);
		if (val < from || val > to)
		{
			if (validationObject != null)
				validationObject.report("i18n:servoy.validator.range", dataprovider, ILogLevel.ERROR, state,
					new Object[] { value, dataprovider, Double.valueOf(from), Double.valueOf(to) });
			else throw new IllegalArgumentException();
		}
	}

	public void validate(Map<String, String> props, Object arg) throws IllegalArgumentException
	{
		validate(props, arg, null, null, null);
	}
}
