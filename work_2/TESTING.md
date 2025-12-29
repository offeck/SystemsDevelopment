# Testing `work_2`

## JUnit tests (uses `examples/`)

- Tests live in `work_2/src/test/java/spl/lae/ExamplesTest.java` and run `spl.lae.Main` against each `examples/example*.json`, comparing the produced JSON to `examples/out*.json`.
- Run from `work_2/`: `mvn test`
- Run from repo root: `mvn -f work_2/pom.xml test`

## CLI example runner (no JUnit)

- `work_2/src/main/java/spl/lae/ExamplesTester.java` runs all `examples/example*.json` and compares in-memory results to `examples/out*.json`.
- After compiling, run from `work_2/`: `java spl.lae.ExamplesTester 4`

