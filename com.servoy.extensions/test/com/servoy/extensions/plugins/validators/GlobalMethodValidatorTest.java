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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mozilla.javascript.Undefined;

import com.servoy.j2db.dataprocessing.IPropertyDescriptor;
import com.servoy.j2db.dataprocessing.IRecordMarkers;
import com.servoy.j2db.persistence.IMethodTemplate;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IMethodTemplatesFactory;

public class GlobalMethodValidatorTest
{
	private static final String PROP = GlobalMethodValidator.GLOBAL_METHOD_NAME_PROPERTY;

	private final IClientPluginAccess access = mock(IClientPluginAccess.class);
	private final GlobalMethodValidator validator = new GlobalMethodValidator(access);

	@Test
	public void exposesNameDefaultPropertiesAndAllColumnTypes()
	{
		assertEquals("servoy.GlobalMethodValidator", validator.getName());
		assertEquals(Map.of(PROP, ""), validator.getDefaultProperties());
		assertNull(validator.getSupportedColumnTypes());
	}

	@Test
	public void nullPropertiesDoNotCallTheGlobalMethod() throws Exception
	{
		assertDoesNotThrow(() -> validator.validate(null, "value", "dp", null, null));
		verifyNoInteractions(access);
	}

	@Test
	public void missingOrBlankMethodNameDoesNotCallTheGlobalMethod() throws Exception
	{
		assertDoesNotThrow(() -> validator.validate(Map.of(), "value", "dp", null, null));
		assertDoesNotThrow(() -> validator.validate(Map.of(PROP, "   "), "value", "dp", null, null));
		verifyNoInteractions(access);
	}

	@Test
	public void globalMethodReceivesValueDataproviderMarkersAndState() throws Exception
	{
		IRecordMarkers markers = mock(IRecordMarkers.class);
		Object state = new Object();
		when(access.executeMethod(any(), anyString(), any(), anyBoolean())).thenReturn(Boolean.TRUE);

		validator.validate(Map.of(PROP, "globals.check"), "value", "dp", markers, state);

		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(access).executeMethod(isNull(), eq("globals.check"), args.capture(), eq(false));
		assertEquals(4, args.getValue().length);
		assertEquals("value", args.getValue()[0]);
		assertEquals("dp", args.getValue()[1]);
		assertEquals(markers, args.getValue()[2]);
		assertEquals(state, args.getValue()[3]);
	}

	@Test
	public void trueResultPasses() throws Exception
	{
		when(access.executeMethod(any(), anyString(), any(), anyBoolean())).thenReturn(Boolean.TRUE);
		assertDoesNotThrow(() -> validator.validate(Map.of(PROP, "m"), "v", "dp", null, null));
	}

	@Test
	public void falseResultThrows() throws Exception
	{
		when(access.executeMethod(any(), anyString(), any(), anyBoolean())).thenReturn(Boolean.FALSE);
		assertThrows(IllegalArgumentException.class, () -> validator.validate(Map.of(PROP, "m"), "v", "dp", null, null));
	}

	@Test
	public void undefinedResultPasses() throws Exception
	{
		when(access.executeMethod(any(), anyString(), any(), anyBoolean())).thenReturn(Undefined.instance);
		assertDoesNotThrow(() -> validator.validate(Map.of(PROP, "m"), "v", "dp", null, null));
	}

	@Test
	public void exceptionInTheGlobalMethodIsTreatedAsFailure() throws Exception
	{
		when(access.executeMethod(any(), anyString(), any(), anyBoolean())).thenThrow(new RuntimeException("boom"));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(Map.of(PROP, "m"), "v", "dp", null, null));
	}

	@Test
	public void replacedClientPluginAccessIsUsed() throws Exception
	{
		IClientPluginAccess other = mock(IClientPluginAccess.class);
		when(other.executeMethod(any(), anyString(), any(), anyBoolean())).thenReturn(Boolean.TRUE);
		validator.setClientPluginAccess(other);

		validator.validate(Map.of(PROP, "m"), "v", "dp", null, null);

		verify(other).executeMethod(isNull(), eq("m"), any(), eq(false));
		verifyNoInteractions(access);
	}

	@Test
	public void propertyDescriptorIsOnlyProvidedForTheGlobalMethodProperty()
	{
		IPropertyDescriptor descriptor = validator.getPropertyDescriptor(PROP);
		assertNotNull(descriptor);
		assertEquals(IPropertyDescriptor.GLOBAL_METHOD, descriptor.getType());
		assertNull(validator.getPropertyDescriptor("other"));
	}

	@Test
	public void validatePropertiesAcceptsAnything()
	{
		assertDoesNotThrow(() -> validator.validateProperties(Map.of(PROP, "m")));
	}

	@Test
	public void methodTemplateIsRegisteredForTheGlobalMethodProperty()
	{
		IMethodTemplatesFactory factory = mock(IMethodTemplatesFactory.class);
		IMethodTemplate template = mock(IMethodTemplate.class);
		when(factory.createMethodTemplate(anyString(), anyString(), any(), any(), any(), anyString(), anyBoolean())).thenReturn(template);

		Map<String, IMethodTemplate> templates = validator.getMethodTemplates(factory);

		assertEquals(1, templates.size());
		assertEquals(template, templates.get(PROP));
		verify(factory).createMethodTemplate(eq("globalValidator"), anyString(), any(), any(), any(), anyString(), eq(true));
	}
}
