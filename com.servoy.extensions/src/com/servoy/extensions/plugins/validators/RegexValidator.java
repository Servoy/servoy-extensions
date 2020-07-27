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
import java.util.regex.Pattern;

import com.servoy.j2db.dataprocessing.IColumnValidator2;
import com.servoy.j2db.dataprocessing.IValidationObject;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.util.ILogLevel;

@SuppressWarnings("nls")
public class RegexValidator implements IColumnValidator2
{
	private static final String REGEX_PROPERTY = "regex";

	public Map<String, String> getDefaultProperties()
	{
		Map<String, String> props = new HashMap<>();
		props.put(REGEX_PROPERTY, "");
		return props;
	}

	public String getName()
	{
		return "servoy.RegexValidator";
	}

	public int[] getSupportedColumnTypes()
	{
		return new int[] { IColumnTypes.TEXT };
	}

	@Override
	public void validate(Map<String, String> props, Object value, String dataprovider, IValidationObject validationObject, Object state)
	{
		if (value == null || value.toString().trim().length() == 0) return;

		String regex = props.get(REGEX_PROPERTY);
		if (!Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value.toString()).matches())
		{
			if (validationObject != null)
				validationObject.report("i18n:servoy.validator.regexp", dataprovider, ILogLevel.ERROR, state,
					new String[] { value.toString(), regex, dataprovider });
			else throw new IllegalArgumentException();
		}
	}

	public void validate(Map<String, String> props, Object arg) throws IllegalArgumentException
	{
		validate(props, arg, null, null, null);
	}
}
