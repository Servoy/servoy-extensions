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

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.servoy.extensions.plugins.file.JSFile;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;

/**
 * <p><code>XMLReader</code> is an XML parsing tool designed to process XML data. It provides
 * two main methods for reading XML content, catering to both file-based and string-based sources.
 * Both methods provide access to the hierarchical structure of the XML, including child nodes,
 * attributes, and content, making them versatile for various XML processing needs.</p>
 *
 * @author jcompagner
 */
@SuppressWarnings("nls")
@ServoyDocumented(publicName = XmlReaderPlugin.PLUGIN_NAME, scriptingName = "plugins." + XmlReaderPlugin.PLUGIN_NAME)
public class XmlReaderProvider implements IScriptable, IReturnedTypesProvider
{

	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { XmlNode.class };
	}

	/**
	 * Reads an XML document from a file.
	 *
	 * @sample
	 * // specifies a reference to a file containing valid XML
	 * var xmlNodes = plugins.XmlReader.readXmlDocumentFromFile('c:/test.xml');
	 * var childNodes = xmlNodes[0].getChildNodes();
	 * // shows a dialog to open an xml file, then reads the file
	 * var xmlFile = plugins.file.showFileOpenDialog(1);
	 * var xmlNodes = plugins.XmlReader.readXmlDocumentFromFile(xmlFile);
	 * var childNodes = xmlNodes[0].getChildNodes();
	 *
	 * @param argument
	 * @return {XmlNode[]} An array of XmlNode objects representing the parsed XML document read from the file.
	 */
	public XmlNode[] js_readXmlDocumentFromFile(Object argument)
	{
		return js_readXmlDocument(argument);
	}

	/**
	 * Reads an XML document from a string.
	 *
	 * @sample
	 * var xmlString = '<books><book price="44.95">' +
	 * '<title>Core Java 1.5</title>' +
	 * '<author>Piet Klerksen</author>' +
	 * '<nrPages>1487</nrPages>' +
	 * '</book>' +
	 * '<book price="59.95">' +
	 * '<title>Developing with Servoy</title>' +
	 * '<author>Cheryl Owens and others</author><nrPages>492</nrPages></book></books>';
	 * var xmlNodes = plugins.XmlReader.readXmlDocumentFromString(xmlString);
	 *
	 * @param argument
	 * @return {XmlNode[]} An array of XmlNode objects representing the parsed XML document read from the string.
	 */
	public XmlNode[] js_readXmlDocumentFromString(String argument)
	{
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		try
		{
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(new InputSource(new StringReader(argument.trim())));
			return readDoc(doc);
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		return null;
	}

	/**
	 * @deprecated Replaced by {@link #readXmlDocumentFromString(String)} and {@link #readXmlDocumentFromFile(Object)}
	 */
	@Deprecated
	public XmlNode[] js_readXmlDocument(Object argument)
	{
		File file = null;
		if (argument instanceof File)
		{
			file = (File)argument;
		}
		else if (argument instanceof JSFile)
		{
			file = ((JSFile)argument).getFile();
		}
		else if (argument instanceof String)
		{
			file = new File((String)argument);
		}

		if (file == null)
		{
			return null;
		}

		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		try
		{
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(file);
			return readDoc(doc);
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		return null;
	}

	/**
	 * @param doc
	 */
	private XmlNode[] readDoc(Document doc)
	{
		if (doc != null)
		{
			NodeList nl = doc.getChildNodes();
			List<XmlNode> al = new ArrayList<XmlNode>();
			for (int i = 0; i < nl.getLength(); i++)
			{
				Node item = nl.item(i);
				if (item.getNodeType() == Node.TEXT_NODE)
				{
					String value = item.getNodeValue();
					if (value == null || "".equals(value.trim()))
					{
						continue;
					}
				}
				al.add(new XmlNode(item));
			}
			XmlNode[] nodes = al.toArray(new XmlNode[al.size()]);
			return nodes;
		}
		return null;
	}
}
