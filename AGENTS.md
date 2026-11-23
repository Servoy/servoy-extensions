# AGENTS.md — Servoy Extensions Repository

This Git repository contains the **Servoy Extensions** — the server/client plugins and beans
that ship with Servoy (mail, http, file, rest_ws, datanotifybroadcaster/AMQP, pdf_output,
clientmanager, oauth, serial, spellcheck, amortization, workflows, …). The plugins build
against the shared Servoy runtime bundles (`servoy_base`, `servoy_shared`, …) which live in the
`servoy-client` / core checkout, not in this repo.

---

## 1. Repository Structure

This is a multi-module Maven project. Java 21 required. Since `2026.3.3` it is a **parent
reactor** (`com.servoy.extensions.parent`, root `pom.xml`, packaging `pom`). The reactor
currently has a single module:

### `com.servoy.extensions`
- **Type:** plain Java/Maven project (NOT a PDE/OSGi plugin — its `.project` has only the
  Maven + Java natures, there is no `MANIFEST.MF`/`build.properties`; the jar assembly and
  signing are done in the `pom.xml`).
- **Eclipse project name:** `com.servoy.extensions` (use this for the Eclipse MCP tools).
- **Main Role:** All production plugin/bean code. Each plugin lives under
  `src/com/servoy/extensions/plugins/<pluginname>/`; the big `pom.xml` assembles the signed
  plugin/bean jars into `target/plugins_beans/plugins/...` and copies the third-party jars.
- **Tests live in this same module**, in a separate `test/` source folder
  (`<testSourceDirectory>test</testSourceDirectory>`), under
  `test/com/servoy/extensions/plugins/<pluginname>/` — the **same package** as the code under
  test, so tests get package-private access without any inter-module dependency. This is the
  standard Maven layout (tests compiled and run in the `test` phase, which is **before**
  `package`/`verify` sign the jar, so tests always run against the plain, unsigned
  `target/classes` and never hit a signed-vs-unsigned split-package clash).
  - Historical note: tests briefly lived in a separate `com.servoy.extensions.test` module.
    That required a `<dependency>` on the main jar (plain Maven does not share a classpath
    across reactor modules — only Tycho resolves sibling bundles via the manifest) and a
    parent-version link that broke on a release merge, and it made the tests run against the
    **signed** jar (java.lang.SecurityException: signer information does not match …). Moving
    the tests into this module removed all three problems. From 2026.03 LTS up this is the
    layout; older branches (e.g. 2024) may still have the separate test project.
- **Key detail:** the build is heavy — it unpacks the doc-generator product, runs a Tycho
  `eclipse-run` doc-generation step, pulls many third-party artifacts and **signs** jars. A full
  local `mvn package` is rarely needed for a single-plugin change.

### Shared servoy bundles (read-only here)
`servoy_base`, `servoy_shared`, `servoy_headless_client`, `servoy_smart_client`,
`org.eclipse.dltk.javascript.rhino` and core classes such as `com.servoy.j2db.*` (e.g.
`SQLEngine` in `j2db_server`) come from the separate core checkout. In Eclipse they resolve as
**workspace project references** (see each module's `.classpath`); for the Maven CLI they are
pulled from the repository by the `not_dev` profile (see §3). **Do not edit these from this
repo** — a change there means a separate SVY case against the core repo.

---

## 2. Relationship to Other Repositories

| Repository | Relationship |
|-----------|-------------|
| `servoy-client` | Provides `servoy_base`, `servoy_shared`, `servoy_headless_client`, `servoy_smart_client` — the extensions compile and run against those bundles |
| `server` (`j2db_server`) | Server-side runtime (e.g. `SQLEngine`, databroadcast distribution) that the server plugins integrate with |
| `servoy-eclipse` | The Servoy Developer IDE; consumes the built plugin jars |

Changes to shared classes must be coordinated across those repos.

---

## 3. Build & the parent reactor

- **Build (CI / full):** `mvn -f pom.xml <goals>` from the repo root (the Jenkins pipeline runs
  exactly this). The unit tests run in `com.servoy.extensions`'s own `test` phase, and their
  surefire results are collected in CI (`**/target/surefire-reports/*.xml`).
- **Build system:** Maven; Tycho 5.x only for the doc-generator `eclipse-run` step in
  `com.servoy.extensions`. Java 21 (source + target), UTF-8.
- **The `not_dev` profile** (the parent + `com.servoy.extensions`) is auto-activated when **not**
  running under m2e/Eclipse (`<activation><property><name>!m2e.version</name></property></activation>`).
  It supplies the servoy bundles as repository dependencies for the Maven CLI. Their **versions
  are centralized** in the parent pom's `not_dev` `<dependencyManagement>`; the module's `not_dev`
  profile only lists the bundles it needs (versionless). The heavy docgen/unpack/sign/toolchains
  build stays in `com.servoy.extensions`.

### ⚠️ Local `mvn` build caveat (version not yet published)

The servoy bundle dependencies resolve to **`${project.version}`** (currently
`2026.3.3-SNAPSHOT`). That snapshot is only published once a full Servoy core build for the
release has run, so a **local `mvn` CLI build cannot resolve the bundles and will fail** — this
is expected, not a setup bug. Two consequences:

- **Verify locally through the Eclipse workspace**, which resolves the bundles via project
  references: `tools["eclipse-ide"].getCompilationErrors` (scoped to `com.servoy.extensions`)
  and `tools["eclipse-ide"].runJUnitTests` (project `com.servoy.extensions`, passing
  `junitVersion: "6"`). CI does the full reactor build.
- If you need a quick CLI sanity build, point a **throwaway copy** at the last published
  version (e.g. `2026.3.2-SNAPSHOT`); never commit a downgraded version — the real poms stay at
  `${project.version}`.

- **No separate lint/typecheck** — compilation validates the code.

---

## 4. Commit Message Convention

- Commits related to a Jira case must include the case number: `SVY-21803 short description`
- AI-assisted commits must end with `[ai]`: `SVY-21803 short description [ai]`
- Add a `Co-Authored-By: opencode <noreply@opencode.ai>` trailer on AI-assisted commits.
- Never amend a commit that has already been pushed; prefer a new commit. Only amend a
  **local-only** commit (verify with `git branch -r --contains <sha>` / `git log @{u}..HEAD`).
- Keep comments that describe behaviour/intent; only remove clearly stale or trivial ones.

---

## 5. Prioritize Eclipse MCP Tools Over Standard Tools

This workspace is a multi-project Eclipse environment, so **always prioritize Eclipse-specific
MCP/PDE tools** over general-purpose command-line or filesystem tools, to keep the Eclipse
index, builder and classpath in sync.

### ⚠️ Everything runs through Code Mode — READ THIS FIRST

The Eclipse MCP servers (`eclipse-coder`, `eclipse-ide`, `eclipse-git`, `eclipse-pde`,
`eclipse-runner`, `eclipse-context`) and the other MCP tools (`memory`, `time`) are exposed
**only through Code Mode**. There is **no direct top-level tool** for any of them.

- To call any Eclipse/MCP tool you MUST write JavaScript inside the **`execute`** tool and call
  it by its exact catalog `path`, using bracket notation:
  `await tools["eclipse-coder"].replaceString({ ... })`,
  `await tools["eclipse-ide"].getCompilationErrors({ ... })`,
  `await tools["eclipse-git"].gitStatus({ ... })`.
- **NEVER** call these as if they were plain tools (e.g. `eclipse-coder_replaceString` or
  `tools.eclipse_ide.getCompilationErrors`). Those names do not exist in Code Mode; the call
  fails with *"No tool named … is currently available."* When that happens, do **not** fall back
  to the built-in `edit`/`write` — fix the call by wrapping it in `execute` with the bracket form.
- The Code Mode catalog is **partial**. If a tool is not shown, find it with `search(...)`
  **inside** an `execute` script (synchronous — call it without `await`), then call it by the
  returned `path`. Do not guess tool names.
- Throughout this document, a tool written as `eclipse-coder_replaceString` is shorthand for
  `tools["eclipse-coder"].replaceString(...)` called via `execute`.

**The only tools called directly (not through Code Mode):** the built-in `read`, `grep`, `glob`
and `shell`. Everything else goes through `execute`.

**Every file inside an Eclipse workspace project MUST be edited through the `eclipse-coder`
tools**, never the built-in `edit`/`write` (they write behind Eclipse's back and desync the
editor, JDT model and undo history). This is a hard rule.

- **File Reading:** `tools["eclipse-ide"].readProjectResource` (plus `getSource`,
  `getFilteredSource`, `getMethodSource`, `getClassOutline`) instead of the generic `read` tool.
- **File Writing & Creating:** `tools["eclipse-coder"].createFile` / `replaceFileContent`
  instead of the generic `write` tool.
- **File Editing:** `tools["eclipse-coder"].applyPatch`, `insertIntoFile`, `replaceString`,
  `applyTextEdits` or `deleteLinesInFile` instead of the generic `edit` tool.
- **File / Class Searching:** `tools["eclipse-ide"].fileSearch`, `fileSearchRegExp`,
  `searchTypes`, `searchMethods` instead of generic `grep`/`glob` for Java facts.
- **Git Operations:** `tools["eclipse-git"]` (`gitStatus`, `gitDiff`, `gitCommit`, …) instead of
  shell `git` where practical.
- **Testing:** `tools["eclipse-ide"].runJUnitTests` (discover with `findTestClasses`) over shell
  test commands. (The main module's `mvn` build is heavy and, see §3, often not resolvable
  locally.)

The repo root itself is an Eclipse project (**`servoy-extensions`**, a plain project with no
Java nature — it just holds the reactor `pom.xml`, `Jenkinsfile`, this file, `docs/`,
`opencode.json` and `.opencode/`), and `com.servoy.extensions` is imported as its own
Eclipse project. So **every file in this checkout lives inside some Eclipse project** — edit
through the `eclipse-coder` tools so the IDE stays in sync. For the root-level files use
`projectName: "servoy-extensions"` (e.g.
`readProjectResource`/`replaceString` on `AGENTS.md`, `pom.xml`, `Jenkinsfile`). The built-in
`edit`/`write` are a last resort only for a path that is genuinely outside every imported
project; when in doubt, use `eclipse-coder`.

---

## 6. Post-Modification Compilation & Quick-Fix Loop

After any code change via the Eclipse MCP tools, run a self-verification loop:

1. **Check for errors:** `tools["eclipse-ide"].getCompilationErrors(...)` scoped to the touched
   project(s).
2. **Review quick fixes** on any reported marker.
3. **Apply quick fixes** that are safe via `tools["eclipse-ide"].executeQuickFix` (`markerId` +
   `proposalIndex`).
4. **Re-check** until the workspace is clean. Zero compilation errors must remain.

---

## Testing

Unit tests live **in the `com.servoy.extensions` module itself**, in its `test/` source folder
(`<testSourceDirectory>test</testSourceDirectory>`), under
`test/com/servoy/extensions/plugins/<pluginname>/`, in the **same package** as the code under
test for package-private access. JUnit 6 / Jupiter; surefire runs the JUnit Platform provider
(`surefire-junit-platform`), include pattern `**/*Test.java`, Jupiter version from the parent
pom property `junit.jupiter.version`. They run in the module's `test` phase (before the jar is
assembled/signed), so they always compile and run against the plain unsigned `target/classes`.

- **Run locally:** `tools["eclipse-ide"].runJUnitTests` with `projectName =
  "com.servoy.extensions"`, the test class/package, and `junitVersion: "6"`. Use this, not `mvn`
  — see the §3 caveat (the `2026.3.x-SNAPSHOT` servoy bundles may not be published yet for a CLI
  build, but Eclipse resolves them via project references). If a run reports no tests / a missing
  `Testable`, the build path is stale: `updateMavenProject` + `buildWorkspace` on
  `com.servoy.extensions`, then re-run.
- **In CI:** the Jenkins build (`mvn -f pom.xml …`) runs the tests in the `test` phase; results
  are collected from `**/target/surefire-reports/*.xml`.
- **Broker-free fakes:** RabbitMQ `Channel`/`Connection`/`ShutdownListener` are interfaces, so
  the AMQP broadcaster is tested with hand-written fakes — no live broker. Keep unit tests
  broker-free and deterministic (drive async recovery via observable state, not wall-clock sleeps).

### Test rules: real unit tests, no integration tests

We want **pure JUnit 6 unit tests**, not integration tests. They must be fast and deterministic and
must run in CI without any external service. The goal is a high coverage number that is cheap to
keep green.

- **Real I/O is fine when it is cheap.** Time is not lost on the file system, so plugins like
  `file` should be tested against real files and folders created in a `@TempDir`. Do not mock
  `java.io`/`java.nio` just to avoid touching disk. The same goes for in-memory work such as
  parsing, serialization, converters, validators, XML/JSON, excel/pdf/tab export into a temp folder.
- **Mock or fake everything that needs a network, a broker or a running service.** That means
  RabbitMQ/AMQP (broadcaster), SMTP/IMAP/POP (mail), HTTP endpoints (http, oauth, openid, jwt key
  fetches), LDAP, UDP/serial sockets, databases and any Servoy server/client runtime
  (`IServerAccess`, `IClientPluginAccess`, `IApplication`, ...). Never open a real connection
  from a test and never depend on a service being reachable.
- **Prefer Mockito for interfaces.** For types where a fake reads clearer (RabbitMQ
  `Channel`/`Connection`), write a small hand-written fake that only **records** calls, like the
  `Fake*` classes in the broadcaster tests. The decision logic must stay in production code, the
  fake must not re-implement it.
- **Test the plugin's own logic, not the library.** Assert on our branching, validation, error
  handling and mapping. Do not re-test Apache HttpClient, Jackson, POI, etc.
- **No `Thread.sleep` or wall-clock timing.** Drive async work through observable state, a
  same-thread executor, or an injectable clock/backoff. If production code hard-codes a delay that
  makes a test slow, add a small package-private seam (setter/constructor) instead of waiting.
- **Package-private access instead of public API changes.** Tests live in the same package as the
  code under test, so use package-private seams or reflection for private paths rather than
  widening production visibility without need.
- **If a class cannot be mocked** (final type or static call inside a shared Servoy core bundle),
  do not fall back to an integration test. Wrap it behind a small seam in this repo, or when the
  blocker is in core, raise a separate SVY case against the core repo.
- **Naming and layout:** `test/com/servoy/extensions/plugins/<plugin>/<ClassUnderTest>Test.java`,
  JUnit Jupiter, one behaviour per test method with a descriptive name.
- **Coverage is reported per build.** JaCoCo runs in the module's `test`/`verify` phases and
  Jenkins shows it through `recordCoverage`. The raw data is archived as build artifacts:
  `**/target/site/jacoco/**` (jacoco.xml + HTML report) and `**/target/jacoco.exec`. When you
  add tests, check the coverage of the touched plugin in the Jenkins build.

### SVY-21803 — AMQP databroadcaster channel recovery + payload size guard

Covers the channel-level recovery and the serialized-payload size guard added to
`DataNotifyBroadCaster` / `DataNotifyListener` (plugin
`com.servoy.extensions.plugins.broadcaster`). Reported via support ticket SCS-3537: a
broker `precondition_failed` on an oversized (~22 MB) databroadcast closed the single shared
channel, which was never rebuilt, silently stopping pod-wide databroadcast until restart.

| Test class | Package | Type | Runner |
|-----------|---------|------|--------|
| `DataNotifyBroadCasterRecoveryTest` | `com.servoy.extensions.plugins.broadcaster` (in `com.servoy.extensions/test`) | Plain JUnit 6 (Jupiter), broker-free (fakes `FakeChannel`/`FakeConnection`/`FakeServerAccess`) | CI: surefire in the `test` phase (`**/*Test.java`). Locally: `eclipse-ide_runJUnitTests` (project `com.servoy.extensions`, `junitVersion: "6"`) |

Notes:
- The test drives the real private `installShutdownListener` / `rebuildChannel` /
  `getEffectiveMaxMessageSize` paths through reflection and a same-thread executor, and asserts
  on the `FakeChannel`/`FakeConnection` recordings — the fakes only record, the flush-vs-row
  decision stays in production (`DataNotifyListener`).
- Covers the 10 acceptance criteria of `docs/SVY-21803-amqp-channel-recovery-size-guard.spec.md`
  plus the non-reentrant/FIFO `failedList` drain. The bounded-rebuild tests incur the production
  2 s backoff (observable-state assertions, not timing).

> The spec-driven-development pipeline this repo uses records its per-repo context in
> `.opencode/sdd/project-context-java.md` (Java variant). Keep it in sync with this file.

---

## 7. Jira API

For anything Jira — reading, creating, updating, commenting, linking, searching (JQL), assigning
or transitioning issues — load the **`servoy-jira`** skill (global opencode skill) and follow its
instructions. It holds the connection details (base URL, `ATLASSIAN_AUTH_BASIC` auth), the
per-OS command recipes and the ADF templates. Always load the skill first rather than
hand-rolling the REST calls.
