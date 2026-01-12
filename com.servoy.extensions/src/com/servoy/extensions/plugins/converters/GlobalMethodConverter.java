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
package com.servoy.extensions.plugins.converters;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import com.servoy.j2db.dataprocessing.IPropertyDescriptor;
import com.servoy.j2db.dataprocessing.IPropertyDescriptorProvider;
import com.servoy.j2db.dataprocessing.ITypedColumnConverter;
import com.servoy.j2db.dataprocessing.IUIConverter;
import com.servoy.j2db.dataprocessing.PropertyDescriptor;
import com.servoy.j2db.persistence.ArgumentType;
import com.servoy.j2db.persistence.Column;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.persistence.IMethodArgument;
import com.servoy.j2db.persistence.IMethodTemplate;
import com.servoy.j2db.plugins.IMethodTemplatesFactory;
import com.servoy.j2db.plugins.IMethodTemplatesProvider;

/**
 * Column/UI converter that uses global methods.
 *
 * @author pianas
 */

public class GlobalMethodConverter implements ITypedColumnConverter, IUIConverter, IPropertyDescriptorProvider, IMethodTemplatesProvider
{
	private final String TO_OBJECT_NAME_PROPERTY = "toObjectMethodName"; //$NON-NLS-1$
	private final String FROM_OBJECT_NAME_PROPERTY = "fromObjectMethodName"; //$NON-NLS-1$
	private final String TYPE_NAME_PROPERTY = "type"; //$NON-NLS-1$

	private final ConverterPlugin plugin;

	public GlobalMethodConverter(ConverterPlugin plugin)
	{
		this.plugin = plugin;
	}

	private Object executeMethod(String methodName, int column_type, Object obj) throws Exception
	{
		Object value = obj;
		if (plugin.getApplication() != null && methodName != null && methodName.trim().length() != 0)
		{
			value = plugin.getApplication().executeMethod(null, methodName, new Object[] { obj, Column.getDisplayTypeString(column_type) }, false, false);
		}
		return value;
	}

	public Object convertFromObject(Map<String, String> props, int column_type, Object obj) throws Exception
	{
		if (props != null)
		{
			return executeMethod(props.get(FROM_OBJECT_NAME_PROPERTY), column_type, obj);
		}
		return obj;
	}

	public Object convertToObject(Map<String, String> props, int column_type, Object dbvalue) throws Exception
	{
		if (props != null)
		{
			return executeMethod(props.get(TO_OBJECT_NAME_PROPERTY), column_type, dbvalue);
		}
		return dbvalue;
	}

	public Map<String, String> getDefaultProperties()
	{
		Map<String, String> props = new HashMap<String, String>();
		props.put(FROM_OBJECT_NAME_PROPERTY, ""); //$NON-NLS-1$
		props.put(TO_OBJECT_NAME_PROPERTY, ""); //$NON-NLS-1$
		props.put(TYPE_NAME_PROPERTY, ""); //$NON-NLS-1$
		return props;
	}

	public String getName()
	{
		return getClass().getSimpleName();
	}

	/**
	 * @see com.servoy.j2db.dataprocessing.IColumnConverter#getToObjectType(java.util.Map)
	 */
	public int getToObjectType(Map<String, String> props)
	{
		if (props != null)
		{
			Object value = props.get(TYPE_NAME_PROPERTY);
			if (value != null)
			{
				if (value.equals("TEXT")) return IColumnTypes.TEXT; //$NON-NLS-1$
				if (value.equals("INTEGER")) return IColumnTypes.INTEGER; //$NON-NLS-1$
				if (value.equals("NUMBER")) return IColumnTypes.NUMBER; //$NON-NLS-1$
				if (value.equals("DATETIME")) return IColumnTypes.DATETIME; //$NON-NLS-1$
				if (value.equals("MEDIA")) return IColumnTypes.MEDIA; //$NON-NLS-1$
				if (value.equals("ARRAY")) return Types.ARRAY; //$NON-NLS-1$
			}
		}
		return Integer.MAX_VALUE;
	}

	public int[] getSupportedColumnTypes()
	{
		return new int[] { IColumnTypes.DATETIME, IColumnTypes.INTEGER, IColumnTypes.MEDIA, IColumnTypes.NUMBER, IColumnTypes.TEXT, Types.ARRAY };
	}

	public int[] getSupportedDataproviderTypes()
	{
		return getSupportedColumnTypes();
	}

	/**
	 * @see com.servoy.j2db.dataprocessing.ITypedColumnConverter#getPropertyDescriptor(java.lang.String)
	 */
	public IPropertyDescriptor getPropertyDescriptor(String property)
	{
		if (TO_OBJECT_NAME_PROPERTY.equals(property))
		{
			return new PropertyDescriptor("Global method to convert DBValue to Object, signature: (dbvalue,columntype)", IPropertyDescriptor.GLOBAL_METHOD); //$NON-NLS-1$
		}
		if (FROM_OBJECT_NAME_PROPERTY.equals(property))
		{
			return new PropertyDescriptor("Global method to convert Object to DBValue, signature: (object,columntype)", IPropertyDescriptor.GLOBAL_METHOD); //$NON-NLS-1$
		}
		if (TYPE_NAME_PROPERTY.equals(property))
		{
			String[] choices = new String[] { //
				"<as is>", //$NON-NLS-1$
				"TEXT", //$NON-NLS-1$
				"INTEGER", //$NON-NLS-1$
				"NUMBER", //$NON-NLS-1$
				"DATETIME", //$NON-NLS-1$
				"MEDIA", //$NON-NLS-1$
				"ARRAY" //$NON-NLS-1$
			};
			return new PropertyDescriptor("The converted object type", IPropertyDescriptor.NUMBER, choices); //$NON-NLS-1$
		}
		return null;
	}

	/**
	 * @see com.servoy.j2db.dataprocessing.IPropertyDescriptorProvider#validateProperties(java.util.Map)
	 */
	public void validateProperties(Map<String, String> properties)
	{
	}

	public Map<String, IMethodTemplate> getMethodTemplates(IMethodTemplatesFactory methodTemplatesFactory)
	{
		Map<String, IMethodTemplate> methodTemplates = new HashMap<String, IMethodTemplate>();

		methodTemplates.put(
			FROM_OBJECT_NAME_PROPERTY,
			methodTemplatesFactory.createMethodTemplate(
				"globalConverterObj2DB",
				"Called for performing a conversion between a displayed value and a database value.",
				ArgumentType.Object,
				"the database value.",
				new IMethodArgument[] { methodTemplatesFactory.createMethodArgument("displayedValue", ArgumentType.Object,
					"The displayed value."), methodTemplatesFactory.createMethodArgument(
						"dbType", ArgumentType.String, "The type of the database column. Can be one of \"" + Column.getDisplayTypeString(IColumnTypes.TEXT) +
							"\", \"" + Column.getDisplayTypeString(IColumnTypes.INTEGER) + "\", \"" + Column.getDisplayTypeString(IColumnTypes.NUMBER) +
							"\", \"" +
							Column.getDisplayTypeString(IColumnTypes.DATETIME) + "\" or \"" + Column.getDisplayTypeString(IColumnTypes.MEDIA) + "\".") },
				"// return the original value without conversion\n" + "return displayedValue;", true));

		methodTemplates.put(
			TO_OBJECT_NAME_PROPERTY,
			methodTemplatesFactory.createMethodTemplate(
				"globalConverterDB2Obj",
				"Called for performing a conversion between a database value and a displayed value.",
				ArgumentType.Object,
				"the displayed value.",
				new IMethodArgument[] { methodTemplatesFactory.createMethodArgument("databaseValue", ArgumentType.Object,
					"The database value."), methodTemplatesFactory.createMethodArgument(
						"dbType", ArgumentType.String, "The type of the database column. Can be one of \"" + Column.getDisplayTypeString(IColumnTypes.TEXT) +
							"\", \"" + Column.getDisplayTypeString(IColumnTypes.INTEGER) + "\", \"" + Column.getDisplayTypeString(IColumnTypes.NUMBER) +
							"\", \"" +
							Column.getDisplayTypeString(IColumnTypes.DATETIME) + "\" or \"" + Column.getDisplayTypeString(IColumnTypes.MEDIA) + "\".") },
				"// return the original value without conversion\n" + "return databaseValue;", true));

		return methodTemplates;
	}
}
