# work_1 CI workflow

This repository has a GitHub Actions workflow (`.github/workflows/work-1-ci.yml`) that validates the `work_1` assignment whenever relevant files change.

## When it runs
- Pull requests or pushes that touch `work_1/**` or the workflow file itself.
- Manual trigger via **Run workflow** in the GitHub Actions UI.

## What it does
1) Checks out the repo.  
2) Installs `build-essential` and `valgrind` (needed for the leak test noted in `Assignment1_SPL25_v1.1.pdf`, Section 8).  
3) `make clean` (matches the PDF’s final integration flow).  
4) `make debug` to build the non-interactive test harness.  
5) Executes `./bin/dj_manager`, captures output to `actual_output.txt`, and `diff`s against `test_output.txt` (expected output for non-interactive mode).  
6) Runs `make clean && make test-leaks` to check for leaks with valgrind.  
7) `make clean && make release` to confirm a release build is producible for interactive `-I/-A` usage.

## How to reproduce locally
From the repository root:
```bash
cd work_1
make clean
make debug
./bin/dj_manager | tee actual_output.txt
diff -u test_output.txt actual_output.txt
make clean
make test-leaks   # requires valgrind
make clean
make release
```

## Common failure hints
- Build errors: check Rule of 5 implementations, ownership semantics, and includes.  
- Diff mismatches: compare your run to `test_output.txt`; focus on missing destructors, ownership bugs, or ordering differences.  
- Valgrind failures: re-run `make test-leaks` locally and fix leaks/dangling pointers before pushing.  
- If you need interactive testing, run `./bin/dj_manager -I -A` after a release build (not part of CI).
