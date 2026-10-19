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

## Paused task: constructor dependency injection

The CLI execution card (10) was paused to build a small resolver first.

Files:

- `src/main/java/dev/procrun/config/AutowireComponent.java`: runtime annotation
  targeting constructors. Its actual name is `AutowireComponent`, not `Autowired`.
- `src/main/java/dev/procrun/config/AutowireResolver.java`: explicit
  `Map<Class<?>, Class<?>>` bindings, assigned through Lombok's constructor.
  No scanning, field injection, caching, or lifecycle management. Each resolution
  will create new instances.

Implemented and reviewed by source inspection:

- `createAnnotatedConstructors(Class<?>)`: collects annotated public constructors.
- `findConstructor(Class<?>)`: selects exactly one annotated public constructor;
  rejects multiple annotated constructors. With no annotation, accepts exactly
  one public constructor and rejects zero or multiple public constructors.
- Removed unused `Modifier` import and clarified the fallback error message.

All other resolver methods still throw the standard unimplemented exception:

- Public `resolve(Class<T>)`.
- Recursive `resolve(Class<T>, Set<Class<?>>)`.
- `implementationFor(Class<?>)` — **resume here**.
- `resolveArguments(Constructor<?>, Set<Class<?>>)`.
- `instantiate(Constructor<?>, Object[])`.

No DI logic is connected to the application bootstrap or CLI. `RunCommand`
also contains the user's unfinished, unused private `run()` draft, which only
creates options. It is not an implemented execution entry point.

## Exact next learning step

Implement only `implementationFor(Class<?> type)`:

1. Look up the requested type in `bindings`.
2. If a binding exists, require `type.isAssignableFrom(implementation)`.
3. Without a binding, select the requested type itself.
4. Reject interfaces and abstract selected classes with descriptive
   `IllegalStateException`s.
5. Return the selected concrete class; do not construct it here.

Useful APIs: `Class.isAssignableFrom`, `Class.isInterface`, and
`Modifier.isAbstract(selected.getModifiers())`. Reintroduce the `Modifier`
import when this method actually uses it.

Examples: `JobFileReader -> TsvJobFileReader` is valid; requesting concrete
`TsvJobFileReader` without a binding is valid; requesting unbound `JobFileReader`
or binding it to `String` must fail.

## Verification and Git

`mvn -B -ntp clean verify` passed at this checkpoint: 64 tests, zero failures,
errors, or skipped tests. The DI constructor-selection behavior has not been
tested yet; the passing suite does not prove the unfinished DI methods work.

The previous concurrency commit initially could not be pushed because of DNS
restrictions. At checkpoint start, local Git reported the concurrency branch
aligned with its tracking reference. Network and `.git` write access have varied
between sessions: inspect errors and report blockers accurately. Never claim a
successful push without checking its result, and do not force push.
