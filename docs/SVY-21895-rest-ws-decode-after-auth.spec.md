# Spec: SVY-21895 — rest_ws: decode request bodies only after authorization, restrict charsets (lts_2024)

Security level: Private. Do not put exploit payloads, gadget class names or customer hostnames in code, tests, comments, commit messages or docs for this case.

## 1. Goal

In the rest_ws plugin on `lts_2024` (Servoy 2024.3.x), make sure a request body is never deserialized (JSON, or XML converted to JSON) before `checkAuthorization()` has succeeded. This applies to POST, PUT, PATCH and to multipart parts. Also accept only a safe allowlist of request charsets, including per-part charsets, and answer other charsets with a clean 4xx instead of a 500.

**This change alone does not fully remove the vulnerability.** Two groups stay exposed until the class allowlist exists in servoy-client (`JSONSerializerWrapper.getClassFromHint`, a separate case and repo, also on `lts_2024` first):
- Solutions that allow anonymous access (no `ws_authenticate`, no authorized groups). `checkAuthorization` allows everything for them.
- Authenticated users, and users who can pass a weak `ws_authenticate`.

The change only shrinks who can reach the deserialization sink and blocks the reported charset form of the WAF bypass. It must not be described as resolving SVY-21895.

## 2. Background

### 2.1 Findings re-checked on lts_2024

Verified in this checkout (`D:\git\servoy_201903\servoy-extensions`, branch `lts_2024`, module `com.servoy.extensions` version `2024.3.13-SNAPSHOT`). The triage line numbers (2026) differ from these.

Differences from the 2026 project context:

| Item | 2026 context | lts_2024 (verified) |
|------|--------------|---------------------|
| Java level | 21 | 17 (`maven.compiler.source/target` = 17 in the pom; Eclipse `.classpath` JavaSE-17). Do not use Java 21 language or API features. |
| Servlet namespace | jakarta | **javax** (`javax.servlet.*` in `RestWSServlet` and the rest_ws classes). The pom has a `javax.servlet:javax.servlet-api` dependency. A `jakarta.servlet-api` jar only appears in the plugin-copy section, not as a compile dependency. |
| Test setup | `test/` folder in the module, JUnit 6, surefire | No `test/` folder. No JUnit/surefire setup in `com.servoy.extensions/pom.xml` (the only "junit" hit is a third-party jar that is copied into the plugin output). |
| Existing tests | `RestWSServletMultipartDetectionTest` | **None in this checkout.** The multipart detection test and `RestWSServlet.isMultipartContent(...)` were added by SVY-21436 (commits 39ffe087, 8a21fddf) in a separate module `com.servoy.extensions.test` (JUnit 4, jakarta), which exists only on `origin/lts_2026`, `master` and `release`. It does not exist on `lts_2024`. `RestWSServlet` on lts_2024 still calls `ServletFileUpload.isMultipartContent(request)` (line 1269). |
| Eclipse MCP | available | not available; use file tools and `mvn` |

### 2.2 Current code (`com.servoy.extensions/src/com/servoy/extensions/plugins/rest_ws/servlets/RestWSServlet.java`)

- `doPost` (line 406), `doPut` (448), `doPatch` (498): call `getContents(request)`, then `getClient(request)`, then `wsService(WS_x, new Object[] { decodeContent(...) }, contents, request, response, client)`. The array literal evaluates `decodeContent` before `wsService` runs, so the body is deserialized before `wsService -> doWsService -> checkAuthorization` (line 732). The `decodeContent` calls are at lines 420, 467 and 517.
- `decodeContent` (1211) reads the charset from the `Content-Type` header (default `CHARSET_DEFAULT` = "UTF-8") and calls `decode(contentType, body, charset)`.
- `decode` (1237) is the sink: `plugin.getJSONSerializer().fromJSON(body.getString(charset))` for JSON (1242), and the same for XML after `XML.toJSONObject(...)` (1245). FORMPOST and TEXT also use `body.getString(charset)`. An unknown charset gives `UnsupportedEncodingException` and therefore a 500. Any JDK charset (including exotic ones) is accepted.
- `getMultipartContent` (1297) reaches `decode(...)` per part (1362). The charset comes from the part `Content-Type` header, defaulting to UTF-8. Form fields in the `useJSUploadForBinaryData` branch are read with `item.getString(charset)` using the per-part or request charset (1312-1317), and silently fall back to the default charset on `UnsupportedEncodingException`.
- `getRequestContentType` (1046) also calls `Charset.forName(charset)` on the request header charset (1060) to guess the content type. Exceptions are swallowed there. It only decodes the first 512 bytes and does not deserialize.
- `getContents` (1267) runs before `getClient` and before authorization. For multipart it parses and buffers the whole request (`ServletFileUpload.parseRequest`). That is not deserialization of the content and is left unchanged here (see section 6).
- `handleException` (303) maps `WebServiceException` to its `httpResponseCode`, so a new `WebServiceException(msg, 415)` or `400` yields a clean status. `NotAuthenticatedException` maps to 401 and `NotAuthorizedException` to 403.
- Sibling consumers of `contents`: `wsService` hands the raw `List<FileItem>` to `RestWSClientPlugin.setRequestResponseContents`, which solution code can read raw. This does not deserialize and stays unchanged.

### 2.3 Git history

Taken from the triage report. I did not rerun a full blame and confirmed only that the decode-before-auth ordering is in the original design (multipart support SVY-4547, PATCH SVY-6019), not a recent regression. The most recent commits touching the file on this branch (SVY-17124, SVY-15350, SVY-16023) are unrelated. No earlier spec for this key exists in `docs/`.

## 3. Design

### 3.1 Lazy decode after authorization (approved approach a)

Change `wsService`/`doWsService` so they receive a deferred body decoder instead of the already decoded `Object[] fixedArgs`.

- Add a small private functional interface in `RestWSServlet`, for example `interface BodyDecoder { Object[] decode() throws Exception; }`. `null` means "no body arguments" (GET and DELETE).
- `doPost`, `doPut` and `doPatch` pass `() -> new Object[] { decodeContent(contents.getRight(), contents.getLeft(), request) }`. No `decodeContent`/`decode`/`getMultipartContent` call may remain outside such a deferred decoder.
- In `doWsService`, call the decoder only **after** `checkAuthorization(...)` returned. Place the call right before `fixedArgs` is used (the args assembly at about line 757), or earlier if that is simpler, but always after `checkAuthorization`. The decoded array replaces the old `fixedArgs` variable. For GET and DELETE the decoder is `null` and behavior stays the same.
- If `checkAuthorization` throws, the decoder is never invoked. `NotAuthenticatedException`/`NotAuthorizedException` then give 401/403 through `handleException`, exactly as for requests without a body.
- Everything that happens before `checkAuthorization` in `doWsService` (`setApplicationUserProperties`, `parsePath`) does not deserialize the body. Do not move any body decoding in front of it.
- Multipart: parts are decoded inside `getMultipartContent`, which is called from `decodeContent`, so wrapping `decodeContent` in the deferred decoder covers it. `getMultipartContent` must not be called anywhere else.
- Behavior change to document in a code comment: errors caused by an invalid body (malformed JSON/XML, decode errors) are now reported after the auth check, so an unauthenticated caller gets 401/403 instead of 400/500. Order of response codes for authenticated callers is unchanged, except as in 3.2.
- Keep existing comments that describe intent. Add a comment at the decoder call: the body must not be deserialized before authorization (do not mention payload details).

### 3.1b Authorize before parsing the request body (added after user decision on open question 7)

`getContents(request)` runs before `getClient` and before `checkAuthorization`, and for multipart it parses and buffers the whole upload (`ServletFileUpload.parseRequest`) and reads the first bytes of the body to guess the content type. This is not deserialization, but it lets anonymous callers make the server buffer arbitrary uploads to disk before any authorization.

- Restructure `doPost`, `doPut` and `doPatch` so that the body is not read, parsed or buffered before `checkAuthorization()` succeeded. The body reading (`getContents`) moves into the same deferred step as the decoding (section 3.1), or authorization is performed earlier in the do* method. Choose the smallest change that keeps these properties: (1) `checkAuthorization` still runs exactly once per request, with the same arguments and the same `WS_AUTHENTICATE` handling as today; (2) the contents list that is handed to `RestWSClientPlugin.setRequestResponseContents` is still the parsed contents, available to solution code as before; (3) a failed authorization never touches the request body.
- Constraint: `checkAuthorization` needs the client and the parsed request path, not the body. If `getRequestContentType`/`getContents` currently influence anything needed before authorization (for example the `Content-Type` based method lookup), keep that part header-only.
- Document any behavior change in a code comment. The header-only charset check (3.2) still runs early.
- Add a test: with a failing `checkAuthorization`, the request input stream is never read (zero reads on the fake stream) for a multipart POST, as well as zero `decode` calls.

### 3.2 Charset allowlist (approved approach b)

Add a private static helper in `RestWSServlet`, package-private for tests, e.g. `static String checkCharset(String charset) throws WebServiceException`:

- Normalize the name (trim, strip quotes, which `getHeaderKey` already strips at the outer level).
- Look the name up with `Charset.isSupported`/`Charset.forName` inside a try that maps `IllegalCharsetNameException`/`UnsupportedCharsetException` to the rejection below. Compare the **canonical** `Charset.name()` (so aliases such as `utf8` or `latin1` resolve) against an allowlist of canonical names.
- Proposed allowlist (see open question 1): `UTF-8`, `US-ASCII`, `ISO-8859-1`, `UTF-16`, `UTF-16BE`, `UTF-16LE`, `windows-1252`. UTF-32 and EBCDIC/IBM code pages, and all other charsets, are rejected.
- A rejected charset throws `new WebServiceException("Unsupported charset", HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE)` (415). Recommended over 400 because the problem is the media type parameter. See open question 2. Do not echo the charset value back to the client. Log it at info/debug level with the plugin logger, with the value sanitized or length-limited.
- A missing charset still defaults to `CHARSET_DEFAULT`. An empty or malformed value is rejected like any other non-allowlisted value.

Where the check runs:

- Request charset: in `decodeContent`, and in `getRequestContentType` (replace the raw `Charset.forName(charset)` with the checked charset; an invalid charset there must still end in a rejection and not be swallowed into "guess OTHER"). Additionally validate the `Content-Type` header charset early in `doPost`/`doPut`/`doPatch`, right after `getContents`, because it only inspects a header and cannot trigger deserialization. If doing it early and in `decode` is redundant, the minimum requirement is that **every** path that calls `body.getString(charset)` or `Charset.forName(...)` goes through the helper.
- Per-part charset in `getMultipartContent`: validate the part charset (explicit part header, request charset fallback, and the form-field branch at lines 1312-1317) through the helper. In the form-field branch, remove the silent fallback to the default charset for disallowed charsets; a disallowed charset must be rejected, not decoded with a different charset. A charset that is allowlisted but `Charset.isSupported` false cannot occur (all allowlist entries are standard JDK charsets).
- A part without charset keeps the existing UTF-8 default.
- Response charset handling (`sendResult`, lines about 1421-1430, uses `Accept`/`Content-Type` charset for `content.getBytes(charset)`) is outside the request-decoding scope. It is unchanged (see open question 4).

### 3.3 Tests (approved approach c)

Where tests go on lts_2024 (decision needed, open question 3). Recommended: introduce the standard layout used by newer branches, inside the same module:

- New source folder `com.servoy.extensions/test/`, package `com.servoy.extensions.plugins.rest_ws.servlets`, class `RestWSServletAuthBeforeDecodeTest` (and a charset test class, or one class). Same package as `RestWSServlet`, so the package-private seams are visible.
- `com.servoy.extensions/pom.xml`: add `<testSourceDirectory>test</testSourceDirectory>`, `maven-surefire-plugin` with `**/*Test.java` includes, and test-scoped JUnit Jupiter. The test phase must run before the jar signing/packaging so tests use the unsigned `target/classes` (the reason newer branches dropped the separate test module). Add the Eclipse `.classpath` entry for `test` if it is to be run from the IDE. Pin exact versions, no ranges. The branch uses JUnit 4 style elsewhere in history; JUnit 5 (Jupiter) is preferred for new tests, Java 17 compatible, and Mockito is not needed (hand-written fakes).
- Alternative (not recommended): recreate a separate `com.servoy.extensions.test` module as on the 2026 branch. It needs a dependency on the signed main jar, which previously caused signer-info `SecurityException`s and release-merge trouble.
- How to run on lts_2024: `mvn -f com.servoy.extensions/pom.xml test -Dtest=RestWS*Test` (the pom is heavy and needs third-party artifacts and the Servoy parent/API artifacts; if offline resolution fails locally, state it and rely on the Eclipse compile check plus CI). `-DfailIfNoTests=false` for runs that do not include these tests. Eclipse MCP is not available in this session.
- Tests use `javax.servlet` types on lts_2024, not jakarta. Fake `HttpServletRequest`/`HttpServletResponse` through `java.lang.reflect.Proxy` or hand-written stubs (the javax interfaces are large; a dynamic proxy that implements only the used methods keeps tests short).

The servlet is hard to unit test end-to-end because it needs `RestWSPlugin`, a headless client pool, and `RestWSClientPlugin`. To keep tests broker-free and meaningful, introduce minimal package-private seams (no behavior change):

- Package-private/overridable methods in `RestWSServlet` for the pieces the test must replace: `getClient(request)` and the body decoder invocation, or make the decode step a package-private method (e.g. `decodeContent`, `decode`) so a test subclass can count calls. Prefer making `checkAuthorization` and `decodeContent` non-private (package-private, non-final) and subclassing the servlet in the test with a fake `RestWSPlugin`, rather than adding new abstractions.
- Tests:
  1. A POST (and PUT, PATCH) whose `checkAuthorization` fails (fake throws `NotAuthenticatedException`, then `NotAuthorizedException`) results in 401 (with `WWW-Authenticate`) and 403 respectively, and the counting `decodeContent`/`decode`/`getJSONSerializer().fromJSON` fake is invoked **zero** times. Use a harmless body such as `{}`; the fake serializer throws `AssertionError` if called, so no real class loading is needed.
  2. With successful authorization, the decoder is invoked exactly once and the decoded result is passed as the first function argument (guards against regressions in the happy path).
  3. Multipart: with failed authorization no part is decoded (zero `decode`/`getMultipartContent` calls); with success parts are decoded.
  4. Charset: `checkCharset` accepts each allowlist entry and common aliases (`utf8`, `latin1`, case variations, quoted values), and rejects a non-allowlisted charset, an EBCDIC-family name, an unknown name, an illegal name (e.g. containing spaces or control characters) and the empty string, each as a `WebServiceException` with 415 (or the confirmed status).
  5. A request with a disallowed request charset yields the confirmed status (not 500), and a multipart part with a disallowed per-part charset yields the same status, with zero deserialization calls in both cases.
  6. Pure ordering test for the lazy decoder (calls recorded in order: `checkAuthorization` before `decode`).
- No exploit payloads, gadget class names or hostnames anywhere in tests. Use neutral bodies such as `{}` and the names of allowlisted/non-allowlisted charsets only.

## 4. Implementation plan

1. In `RestWSServlet`, add the `BodyDecoder` functional interface and change `wsService` and `doWsService` signatures: replace the `Object[] fixedArgs` parameter by a `BodyDecoder`. In `doWsService`, after `checkAuthorization`, call the decoder and use its result where `fixedArgs` is used today. Update `doGet` (line 251) and `doDelete` (380) to pass `null`.
1b. Per section 3.1b, make sure `getContents(request)` (body reading and multipart buffering) also happens only after `checkAuthorization` succeeded, with the smallest restructuring of `doPost`/`doPut`/`doPatch`/`doWsService` that keeps `checkAuthorization` called once and the contents available to `RestWSClientPlugin`.
2. Update `doPost` (420), `doPut` (467), `doPatch` (517) to pass a lambda wrapping `decodeContent`. Verify with a search that no `decodeContent(`, `decode(` or `getMultipartContent(` call can run before `checkAuthorization`.
3. Add `checkCharset` (allowlist, canonical-name comparison, 415 `WebServiceException`) and use it in `decodeContent`, `decode` (defensive), `getRequestContentType`, and all charset uses in `getMultipartContent` including the form-field branch (remove the silent fallback for disallowed charsets). Add an early header-only check of the request charset in `doPost`/`doPut`/`doPatch` after `getContents`.
4. Make the minimum members package-private for test access (`checkAuthorization`, `decodeContent`, `checkCharset`), keep production behavior unchanged otherwise.
5. Add the `test/` source folder, surefire and JUnit Jupiter configuration to `com.servoy.extensions/pom.xml` (and `.classpath` if needed), per section 3.3, and write the tests listed there. Keep the AGPL header on new files and tabs for indentation. Use `javax.servlet` and Java 17 only.
6. Run the compile (`mvn -f com.servoy.extensions/pom.xml test-compile` or the closest working equivalent) and the new tests. State exactly what could not be run (heavy pom, signing, third-party artifacts) and why.
7. Commit locally with message `SVY-21895 rest_ws: decode request bodies after authorization and restrict charsets [ai]`, with generic bullets and no payload details. Do not push. Do not mention in the commit that the issue is fixed; say the core allowlist is still required.

## 5. Acceptance criteria

- [ ] For POST, PUT and PATCH (JSON, XML, text, form, binary and multipart), no deserialization or content decoding happens before `checkAuthorization()` returned successfully. A failing check gives 401/403 and the decoder is not invoked (unit test, zero calls).
- [ ] `fromJSON` is not reachable from the servlet except through the lazily invoked decoder.
- [ ] Authenticated and anonymous-allowed requests behave as before (same arguments passed to the `ws_*` function; same status codes for valid requests).
- [ ] Request charsets outside the allowlist are rejected with the confirmed 4xx (default 415), never a 500, for the request charset and for per-part charsets (including the multipart form-field branch). No silent fallback to another charset for disallowed values.
- [ ] Charset aliases of allowlisted charsets (e.g. `utf8`, `latin1`) still work. Requests with no charset still default to UTF-8.
- [ ] The rejected charset value is not reflected in the response body.
- [ ] New unit tests exist under the confirmed test location, run under `mvn test` for `com.servoy.extensions` on lts_2024, and contain no exploit payloads or gadget class names.
- [ ] Code compiles at Java 17 with `javax.servlet`.
- [ ] Release note / commit text states that this does not fully resolve the vulnerability and that the servoy-client allowlist is still required.

## 6. Out of scope

- The class allowlist in servoy-client `JSONSerializerWrapper.getClassFromHint` (separate case, separate repo, lts_2024 first) and the audit of other `fromJSON` callers (serialize plugin etc.).
- Any change in `servoy_shared` / `servoy_base`.
- Forward-port of this plugin fix to `lts_2026`/`master`/`release` (separate step; note that those branches use jakarta and a different test layout).
- Changing response-side charset handling.
- Changing the anonymous-access semantics of solutions without `ws_authenticate`.

## 7. Open questions

| Question | Owner | Status |
|----------|-------|--------|
| 1. Confirm the charset allowlist: UTF-8, US-ASCII, ISO-8859-1, UTF-16, UTF-16BE, UTF-16LE, windows-1252. Add or remove entries (e.g. UTF-32, ISO-8859-15, windows-1250/1251)? Rejecting more risks breaking existing clients. | User | open |
| 2. Status for a disallowed charset: 415 (proposed) or 400? | User | open |
| 3. Test location on lts_2024: add a `test/` folder plus surefire/JUnit Jupiter to `com.servoy.extensions/pom.xml` (recommended, matches newer branches) or recreate the separate `com.servoy.extensions.test` module? Also confirm the CI (Jenkins) job for lts_2024 runs `mvn test` for this module. | User | open |
| 4. Is it acceptable that the response charset (from `Accept`/`Content-Type`) stays unrestricted in this change? | User | open |
| 5. Should the early header-only charset check run before or after `getClient` (before keeps unauthenticated callers from reaching the client pool with bad charsets)? Proposed: before `getClient`, right after `getContents`. | User | open |
| 6. Confirm that a 401/403 now replaces 400/500 for unauthenticated requests with malformed bodies is acceptable. | User | open |
| 7. Follow-up case for moving authorization ahead of multipart parsing (`getContents`)? | User | answered: no follow-up, fix it in this case (see 3.1b) |

Answers from the user: 1 = proposed allowlist; 2 = 415; 3 = `test/` folder + surefire + JUnit Jupiter in `com.servoy.extensions`; 4 = response charset stays unchanged (not a security issue, no deserialization); 5 = early charset check before `getClient`, as proposed; 6 = 401/403 instead of 400/500 for unauthenticated malformed bodies is fine; 7 = included in this case.
