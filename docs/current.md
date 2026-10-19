# Current checkpoint

Checkpoint: 2026-10-19. Branch: `feature/constructor-di`.

Read [completed.md](completed.md) for finished work and [next.md](next.md) for
the remaining cards. These documents preserve decisions from the conversation;
check the actual code and Git status before making changes.

## How the user wants to work

The user is implementing ProcRun to practise Java. Explain one small step at a
time, inspect their changes when they ask for review, and avoid implementing the
next steps unless asked. The assistant writes meaningful tests. Preserve unused
or unfinished areas as explicit skeletons rather than inventing fake results.
No commits or pushes without a new request; this checkpoint was explicitly
authorized for branching, committing, and pushing.

The original two-day scope remains the target. Direct children only; no process
trees, restarts, remote execution, dashboards, or virtual-thread comparisons.
The user explicitly requested Lombok and then a small custom constructor-DI
exercise despite those not being part of the original skeleton scope. Do not
expand that exercise into Spring or a general framework.

## Current task: finish CLI execution (card 10)

The constructor-DI exercise is implemented, tested, and connected to the
application bootstrap. Resume the remaining CLI execution and reporting work.

Files:

- `src/main/java/dev/procrun/config/AutowireComponent.java`: runtime annotation
  targeting constructors. Its actual name is `AutowireComponent`, not `Autowired`.
- `src/main/java/dev/procrun/config/AutowireResolver.java`: explicit
  `Map<Class<?>, Class<?>>` bindings, assigned through Lombok's constructor.
  No scanning, field injection, caching, or lifecycle management. Each resolution
  will create new instances.

Implemented and tested:

- `createAnnotatedConstructors(Class<?>)`: collects annotated public constructors.
- `findConstructor(Class<?>)`: selects exactly one annotated public constructor;
  rejects multiple annotated constructors. With no annotation, accepts exactly
  one public constructor and rejects zero or multiple public constructors.
- `implementationFor(Class<?>)`: validates explicit bindings and selects a
  concrete implementation, rejecting interfaces and abstract classes.
- `instantiate(...)`: invokes constructors and preserves the original failure cause.
- `resolveArguments(...)`: recursively resolves parameters in declaration order.
- Recursive `resolve(...)`: detects cycles on the active implementation path and
  removes entries in `finally`; public `resolve(...)` starts with a fresh set.
- `ProcRunApplication` creates the resolver with the explicit
  `JobFileReader -> TsvJobFileReader` binding and resolves `RunCommand`.
- `RunCommand` has an annotated constructor taking `JobFileReader`; its `call()`
  validates options and loads the selected file using the injected reader.

After loading valid input, `call()` still throws the standard unimplemented
exception. It does not start processes or print a report. The unused private
`run()` draft still only creates options; it is not an execution entry point.

## Exact next learning step

In `RunCommand.call()`, retain `RunnerOptions`, create `ProcessRunner` after
parsing options, execute the loaded jobs, and add minimal result reporting and
exit-code handling as described in [next.md](next.md). Implement only the next
small step requested by the user. Do not add scanning or extend the DI framework.

## Verification and Git

`mvn -B -ntp clean verify` passed: 79 tests, zero failures, errors, or skipped
tests. Resolver tests cover constructor selection, bindings, nested dependencies,
cycles, repeated dependencies, fresh instances, original constructor failures,
and application-command construction. CLI tests cover injected-reader use and
help/version bypassing input reads.

The previous concurrency commit initially could not be pushed because of DNS
restrictions. At checkpoint start, local Git reported the concurrency branch
aligned with its tracking reference. Network and `.git` write access have varied
between sessions: inspect errors and report blockers accurately. Never claim a
successful push without checking its result, and do not force push.
