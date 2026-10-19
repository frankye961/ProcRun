# Next work

Resume from [current.md](current.md). The user implements behavior step by step;
the assistant reviews it and adds tests when requested.

## Constructor-DI exercise complete

The resolver is implemented, tested, and connected to the application bootstrap.
`RunCommand` receives a `JobFileReader` and uses it to load input. Continue with
the remaining execution and reporting work in card 10.

Only public constructors are supported. Keep explicit bindings; do not add
classpath scanning, singleton scopes, field injection, or lifecycle machinery.

## Return to ProcRun cards

### Card 10: connect CLI execution and minimal reporting

- Input loading and option conversion are wired. Retain the resulting options,
  create the configured `ProcessRunner`, and execute the loaded batch.
- Report ID, status, available exit code, duration in milliseconds, available
  output path, and any error message in input order.
- Return 0 when all jobs succeed, otherwise 1; input errors need readable messages
  and a nonzero exit code. Existing usage errors remain exit code 2.
- Remove the CLI description's claim that execution is unimplemented once wired.
- Keep help and version working and add packaged CLI execution tests.
- The command's injected constructor dependency is `JobFileReader`.
  Create `ProcessRunner` only after CLI options are parsed;
  a resolver cannot supply its runtime configuration from type bindings alone.

### Card 11: execution timeouts

- Timeout begins when the child starts; queue waiting does not consume it.
- Request termination, wait the configured grace period, then force termination
  if needed. Wait for termination before finalizing a `TIMED_OUT` result.

### Card 12: completion and cancellation races

- Define which terminal outcome wins; finalize each job once.
- Test completion versus timeout/cancellation and shutdown during startup.

### Card 13: external runner shutdown

- Stop admission, cancel queued jobs, terminate direct children, and release
  resources. Repeated shutdown must be safe.
- Integrate shutdown with admission; the current executor is local to execute().
- Keep batch cleanup ownership clear. Cancellation currently abandons a batch
  through interruption; returning `CANCELLED` results is a later policy step.

### Card 14: CLI lifecycle and Ctrl+C

- Wire normal/error cleanup and a shutdown hook to the implemented runner
  lifecycle. Preserve interruption and verify direct-child cleanup.

### Cards 15–16: JVM observations

- Collect the runner's heap, live platform-thread count, and cumulative GC
  count/time using management APIs.
- Preserve documented `-1` unavailable values. Show snapshots with explicit
  units; avoid misleading deltas when values are unavailable.

### Card 17: final verification and documentation

- Run `mvn clean verify`, packaged help/version, and example execution.
- Exercise failures, large output, saturation, timeout, and Ctrl+C.
- Update README and these checkpoint documents. Keep the two-day project scope.
