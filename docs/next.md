# Next work

Resume from [current.md](current.md). The user implements behavior step by step;
the assistant reviews it and adds tests when requested.

## Finish the constructor-DI exercise

1. Implement and review `implementationFor(Class<?>)` using the rules in current.md.
2. Implement `instantiate(Constructor<?>, Object[])`: invoke the selected public
   constructor, wrap reflective failures in `IllegalStateException`, and preserve
   the underlying constructor failure as the cause.
3. Implement `resolveArguments(...)`: resolve each parameter type in declaration
   order using the same active construction-path set.
4. Implement recursive `resolve(...)`: select implementation, detect circular
   dependencies on the active path, select constructor, resolve arguments, invoke,
   and cast to the requested type. Remove the path entry in `finally`, including
   on failure. Reusing a dependency in different branches is not a cycle.
5. Implement public `resolve(Class<T>)` as a fresh-resolution entry point.
6. Add meaningful tests for constructor selection, valid and invalid bindings,
   nested dependencies, cycles, constructor failures, repeated dependencies, and
   fresh instances. Do not claim DI is complete until these tests pass.

Only public constructors are supported. Keep explicit bindings; do not add
classpath scanning, singleton scopes, field injection, or lifecycle machinery.

## Return to ProcRun cards

### Card 10: connect CLI execution and minimal reporting

- Load jobs through `JobFileReader`, convert options, create the configured
  `ProcessRunner`, and execute the batch.
- Report ID, status, available exit code, duration in milliseconds, available
  output path, and any error message in input order.
- Return 0 when all jobs succeed, otherwise 1; input errors need readable messages
  and a nonzero exit code. Existing usage errors remain exit code 2.
- Remove the CLI description's claim that execution is unimplemented once wired.
- Keep help and version working and add packaged CLI execution tests.
- Before the custom-DI detour, the proposed constructor dependency was
  `JobFileReader`. Create `ProcessRunner` only after CLI options are parsed;
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
