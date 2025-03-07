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

public class EmailValidator implements IColumnValidator2
{
	public Map<String, String> getDefaultProperties()
	{
		return null;
	}

	@SuppressWarnings("nls")
	public String getName()
	{
		return "servoy.EmailValidator";
	}

	public int[] getSupportedColumnTypes()
	{
		return new int[] { IColumnTypes.TEXT };
	}

	@SuppressWarnings("nls")
	@Override
	public void validate(Map<String, String> props, Object arg, String dataprovider, IRecordMarkers validationObject, Object state)
	{
		if (arg == null || arg.toString().trim().length() == 0) return;

		if (!Utils.isValidEmailAddress(arg.toString()))
		{
			if (validationObject != null)
				validationObject.report("i18n:servoy.validator.email", dataprovider, ILogLevel.ERROR, state, new String[] { arg.toString(), dataprovider });
			else throw new IllegalArgumentException();
		}

	}

	public void validate(Map<String, String> props, Object arg) throws IllegalArgumentException
	{
		validate(props, arg, null, null, null);
	}
}
