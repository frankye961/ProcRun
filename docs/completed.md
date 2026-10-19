# Completed work

Checkpoint: 2026-10-19. Read [current.md](current.md) before resuming and
[next.md](next.md) for the remaining work.

## ProcRun cards 1–9

- Created a single-module Maven project targeting Java 25 without preview features.
- Added picocli, JUnit, and pinned compiler, Surefire, and Shade plugins.
- Later added Lombok at the user's explicit request, with provided scope and
  explicit annotation processing. `@RequiredArgsConstructor` supplies dependency constructors.
- Agreed input policies: skip blank lines; require nonblank IDs and executables;
  reject duplicate IDs using exact, case-sensitive comparison; preserve literal
  argument fields, including trailing empty fields; no shell interpretation.
- Implemented UTF-8 TSV reading with physical line numbers and `IOException`
  context for malformed input. `JobSpec` validates individual job invariants.
- Implemented numeric CLI limit validation and conversion into `RunnerOptions`.
  Defaults: parallelism 3, queue capacity 20, timeout 10 seconds, grace 2 seconds,
  output directory `output`. Grace may be zero; other numeric limits must be positive.
- Created controlled Java child fixtures and meaningful tests.
- Implemented process launching, stdin closure, merged stdout/stderr capture,
  unique files under the configured output directory, and complete results.
- Startup failures become `FAILED` results with an error message and absent PID
  and exit code; other jobs continue. All nonzero exit codes map to `FAILED`.
- Measure duration with `System.nanoTime()` and expose it as `Duration`.
- Implemented fixed parallelism with `ThreadPoolExecutor` and a bounded
  `ArrayBlockingQueue`. Saturation blocks admission interruptibly; jobs never
  run on the submitting thread as a fallback.
- Preserve input order when collecting futures. This also fulfills card 9.
- Cancel unfinished tasks and wait for workers and direct children during batch
  cleanup. Repeated interruptions do not abandon child termination.
- Added comments explaining admission, result ordering, and cleanup ownership.

Bounded admission and cleanup are recorded in commit `8360b46`. GitHub remote:
`https://github.com/frankye961/ProcRun.git`.

## Important limits

The CLI still does not run jobs: `RunCommand.call()` validates options and throws
`UnsupportedOperationException("Not implemented yet")`. Help and version work.
Process timeouts, graceful termination policy, external runner-wide shutdown,
shutdown hooks, and JVM monitoring are not implemented. Batch cleanup is already
implemented; it is distinct from the unfinished public `shutdown()` entry point.

The existing suite has 64 tests covering input, models, CLI options, child
fixtures, execution, bounded admission, and interruption. The DI work described
in [current.md](current.md) has no behavioral tests yet.
