package com.servoy.extensions.plugins.workflow;

import java.util.Map;

public class VariablesTypeHelper 
{
	private VariablesTypeHelper() {}
	
	/**
	 * Converts String variables which are longer than 255 characters to char[].
	 * @param vars process variables
	 */
	static void convertToJBPMTypes(Map<String,Object> vars)
	{
		for (String key : vars.keySet())
		{
			if (vars.get(key) instanceof String)
			{
				String value = (String) vars.get(key);
				if (value.length() > 255) vars.put(key, value.toCharArray());
			}
		}
	}
	
	/**
	 * Converts char[] variables to String.
	 * @param vars process variables
	 */
	public static void convertToServoyTypes(Map<String,Object> vars)
	{
		for (String key : vars.keySet())
		{
			if (vars.get(key) instanceof char[])
			{
				char[] value = (char[]) vars.get(key);
				vars.put(key, new String(value));
			}
		}
	}
}
