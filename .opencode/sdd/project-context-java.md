# Project context — servoy-extensions (Java)

This is the Servoy **extensions** checkout: the server/client plugins and beans that ship
with Servoy (mail, http, file, rest_ws, broadcaster/datanotifybroadcaster, pdf, workflows,
oauth, etc.). SDD runs in Java repos here.

## Checkout layout

Workspace root: `servoy-extensions/`. Relevant projects (each an Eclipse `.project`):

- **`com.servoy.extensions`** — the one and only module; a plain Java/Maven project (NOT a
  PDE/OSGi plugin: no MANIFEST/build.properties; jar assembly + signing are done in the pom).
  Production plugin code lives under `src/com/servoy/extensions/plugins/<pluginname>/`.
  Eclipse project name (for the Eclipse MCP tools): `com.servoy.extensions`.
  **Unit tests live in this same module**, in a separate `test/` source folder
  (`<testSourceDirectory>test</testSourceDirectory>`), under
  `test/com/servoy/extensions/plugins/<pluginname>/` — the **same package** as the code under
  test, so they get package-private access with no inter-module dependency. (They briefly lived
  in a separate `com.servoy.extensions.test` module in early 2026.03; that was dropped because
  a plain-Maven reactor does not share a classpath across modules, so it needed a dependency on
  the signed main jar, which broke tests with a signer-info SecurityException and broke a
  release merge via the parent-version link. Older branches like 2024 may still have it.)
- **`servoy_shared`**, **`servoy_base`** — upstream Servoy API (interfaces such as
  `IServerPlugin`, `IServerAccess`, `IDataNotifyService`, `IDataNotifyListener`,
  `BroadcastFilter`, `IDataSet`, `com.servoy.j2db.util.Debug`/`Utils`). **Read-only here** —
  these are provided by the core (`servoy_shared` / `j2db_server`) checkout, not changed in
  this repo. If the real fix needs a core change, raise a separate SVY case against the core
  repo rather than editing these projects from here.
- `com.servoy.extensions.workflow`, `org.eclipse.dltk.javascript.rhino` — unrelated to this
  work.

## Stack / build

- **Java 21** (`maven.compiler.source/target` = 21), encoding UTF-8.
- `com.servoy.extensions` is built with **Maven** (the giant `pom.xml` assembles plugin/bean
  jars, copies third-party jars into `target/plugins_beans/plugins/...`, and signs them). It
  is **not** a plain quick build: it pulls many third-party artifacts and signs jars, so a
  full local `mvn package` is heavy and usually unnecessary for a single-plugin change.
- Tests run in `com.servoy.extensions`'s own `test` phase (surefire with the JUnit Platform
  provider, include `**/*Test.java`), BEFORE `package`/`verify` sign the jar — so they always
  run against the plain unsigned `target/classes`. JUnit 6 version via the parent property
  `junit.jupiter.version`; JaCoCo (`jacoco.version`) prepare-agent runs in `test` and the report
  in `verify` (`target/site/jacoco/jacoco.xml`, published to Jenkins via `recordCoverage`).
- RabbitMQ client: `com.rabbitmq:amqp-client` version is the pom property **`amqp.version`**
  (currently `5.34.0`). The broadcaster plugin builds against this.

### Preferred verification (fast, authoritative)

Use the **Eclipse MCP** against the live workspace rather than a Maven build whenever
possible — it is the source of truth for Java here and is always in sync with edits:

- Compile check: `eclipse-ide` `buildWorkspace` (or rely on incremental) then
  `getCompilationErrors` scoped to project `com.servoy.extensions`. Clean markers = compiles.
- Run tests: `eclipse-ide` `runJUnitTests` with `projectName: "com.servoy.extensions"` and
  `junitVersion: "6"`. If it reports no tests / a missing `Testable`, the build path is stale
  after a pom change: `updateMavenProject` + `buildWorkspace`, then re-run.
- A full Tycho/Maven reactor build is generally **not** run locally for a single-plugin
  change (slow, needs signing material and many third-party jars); CI does that. Prefer
  "Eclipse compile check only" or "affected plugin + its tests" at the pre-commit gate and
  say so.

## Testing conventions

- **JUnit 6 (Jupiter)** — `org.junit.jupiter.api.Test`,
  `org.junit.jupiter.api.Assertions.*`, `@BeforeEach`/`@AfterEach`. From Servoy 2026.03 LTS all
  tests are on JUnit 6 (both `DataNotifyBroadCasterRecoveryTest` and the older
  `RestWSServletMultipartDetectionTest` were migrated). Do not introduce JUnit 4 or Mockito
  unless a test genuinely needs it (and then note the new dependency in the test pom); default
  to plain Jupiter with hand-written fakes/stubs. Remember the Jupiter assertion-message order:
  the message is the **last** argument (`assertEquals(expected, actual, "msg")`), unlike JUnit 4.
- The Jupiter version is the parent pom property `junit.jupiter.version`; surefire runs the
  JUnit Platform provider (`surefire-junit-platform`).
- Test class name ends in `Test` (surefire include `**/*Test.java`).
- Place tests in `com.servoy.extensions/test` under the package of the class under test,
  e.g. broadcaster tests → `com.servoy.extensions/test/com/servoy/extensions/plugins/broadcaster/`.
- The RabbitMQ `Channel`/`Connection` are interfaces, so broadcaster logic can be tested
  with hand-written fakes (a fake `Channel` whose `basicPublish` throws / records sizes, a
  fake `Connection` toggling `isOpen()`), no live broker required. Keep unit tests broker-free.

## Code conventions

- Every source file starts with the Servoy **AGPL v3 header** (copy it from an existing file
  in the same project; keep the original copyright year on existing files).
- Tabs for indentation (match surrounding code).
- Logging/diagnostics go through `com.servoy.j2db.util.Debug` (`Debug.log/warn/error`), not
  `System.out` or a logging framework. `@SuppressWarnings("nls")` is used on classes with
  many string literals, as in the existing broadcaster classes.
- Server plugins implement `com.servoy.j2db.plugins.IServerPlugin`; settings come from
  `IServerAccess.getSettings().getProperty(...)` and every setting is documented in
  `getRequiredPropertyNames()`. If you add a new `amqpbroadcaster.*` setting, add its
  description there too.
- Preserve comments that explain intent/behaviour (e.g. "when a connection is recovered we
  don't know what we missed so the only thing to do is a full flush").

## The code in scope for this work

Plugin package: `com.servoy.extensions/src/com/servoy/extensions/plugins/broadcaster/`
- `DataNotifyBroadCaster` — `IServerPlugin`; builds the single `Connection` + single
  `Channel`, declares the `fanout` exchange, registers the consumer and the
  `DataNotifyListener`. Only a connection-level `RecoveryListener` exists today; there is no
  channel-level shutdown/recovery handling.
- `DataNotifyListener` — publish side; serializes `NotifyData` and `channel.basicPublish`es,
  with a `failedList` that only drains on connection recovery or a healthy publish.
- `NotifyData` (Serializable payload), `BroadcastMessage`, `IBroadcastMessageConsumer`,
  `IBroadcastMessageSender`.

Relevant core facts (verified, read-only from here):
- Core `SQLEngine.DistributeDataChange.run()` already strips large **byte[]** blobs
  (`> 16` bytes → `ValueFactory.createBlobMarkerValue()`) but **only for single-row INSERTs**,
  and does **not** cap large **String** values or large **pk datasets**. So the oversized
  (~22 MB) databroadcast that RabbitMQ rejects with `precondition_failed` can come from a big
  text column or a bulk pk set — the plugin must guard on the actual serialized size, not on
  column type.

## Git / commit

- Branch: `lts_2026` (origin `github.com/Servoy/servoy-extensions`). Do not push to a shared
  branch without being asked; commit locally.
- Commit messages **must** end with ` [ai]` and include the Jira key, per repo AGENTS.md:
  ```
  SVY-XXXXX <short description> [ai]

  - bullets

  Co-Authored-By: opencode <noreply@opencode.ai>
  ```
- Never amend a pushed commit; prefer a new commit. Keep behaviour-describing comments.
