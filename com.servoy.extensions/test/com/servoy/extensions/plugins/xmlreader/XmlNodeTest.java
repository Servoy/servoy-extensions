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

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public class XmlNodeTest
{
	private static Node root(String xml) throws Exception
	{
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
		return doc.getDocumentElement();
	}

	@Test
	public void elementWithOnlyTextIsReportedAsText() throws Exception
	{
		XmlNode node = new XmlNode(root("<title>Core Java</title>"));

		assertEquals("title", node.js_getName());
		assertEquals("TEXT", node.js_getType());
		assertEquals("Core Java", node.js_getTextValue());
	}

	@Test
	public void elementWithChildElementsIsReportedAsElement() throws Exception
	{
		XmlNode node = new XmlNode(root("<father><child1>John</child1><child2>Mary</child2></father>"));

		assertEquals("ELEMENT", node.js_getType());
		assertNull(node.js_getTextValue());
	}

	@Test
	public void emptyElementIsAnElementWithoutText() throws Exception
	{
		XmlNode node = new XmlNode(root("<empty/>"));

		assertEquals("ELEMENT", node.js_getType());
		assertNull(node.js_getTextValue());
		assertEquals(0, node.js_getChildNodes().length);
	}

	@Test
	public void childNodesSkipWhitespaceTextButKeepElementsInOrder() throws Exception
	{
		XmlNode node = new XmlNode(root("<father>\n  <child1>John</child1>\n  <child2>Mary</child2>\n</father>"));

		XmlNode[] children = node.js_getChildNodes();

		assertEquals(2, children.length);
		assertEquals("child1", children[0].js_getName());
		assertEquals("John", children[0].js_getTextValue());
		assertEquals("child2", children[1].js_getName());
		assertEquals("Mary", children[1].js_getTextValue());
	}

	@Test
	public void textChildIsATextNode() throws Exception
	{
		XmlNode[] children = new XmlNode(root("<child>John</child>")).js_getChildNodes();

		assertEquals(1, children.length);
		assertEquals("#text", children[0].js_getName());
		assertEquals("TEXT", children[0].js_getType());
		assertEquals("John", children[0].js_getTextValue());
	}

	@Test
	public void mixedContentKeepsNonBlankTextAndElements() throws Exception
	{
		XmlNode node = new XmlNode(root("<p>Hello <b>world</b></p>"));

		XmlNode[] children = node.js_getChildNodes();

		assertEquals("ELEMENT", node.js_getType());
		assertEquals(2, children.length);
		assertEquals("Hello ", children[0].js_getTextValue());
		assertEquals("b", children[1].js_getName());
	}

	@Test
	public void attributesAreReadByNameAndValue() throws Exception
	{
		XmlNode node = new XmlNode(root("<root attr1='value1' attr2='value2'/>"));

		assertArrayEquals(new String[] { "attr1", "attr2" }, node.js_getAttributeNames());
		assertEquals("value1", node.js_getAttributeValue("attr1"));
		assertEquals("value2", node.js_getAttributeValue("attr2"));
	}

	@Test
	public void elementWithoutAttributesHasNoAttributeNames() throws Exception
	{
		assertEquals(0, new XmlNode(root("<root/>")).js_getAttributeNames().length);
	}

	@Test
	public void textNodeHasNoAttributeNames() throws Exception
	{
		XmlNode text = new XmlNode(root("<a>x</a>")).js_getChildNodes()[0];
		assertEquals(0, text.js_getAttributeNames().length);
	}

	@Test
	public void missingAttributeReturnsNull() throws Exception
	{
		XmlNode node = new XmlNode(root("<root attr1='value1'/>"));
		assertNull(node.js_getAttributeValue("missing"));
	}

	@Test
	public void attributeValueOfATextNodeIsNull() throws Exception
	{
		XmlNode text = new XmlNode(root("<a>x</a>")).js_getChildNodes()[0];
		assertNull(text.js_getAttributeValue("any"));
	}

	@Test
	public void otherNodeTypesAreUnknown() throws Exception
	{
		Node comment = root("<root><!-- note --></root>").getFirstChild();
		XmlNode node = new XmlNode(comment);

		assertEquals("UNKNOWN", node.js_getType());
		assertEquals(" note ", node.js_getTextValue());
	}

	@Test
	public void toStringListsNameAttributesAndType() throws Exception
	{
		assertEquals("Node[name:root, attributes: {name:a,value:1|name:b,value:2}, type: ELEMENT]",
			new XmlNode(root("<root a='1' b='2'/>")).toString());
		assertEquals("Node[name:title, type: TEXT]", new XmlNode(root("<title>x</title>")).toString());
	}
}
