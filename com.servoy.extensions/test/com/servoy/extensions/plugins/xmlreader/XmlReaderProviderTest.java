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
package com.servoy.extensions.plugins.xmlreader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.servoy.extensions.plugins.file.JSFile;

public class XmlReaderProviderTest
{
	private static final String BOOKS = "<books><book price=\"44.95\"><title>Core Java 1.5</title><nrPages>1487</nrPages></book>" +
		"<book price=\"59.95\"><title>Developing with Servoy</title><nrPages>492</nrPages></book></books>";

	private final XmlReaderProvider provider = new XmlReaderProvider();

	@TempDir
	Path tempDir;

	private File writeXml(String xml) throws Exception
	{
		Path file = tempDir.resolve("books.xml");
		Files.writeString(file, xml, StandardCharsets.UTF_8);
		return file.toFile();
	}

	private static void assertBooks(XmlNode[] nodes)
	{
		assertEquals(1, nodes.length);
		assertEquals("books", nodes[0].js_getName());
		XmlNode[] books = nodes[0].js_getChildNodes();
		assertEquals(2, books.length);
		assertEquals("44.95", books[0].js_getAttributeValue("price"));
		assertEquals("Core Java 1.5", books[0].js_getChildNodes()[0].js_getTextValue());
		assertEquals("492", books[1].js_getChildNodes()[1].js_getTextValue());
	}

	@Test
	public void returnedTypesIsXmlNode()
	{
		assertArrayEquals(new Class< ? >[] { XmlNode.class }, provider.getAllReturnedTypes());
	}

	@Test
	public void readsDocumentFromString()
	{
		assertBooks(provider.js_readXmlDocumentFromString(BOOKS));
	}

	@Test
	public void leadingAndTrailingWhitespaceInTheStringIsIgnored()
	{
		assertBooks(provider.js_readXmlDocumentFromString("\n   " + BOOKS + "  \n"));
	}

	@Test
	public void topLevelCommentIsReturnedNextToTheRootElement()
	{
		XmlNode[] nodes = provider.js_readXmlDocumentFromString("<!-- c --><root/>");

		assertEquals(2, nodes.length);
		assertEquals("UNKNOWN", nodes[0].js_getType());
		assertEquals("root", nodes[1].js_getName());
	}

	@Test
	public void invalidXmlStringReturnsNull()
	{
		assertNull(provider.js_readXmlDocumentFromString("<books><book>"));
	}

	@Test
	public void readsDocumentFromFile() throws Exception
	{
		assertBooks(provider.js_readXmlDocumentFromFile(writeXml(BOOKS)));
	}

	@Test
	public void readsDocumentFromPathString() throws Exception
	{
		assertBooks(provider.js_readXmlDocumentFromFile(writeXml(BOOKS).getAbsolutePath()));
	}

	@Test
	public void readsDocumentFromJSFile() throws Exception
	{
		assertBooks(provider.js_readXmlDocumentFromFile(new JSFile(writeXml(BOOKS), null)));
	}

	@Test
	public void deprecatedReadXmlDocumentReadsFiles() throws Exception
	{
		assertBooks(provider.js_readXmlDocument(writeXml(BOOKS)));
	}

	@Test
	public void unsupportedArgumentReturnsNull()
	{
		assertNull(provider.js_readXmlDocumentFromFile(null));
		assertNull(provider.js_readXmlDocumentFromFile(Integer.valueOf(1)));
	}

	@Test
	public void missingFileReturnsNull()
	{
		assertNull(provider.js_readXmlDocumentFromFile(tempDir.resolve("missing.xml").toFile()));
	}

	@Test
	public void invalidXmlFileReturnsNull() throws Exception
	{
		assertNull(provider.js_readXmlDocumentFromFile(writeXml("not xml at all")));
	}
}
