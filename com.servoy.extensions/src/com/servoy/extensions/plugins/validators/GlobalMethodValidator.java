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

import org.mozilla.javascript.Undefined;

import com.servoy.j2db.dataprocessing.IColumnValidator2;
import com.servoy.j2db.dataprocessing.IPropertyDescriptor;
import com.servoy.j2db.dataprocessing.IPropertyDescriptorProvider;
import com.servoy.j2db.dataprocessing.IRecordMarkers;
import com.servoy.j2db.dataprocessing.PropertyDescriptor;
import com.servoy.j2db.persistence.ArgumentType;
import com.servoy.j2db.persistence.IMethodArgument;
import com.servoy.j2db.persistence.IMethodTemplate;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IMethodTemplatesFactory;
import com.servoy.j2db.plugins.IMethodTemplatesProvider;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

public class GlobalMethodValidator implements IPropertyDescriptorProvider, IMethodTemplatesProvider, IColumnValidator2
{
	public static final String GLOBAL_METHOD_NAME_PROPERTY = "globalMethodName"; //$NON-NLS-1$

	private IClientPluginAccess clientPluginAccess;

	GlobalMethodValidator(IClientPluginAccess access)
	{
		clientPluginAccess = access;
	}

	public Map<String, String> getDefaultProperties()
	{
		Map<String, String> props = new HashMap<String, String>();
		props.put(GLOBAL_METHOD_NAME_PROPERTY, ""); //$NON-NLS-1$
		return props;
	}

	public String getName()
	{
		return "servoy.GlobalMethodValidator"; //$NON-NLS-1$
	}

	public int[] getSupportedColumnTypes()
	{
		return null;
	}

	public void validate(Map<String, String> props, Object arg) throws IllegalArgumentException
	{
		validate(props, props, null, null, null);
	}

	@Override
	public void validate(Map<String, String> props, Object value, String dataprovider, IRecordMarkers validationObject, Object state)
	{
		if (props != null)
		{
			String globalMethodName = props.get(GLOBAL_METHOD_NAME_PROPERTY);
			if (globalMethodName != null && globalMethodName.trim().length() != 0)
			{
				Object retValue = new Boolean(false);
				try
				{
					retValue = clientPluginAccess.executeMethod(null, globalMethodName, new Object[] { value, dataprovider, validationObject, state }, false);
				}
				catch (Exception e)
				{
					Debug.error(e);
				}
				if (!Undefined.isUndefined(retValue) && !Utils.getAsBoolean(retValue))
				{
					throw new IllegalArgumentException();
				}
			}
		}
	}

	public void setClientPluginAccess(IClientPluginAccess clientPluginAccess)
	{
		this.clientPluginAccess = clientPluginAccess;
	}

	/**
	 * @see com.servoy.j2db.dataprocessing.IPropertyDescriptorProvider#getPropertyDescriptor(java.lang.String)
	 */
	public IPropertyDescriptor getPropertyDescriptor(String property)
	{
		if (GLOBAL_METHOD_NAME_PROPERTY.equals(property))
		{
			return new PropertyDescriptor("Global method to use as a validator, signature: (object, dp, validationObject, state)", //$NON-NLS-1$
				IPropertyDescriptor.GLOBAL_METHOD);
		}
		return null;
	}

	/**
	 * @see com.servoy.j2db.dataprocessing.IPropertyDescriptorProvider#validateProperties(java.util.Map)
	 */
	public void validateProperties(Map<String, String> properties)
	{
	}

	@SuppressWarnings("nls")
	public Map<String, IMethodTemplate> getMethodTemplates(IMethodTemplatesFactory methodTemplatesFactory)
	{
		Map<String, IMethodTemplate> methodTemplates = new HashMap<String, IMethodTemplate>();

		methodTemplates.put(GLOBAL_METHOD_NAME_PROPERTY, methodTemplatesFactory.createMethodTemplate("globalValidator",
			"Called for performing validation on a value before storing it into the database. Avoid using the return value or throwing an error, Use the JSValidationObject to report the warning",
			null, null,
			new IMethodArgument[] { methodTemplatesFactory.createMethodArgument("value", ArgumentType.Object,
				"The value to be validated."), methodTemplatesFactory.createMethodArgument("dataproviderid", ArgumentType.String,
					"The dataprovider name that is being validated (to use for reporting and problem)."), methodTemplatesFactory.createMethodArgument(
						"validationObject", ArgumentType.valueOf("JSValidationObject"),
						"The validation object to report problems on."), methodTemplatesFactory.createMethodArgument("state", ArgumentType.Object,
							"The optional state object given by the caller.") },
			"// validate the value and report on the validationObject.report()",
			true));

		return methodTemplates;
	}
}
