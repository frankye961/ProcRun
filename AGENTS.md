# ProcRun agent instructions

- Before resuming, read `docs/current.md`, `docs/completed.md`, and `docs/next.md`
  for the current checkpoint, accepted scope changes, and the user's learning workflow.
- Preserve the skeleton-only boundary until implementation is explicitly requested.
- Unimplemented concrete methods must throw `new UnsupportedOperationException("Not implemented yet")`.
- Do not add operational resource initialization, parsing, file I/O, process execution,
  concurrency, timeout/cancellation handling, shutdown hooks, monitoring collection,
  result aggregation, validation, defensive copies, or fake results to the skeleton.
- Keep the project small, single-module, Java 25 without preview features, and use explicit imports.
- Do not add dependencies without a concrete need.
- Do not silently expand the two-day scope. Initial supervision covers direct children only.
- Keep process-tree supervision, restarts, remote execution, dashboards, and virtual-thread
  comparisons outside this project.
- Add meaningful tests when implementing behavior; do not add placeholder tests.
- Do not commit or push unless asked.
