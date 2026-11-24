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
package com.servoy.extensions.plugins.jwt.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

public class BuilderTest
{
	private final JWTProvider provider = new JWTProvider();

	private DecodedJWT signAndDecode(Builder builder)
	{
		return JWT.decode(builder.sign(provider.HS256("pwd")));
	}

	@Test
	public void scalarClaimsKeepTheirType()
	{
		Date date = new Date(1_700_000_000_000L);
		DecodedJWT jwt = signAndDecode(new Builder().withClaim("s", "text").withClaim("b", Boolean.TRUE).withClaim("i", Integer.valueOf(7))
			.withClaim("l", Long.valueOf(8_000_000_000L)).withClaim("d", Double.valueOf(1.5)).withClaim("date", date));

		assertEquals("text", jwt.getClaim("s").asString());
		assertTrue(jwt.getClaim("b").asBoolean().booleanValue());
		assertEquals(7, jwt.getClaim("i").asInt().intValue());
		assertEquals(8_000_000_000L, jwt.getClaim("l").asLong().longValue());
		assertEquals(1.5, jwt.getClaim("d").asDouble().doubleValue());
		assertEquals(date, jwt.getClaim("date").asDate());
	}

	@Test
	public void unsupportedValuesAreIgnored()
	{
		DecodedJWT jwt = signAndDecode(new Builder().withClaim("o", new Object()).withClaim("n", null).withClaim("s", "kept"));

		assertTrue(jwt.getClaim("o").isMissing());
		assertTrue(jwt.getClaim("n").isMissing());
		assertEquals("kept", jwt.getClaim("s").asString());
	}

	@Test
	public void otherNumberTypesAreAddedAsIntegerLongOrDouble()
	{
		DecodedJWT jwt = signAndDecode(new Builder().withClaim("short", Short.valueOf((short)3)).withClaim("byte", Byte.valueOf((byte)4))
			.withClaim("float", Float.valueOf(2.5f)).withClaim("big", new java.math.BigDecimal("12.25"))
			.withClaim("bigint", java.math.BigInteger.valueOf(9_000_000_000L)));

		assertEquals(3, jwt.getClaim("short").asInt().intValue());
		assertEquals(4, jwt.getClaim("byte").asInt().intValue());
		assertEquals(2.5, jwt.getClaim("float").asDouble().doubleValue());
		assertEquals(12.25, jwt.getClaim("big").asDouble().doubleValue());
		assertEquals(9_000_000_000L, jwt.getClaim("bigint").asLong().longValue());
	}

	@Test
	public void javaMapsAndListsAreAddedLikeTheirJavascriptCounterparts()
	{
		Map<String, Object> inner = new java.util.LinkedHashMap<>();
		inner.put("float", Float.valueOf(1.5f));
		inner.put("short", Short.valueOf((short)2));
		inner.put("list", List.of("x", "y"));
		inner.put("nested", Map.of("k", "v"));

		DecodedJWT jwt = signAndDecode(new Builder().withClaim("map", inner).withClaim("list", List.of(Integer.valueOf(1), Integer.valueOf(2))));

		Map<String, Object> claim = jwt.getClaim("map").asMap();
		assertEquals(1.5, ((Number)claim.get("float")).doubleValue());
		assertEquals(2, claim.get("short"));
		assertEquals(List.of("x", "y"), claim.get("list"));
		assertEquals(Map.of("k", "v"), claim.get("nested"));
		assertEquals(List.of(1L, 2L), jwt.getClaim("list").asList(Long.class));
	}

	@Test
	public void numberArraysBecomeLongArraysAndOthersStringArrays()
	{
		DecodedJWT jwt = signAndDecode(new Builder().withClaim("nums", new Object[] { Integer.valueOf(1), Double.valueOf(2.0), null })
			.withClaim("mixed", new Object[] { "a", Integer.valueOf(1), null }));

		assertEquals(List.of(1L, 2L), jwt.getClaim("nums").asList(Long.class).subList(0, 2));
		assertNull(jwt.getClaim("nums").asList(Long.class).get(2));
		assertArrayEquals(new String[] { "a", "1", null }, jwt.getClaim("mixed").asArray(String.class));
	}

	@Test
	public void javascriptArraysAreUnwrapped()
	{
		DecodedJWT jwt = signAndDecode(new Builder().withClaim("roles", new NativeArray(new Object[] { "admin", "user" })));

		assertArrayEquals(new String[] { "admin", "user" }, jwt.getClaim("roles").asArray(String.class));
	}

	@Test
	public void javascriptObjectsBecomeNestedMapClaims()
	{
		NativeObject inner = new NativeObject();
		inner.put("deep", inner, "value");
		NativeObject address = new NativeObject();
		address.put("city", address, "Amsterdam");
		address.put("zip", address, new NativeArray(new Object[] { Integer.valueOf(1), Integer.valueOf(2) }));
		address.put("tags", address, new NativeArray(new Object[] { "a", "b" }));
		address.put("inner", address, inner);

		Map<String, Object> claim = signAndDecode(new Builder().withClaim("address", address)).getClaim("address").asMap();

		assertEquals("Amsterdam", claim.get("city"));
		assertEquals(List.of(1, 2), claim.get("zip"));
		assertEquals(List.of("a", "b"), claim.get("tags"));
		assertEquals(Map.of("deep", "value"), claim.get("inner"));
	}

	@Test
	public void payloadAddsEveryStringKeyOfAJavascriptObject()
	{
		NativeObject payload = new NativeObject();
		payload.put("user", payload, "admin");
		payload.put("age", payload, Integer.valueOf(42));
		payload.put(0, payload, "indexed values are skipped");

		DecodedJWT jwt = signAndDecode(new Builder().payload(payload));

		assertEquals("admin", jwt.getClaim("user").asString());
		assertEquals(42, jwt.getClaim("age").asInt().intValue());
		assertTrue(jwt.getClaim("0").isMissing());
	}

	@Test
	public void payloadIgnoresNonScriptableValues()
	{
		Builder builder = new Builder();
		assertSame(builder, builder.payload("not an object"));
		assertSame(builder, builder.payload(null));
	}

	@Test
	public void headersAndExpiryAreAdded()
	{
		Date expires = new Date(4_000_000_000_000L);
		DecodedJWT jwt = signAndDecode(new Builder().header("typ", "JWT").header("cty", "custom").withExpires(expires));

		assertEquals("JWT", jwt.getType());
		assertEquals("custom", jwt.getContentType());
		assertEquals(expires, jwt.getExpiresAt());
	}

	@Test
	public void signingWithAnUnusableAlgorithmReturnsNull()
	{
		Builder builder = new Builder().withClaim("a", "b");

		assertNull(builder.sign(new Algorithm(provider, "RS256")));
		assertFalse(new Builder().header("x", "y").sign(provider.HS256("pwd")).isEmpty());
	}
}
