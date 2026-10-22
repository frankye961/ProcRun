# Next work

Resume from [current.md](current.md). The user implements behavior step by step;
the assistant reviews it and adds tests when requested.

## Constructor-DI exercise complete

The resolver is implemented, tested, and connected to the application bootstrap.
`RunCommand` receives `JobFileReader` and `ProcessRunner` and is fully wired for
batch execution and reporting. Timeout handling is also implemented; continue with card 12.

Only public constructors are supported. Keep explicit bindings; do not add
classpath scanning, singleton scopes, field injection, or lifecycle machinery.

## Return to ProcRun cards

### Card 10 complete

Input loading, runtime options, injected runner execution, ordered reports,
exit codes, and readable input errors are implemented and tested. The resolver
creates the runner before parsing; CLI runtime options are passed separately to
`execute(jobs, options)`. Keep that configuration per invocation.

### Card 11 complete: execution timeouts

- Timed process waits exclude queue time. Timeout requests normal termination,
  waits the configured grace period, and forces termination when needed.
- TIMED_OUT results are finalized only after termination, with full cleanup
  duration. Tests cover graceful and forced paths, continuation, and large durations.

### Card 12 complete: completion and cancellation races

- A job that finishes normally before its timeout reports its actual exit status.
- Once its timeout expires, it remains TIMED_OUT even if it exits during grace.
- Interrupting batch execution returns no partial result list. The interruption
  propagates after unfinished tasks are cancelled and active direct children end.
- Tests cover interruption during result collection/admission and while waiting
  for graceful termination. The latter verifies forced cleanup finishes before
  the caller receives InterruptedException.
- Public shutdown during startup is deferred to card 13, since shutdown is not
  implemented yet.

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
