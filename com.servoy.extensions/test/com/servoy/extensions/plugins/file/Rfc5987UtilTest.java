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
package com.servoy.extensions.plugins.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class Rfc5987UtilTest
{
	@Test
	public void attributeCharactersAreKeptAsIs() throws Exception
	{
		assertEquals("report-2026_v1.0.pdf", Rfc5987Util.encode("report-2026_v1.0.pdf"));
		assertEquals("!#$&+^`|~", Rfc5987Util.encode("!#$&+^`|~"));
	}

	@Test
	public void otherCharactersArePercentEncodedAsLowercaseUtf8Hex() throws Exception
	{
		assertEquals("my%20file.txt", Rfc5987Util.encode("my file.txt"));
		assertEquals("caf%c3%a9.txt", Rfc5987Util.encode("caf\u00e9.txt"));
		assertEquals("a%2fb%5cc%22d%27e", Rfc5987Util.encode("a/b\\c\"d'e"));
	}

	@Test
	public void encodeHonoursTheGivenCharset() throws Exception
	{
		assertEquals("caf%e9", Rfc5987Util.encode("caf\u00e9", "ISO-8859-1"));
	}

	@Test
	public void unknownCharsetIsRejected()
	{
		assertThrows(UnsupportedEncodingException.class, () -> Rfc5987Util.encode("x", "no-such-charset"));
		assertThrows(UnsupportedEncodingException.class, () -> Rfc5987Util.decode("x", "no-such-charset"));
	}

	@Test
	public void decodeAcceptsUpperAndLowerCaseHex() throws Exception
	{
		assertEquals("caf\u00e9", Rfc5987Util.decode("caf%C3%A9", "UTF-8"));
		assertEquals("caf\u00e9", Rfc5987Util.decode("caf%c3%a9", "UTF-8"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "plain.txt", "my file.txt", "caf\u00e9 \u65e5\u672c.pdf", "100% sure?.doc" })
	public void decodeReversesEncode(String name) throws Exception
	{
		assertEquals(name, Rfc5987Util.decode(Rfc5987Util.encode(name), "UTF-8"));
	}
}
