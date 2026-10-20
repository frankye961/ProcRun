# ProcRun

For the implementation checkpoint, see [completed work](docs/completed.md),
[current work and resume point](docs/current.md), and [next steps](docs/next.md).

ProcRun is a small Java CLI exercise for practising concurrency, JVM internals,
core Java, and system programming. TSV input, CLI option conversion, and bounded
concurrent process execution with results are implemented and wired to the CLI.
Help and version work, while every unfinished operation throws
`UnsupportedOperationException("Not implemented yet")`.

Keep implementation feasible in two days. The intended runner reads TSV jobs,
limits concurrent OS processes, uses a bounded waiting queue, captures each
process's output in a separate file, enforces per-process timeouts, and reports
exit codes and execution durations. It will clean up its direct child processes
during shutdown and expose basic measurements of its own JVM.

The initial scope covers **direct child processes**. Process-tree supervision,
automatic restarts, remote execution, dashboards, and virtual-thread comparisons
are outside this project.

## Packages and ownership

| Package | Responsibility |
| --- | --- |
| `dev.procrun` | Minimal picocli main-method bootstrap. |
| `dev.procrun.cli` | CLI declarations, input coordination, batch execution, and result reporting; later add monitoring and runner shutdown. |
| `dev.procrun.model` | Data records and terminal job statuses. |
| `dev.procrun.config` | Small constructor-injection resolver using explicit bindings. |
| `dev.procrun.input` | Load job definitions; own and close opened input resources. |
| `dev.procrun.execution` | Own bounded execution resources, direct children, output capture, and cleanup. |
| `dev.procrun.monitoring` | Observe the runner JVM, independently of process execution. |

`RunCommand` receives `JobFileReader` and `ProcessRunner` through constructor
injection. `DiFactory` supplies the reader binding; the resolver constructs the
concrete runner without configuration or resources. Parsed `RunnerOptions` are
passed to `execute(jobs, options)` for each invocation.
Calling `execute()` creates a fixed worker pool and a bounded waiting queue,
captures merged output in separate files, and returns results in input order.
Admission blocks interruptibly when the queue is full, without starting extra
children on the submitting thread. Startup failures become individual failed
results. On interruption or failure, unfinished tasks are cancelled and the runner
waits for workers to clean up their children. Cleanup continues through repeated
interruptions and preserves the interruption signal. Each batch releases its own
executor. Per-process timeouts request normal termination, allow the configured
grace period, then force termination if needed. Results are finalized after
termination; timed-out jobs keep `TIMED_OUT` status regardless of the final exit
code. Runner-wide shutdown remains unimplemented.
`JobSpec` validates nonblank IDs and executables and requires a nonempty command
list. Other records remain skeletons; no defensive copies are made.

## Build and dependencies

Use JDK **25** and Maven **3.6.3 or newer**, without preview features.

| Dependency | Pinned version | Purpose |
| --- | --- | --- |
| `info.picocli:picocli` | 4.7.7 | CLI declarations, parsing, built-in help and version. |
| `org.projectlombok:lombok` | 1.18.42 | Generate dependency constructors with `@RequiredArgsConstructor`; provided scope and explicit annotation processor. |
| `org.junit.jupiter:junit-jupiter` | 6.0.3 | Future meaningful JUnit Platform tests; test scope only. |

| Maven plugin | Pinned version | Purpose |
| --- | --- | --- |
| `maven-compiler-plugin` | 3.15.0 | Compile with release 25. |
| `maven-surefire-plugin` | 3.5.5 | Run tests using JUnit Platform when tests are added. |
| `maven-shade-plugin` | 3.6.2 | Package runtime dependencies and the main-class manifest in `target/procrun.jar`. |

Stable releases were checked against the [picocli documentation](https://picocli.info/),
[JUnit release notes](https://docs.junit.org/6.0.3/release-notes.html),
[Maven Compiler documentation](https://maven.apache.org/plugins/maven-compiler-plugin/),
[Surefire plugin versions](https://maven.apache.org/surefire/maven-surefire-plugin/plugins.html),
and [Shade plugin details](https://maven.apache.org/plugins/maven-shade-plugin/plugin-info.html).
JUnit 6 requires Java 17 or newer; it retains the `org.junit.jupiter` coordinates.

JDK APIs need no extra dependencies: use `ProcessBuilder`, `Process`, and
`ProcessHandle` for processes, `java.util.concurrent` for concurrency,
`java.nio.file` for files, `java.time` for timing, and `java.lang.management`
for JVM observations. Add imports only when actually used.

```sh
mvn clean verify
java -jar target/procrun.jar --help
java -jar target/procrun.jar --version
```

Reader tests cover valid TSV parsing, input order, blank lines, literal UTF-8
arguments, empty argument fields, empty files, invalid rows, duplicate IDs,
physical line numbers, and missing files. Model tests cover `JobSpec` validation.
CLI tests cover defaults, custom options, invalid limits and numeric inputs,
help, version, injected input reading and execution, result reports, exit codes,
and readable input errors.
Resolver tests cover constructor selection, bindings, recursive resolution, cycles,
fresh instances, and constructor failure causes. Runner tests cover
exit codes, startup failures, continued execution, durations, output capture,
literal arguments, stdin EOF, unique files, empty batches, bounded parallelism,
queue saturation, and interruption during admission and result collection.

## Run jobs

```sh
java -jar target/procrun.jar --parallel 3 --queue-capacity 20 --timeout-seconds 10 --grace-seconds 2 --output-dir output examples/jobs.tsv
```

These option values are also the defaults. Durations remain numeric CLI fields
and are converted to `Duration` when creating `RunnerOptions`. Parallelism,
queue capacity, and timeout must be positive; the grace period may be zero.
Invalid limits produce a usage error and exit code 2. The application resolves
`RunCommand` with an injected reader and runner. Valid input is executed with
bounded concurrency. Reports preserve input order and include status, available
exit code, duration in milliseconds, output path, and any startup error.
Exit code 0 means all jobs succeeded (including an empty batch); 1 means a job
failed or the input could not be read. Input errors print a readable message to
stderr. Built-in help and version do not read input or start children.

Timeout starts after process launch; queue waiting does not consume it. Reported
duration includes termination cleanup. A timed-out job makes the CLI exit 1, and
later jobs can still run. Shutdown-hook cleanup and JVM monitoring are unfinished.

## TSV input

Each UTF-8 line describes one job, with actual tab characters between fields:

```text
job-id<TAB>executable<TAB>argument-1<TAB>argument-2...
```

`<TAB>` above denotes a literal tab, not those five characters. Arguments are
individual TSV fields; the executable is the first element of the resulting
command argument list. There is no shell interpretation, quote parsing, variable
expansion, or pipeline support. `examples/jobs.tsv` contains actual tabs and three
harmless Linux commands (`echo`, `uname`, and `pwd`), without a header.

## Input and execution policies

These policies are implemented in the reader, runner, and CLI.

- Skip blank lines, including lines containing only whitespace.
- Require a nonblank job ID on every job row.
- When the waiting queue is full, block admission until space becomes available.
  Waiting for admission must be interruptible; it must not cause extra processes
  to run beyond the configured parallelism.
- A process startup failure produces a `FAILED` result with an error message and
  no exit code. Other jobs can continue.

- Return results in input order, ignoring skipped blank lines. Jobs may finish
  in any order; input order makes the final report predictable and easy to
  compare with the TSV file. This does not require serial execution.
- Require a nonblank executable, reject duplicate job IDs, and report malformed
  input through `IOException` with the physical line number and reason.
- Preserve argument fields literally, including empty and trailing empty fields;
  do not trim arguments.

## Two-day implementation checklist

- Day 1: implement input, bounded execution, output capture, and results.
- Day 2: implement timeouts, shutdown cleanup, JVM observations, and meaningful tests.

## Design questions to resolve

- How will output be drained or redirected so a full process pipe cannot block completion?
- How will blocking admission respond to interruption and runner shutdown?
- How will races between timeout, normal completion, and cancellation produce one terminal outcome?
- Which component owns each process, stream, executor, and file, and who closes them after failure or shutdown?

JVM snapshots describe this runner, not child JVMs. Heap sizes use bytes; GC
count/time are cumulative across collectors, with time in milliseconds. `-1`
means unavailable, including an undefined heap maximum. If any collector lacks
a count or time, the corresponding aggregate is unavailable. Live platform-thread
count excludes virtual threads.
