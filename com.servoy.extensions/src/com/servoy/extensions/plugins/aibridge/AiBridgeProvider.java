/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2023 Servoy BV

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

package com.servoy.extensions.plugins.aibridge;

import org.mozilla.javascript.annotations.JSFunction;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;

@ServoyDocumented(publicName = AiBridgePlugin.PLUGIN_NAME, scriptingName = "plugins." + AiBridgePlugin.PLUGIN_NAME)
public class AiBridgeProvider implements IScriptable, IReturnedTypesProvider
{
	private EncodingRegistry registry;
	private Encoding enc;
	private final String[] allowedTokenizers = { "CL100K_BASE", "P50K_BASE", "P50K_EDIT", "R50K_BASE" };

	/**
	 * Count the number of tokens of the provided value using default tokenizer (CL100K_BASE)
	 *
	 * @param value
	 * @return int - the number of tokens
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public int countTokens(String value)
	{
		if (registry == null)
		{
			registry = Encodings.newDefaultEncodingRegistry();
		}
		enc = registry.getEncoding(EncodingType.CL100K_BASE);
		return enc.countTokens(value);
	}

	/**
	 * Count the number of tokens of the provided value using the specified tokenizer
	 *
	 * @param value
	 * @param tokenizer - one of the: cl100k_base, p50k_base, p50k_edit, r50k_base values
	 * @return int - a negative value for invalid tokenizer or the number of tokens
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public int countTokens(String value, String tokenizer)
	{
		if (registry == null)
		{
			registry = Encodings.newDefaultEncodingRegistry();
		}
		if (tokenizer != null && isValidTokenizer(tokenizer))
		{
			//encoding type valueOf(string) is not woring as expected so iterate
			EncodingType[] types = EncodingType.values();
			for (EncodingType type : types)
			{
				if (type.getName().equals(tokenizer));
				enc = registry.getEncoding(type);
				return enc.countTokens(value);
			}
		}
		return -1;
	}

	/**
	 * Cleanup all comments from the provided string code
	 *
	 * @param selection - formatted string value of code
	 * @return String - the code without comments
	 */
	@JSFunction
	@ServoyClientSupport(ng = true, mc = false, wc = true, sc = true)
	public static String cleanupSelection(String selection)
	{
		// Pattern to find multi-line block comments
		String blockCommentRegex = "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/";
		// Pattern to find single-line comments
		String singleLineCommentRegex = "//.*";
		// Pattern to find and preserve @type within block comments
		String typeTagRegex = "(?m)\\*.*@type.*";

		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(blockCommentRegex);
		java.util.regex.Matcher matcher = pattern.matcher(selection);
		StringBuffer sb = new StringBuffer();

		while (matcher.find())
		{
			// Replace the whole comment block with only the lines containing @type
			String block = matcher.group();
			String[] lines = block.split("\\n");
			StringBuilder typeLines = new StringBuilder("/**");
			for (String line : lines)
			{
				if (line.matches(typeTagRegex))
				{
					typeLines.append("\n").append(line.trim());
				}
			}
			typeLines.append("\n*/");
			matcher.appendReplacement(sb, typeLines.toString().replace("$", "\\$"));
		}
		matcher.appendTail(sb);
		String returnValue = sb.toString();

		returnValue = returnValue.replaceAll(blockCommentRegex, "");
		returnValue = returnValue.replaceAll(singleLineCommentRegex, "");
		// The code now contains no comments except the @type tags within block comments.

		// This pattern matches lines that contain only whitespace and removes them
		String emptyOrWhitespaceOnlyLinesRegex = "(?m)^[ \t]*\r?\n";
		returnValue = returnValue.replaceAll(emptyOrWhitespaceOnlyLinesRegex, "");


		// This pattern should match block comments that are empty or contain only asterisks and whitespace
		String emptyBlockCommentRegex = "/\\*\\*(\\s*\\*(?!.*@type)[^\\S\\r\\n]*\\s*)+\\*/";
		// This pattern should match lines within block comments that don't contain @type and have other content
		String nonTypeContentLineRegex = "(?m)^\\s*\\*(?!.*@type)[^\\S\\r\\n]*.*(?<!\\*/)\\s*$";
		String standaloneCommentDelimitersRegex = "(?m)^\\s*(/\\*\\*?|\\*/)?\\s*$";

		returnValue = returnValue.replaceAll(emptyBlockCommentRegex, "");
		returnValue = returnValue.replaceAll(nonTypeContentLineRegex, "");
		returnValue = returnValue.replaceAll(standaloneCommentDelimitersRegex, "");
		// Collapse multiple blank lines into a single blank line
		returnValue = returnValue.replaceAll("(?m)^[ \t]*\r?\n{2,}", "\n");

		return returnValue;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.j2db.scripting.IReturnedTypesProvider#getAllReturnedTypes()
	 */
	@Override
	public Class< ? >[] getAllReturnedTypes()
	{
		// TODO Auto-generated method stub
		return new Class[] { AiBridgeProvider.class };
	}

	private boolean isValidTokenizer(String tokenizer)
	{
		if (tokenizer == null || tokenizer.trim().length() == 0) return false;
		for (String allowed : allowedTokenizers)
		{
			if (allowed.equalsIgnoreCase(tokenizer))
			{
				return true;
			}
		}
		return false;
	}

}
