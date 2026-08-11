# Repository Guidelines

## Project Structure

- `core/src/main/scala/promsafe4s/` contains the typed metric API and label encoders.
- `core/src/test/scala/promsafe4s/` contains core behavior tests.
- `derivation/src/main/scala-3/` and `derivation/src/main/scala-2.13/` contain version-specific derivation implementations.
- `derivation/src/test/scala/promsafe4s/derivation/` contains derivation tests.
- `build.sbt` defines the `core` and `derivation` modules and their shared settings.
- `README.md` documents the public API and usage examples.

## Build, Test, and Development Commands

Use sbt 2.0.2 as pinned in `project/build.properties`.

```bash
sbt --batch ';compile;test;++ 2.13.18;compile;test'
sbt --batch ';scalafmtSbt;scalafmtAll;scalafmtCheckAll'
```

The first command compiles and tests both Scala 3.3.7 and Scala 2.13.18. The second formats build and source files and verifies formatting.

## Coding Style & Naming

Write Scala with two-space indentation and follow the repository’s `.scalafmt.conf`. Prefer immutable values, small pure functions, and explicit algebraic data types. Use `PascalCase` for types, `camelCase` for methods and values, and descriptive metric/label names. Keep Scala 2.13 and Scala 3 behavior aligned when changing cross-version code.

## Testing Guidelines

Tests use MUnit with Cats Effect integration. Name suites after the subject, for example `LabelEncoderSuite` or `DerivationSuite`, and keep tests focused on observable behavior and compile-time typing guarantees. Run tests for both supported Scala versions before submitting changes.

## Commits and Pull Requests

This checkout has no Git history to establish an existing commit convention. Use short, imperative commit subjects (for example, `Add typed histogram labels`) and keep unrelated changes separate. Pull requests should explain the API or behavior change, identify cross-version impact, include documentation updates for public APIs, and report formatting plus Scala 2.13/Scala 3 test results.

## Documentation and Compatibility

Update `README.md` when adding or changing public APIs. Preserve binary/source compatibility where practical, and call out intentional breaking changes. Do not commit generated directories such as `target/`, `.bsp/`, `.metals/`, `.vscode/`, or `.idea/`.
