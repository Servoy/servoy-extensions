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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.servoy.j2db.dataprocessing.IColumnValidator;
import com.servoy.j2db.plugins.IClientPluginAccess;

public class ValidatorPluginTest
{
	private final ValidatorPlugin plugin = new ValidatorPlugin();

	@Test
	public void providesTheSixDefaultValidatorsInOrder()
	{
		IColumnValidator[] validators = plugin.getColumnValidators();

		assertEquals(6, validators.length);
		assertInstanceOf(GlobalMethodValidator.class, validators[0]);
		assertInstanceOf(EmailValidator.class, validators[1]);
		assertInstanceOf(RegexValidator.class, validators[2]);
		assertInstanceOf(NumberRangeValidator.class, validators[3]);
		assertInstanceOf(SizeValidator.class, validators[4]);
		assertInstanceOf(IdentValidator.class, validators[5]);
	}

	@Test
	public void validatorsAreCreatedOnce()
	{
		assertSame(plugin.getColumnValidators(), plugin.getColumnValidators());
	}

	@Test
	public void initializeAfterGetColumnValidatorsGivesTheGlobalMethodValidatorItsAccess() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		when(access.executeMethod(any(), anyString(), any(), anyBoolean())).thenReturn(Boolean.TRUE);
		IColumnValidator globalMethodValidator = plugin.getColumnValidators()[0];

		plugin.initialize(access);
		globalMethodValidator.validate(Map.of(GlobalMethodValidator.GLOBAL_METHOD_NAME_PROPERTY, "m"), "v");

		verify(access).executeMethod(any(), anyString(), any(), anyBoolean());
	}

	@Test
	public void initializeBeforeGetColumnValidatorsPassesTheAccessOnCreation() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		when(access.executeMethod(any(), anyString(), any(), anyBoolean())).thenReturn(Boolean.TRUE);

		plugin.initialize(access);
		plugin.getColumnValidators()[0].validate(Map.of(GlobalMethodValidator.GLOBAL_METHOD_NAME_PROPERTY, "m"), "v");

		verify(access).executeMethod(any(), anyString(), any(), anyBoolean());
	}

	@Test
	public void exposesPluginMetadata()
	{
		assertEquals("servoy_default_validators", plugin.getName());
		assertNull(plugin.getScriptObject());
		assertNull(plugin.getProperties());
	}

	@Test
	public void imageAndIconUrlAreConsistent()
	{
		assertEquals(plugin.getIconUrl() == null, plugin.getImage() == null);
	}

	@Test
	public void lifecycleCallbacksAreNoOps() throws Exception
	{
		plugin.load();
		plugin.unload();
		plugin.propertyChange(null);
	}
}
