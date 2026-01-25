# Test Failure Detector

## CLI tool

This CLI tool can be used to:

* detect if a project source (such as a local directory or a GitHub workflow) contains FailSafe reports with failures
* bisect [Quarkus git project](https://github.com/quarkusio/quarkus.git) and determine which git commit caused the failure
* report the bisect result

### Prerequisites

* [GitHub CLI](https://cli.github.com/)
* valid GitHub credentials with read permissions to the GitHub project with the tested workflow and Quarkus GitHub project:
  * export `GITHUB_TOKEN`
  * or authenticate with the [gh auth](https://cli.github.com/manual/gh_auth) command
* git
* Quarkus CLI
* JDK 25
* Maven 3.9.9+

### Example usage

#### Example usage for a specific workflow run

```bash
quarkus build --native
./target/test-failure-detector-999-SNAPSHOT-runner GITHUB_ACTION_ARTIFACTS https://github.com/quarkus-qe/quarkus-test-suite/actions/runs/20946183562
```

#### Example usage for a specific workflow

```bash
quarkus build --native
./target/test-failure-detector-999-SNAPSHOT-runner GITHUB_ACTION_ARTIFACTS https://github.com/quarkus-qe/quarkus-test-suite/actions/workflows/daily.yaml
```

### Command-Line Options

The tool supports various options to customize the failure detection and analysis:

```bash
./test-failure-detector [PROJECT_SOURCE_TYPE] [PROJECT_SOURCE] [OPTIONS]
```

#### Project Source Types

- **`LOCAL_DIRECTORY`** - Analyze test failures in a local directory (default)
- **`GITHUB_ACTION_ARTIFACTS`** - Analyze test failures from GitHub Actions workflow artifacts

#### Available Options

**Output and Logging:**
- `--output-file=<path>` - Save the failure analysis report to a file (in addition to stdout)
- `--debug` - Enable debug logging (default: false)

**Failure History:**
- `--history-file=<path>` - Path to the failure history JSON file (default: `failure-history.json`)
  - Stores information about previously detected failures
  - Used to distinguish NEW vs EXISTING vs RESOLVED failures

**Git Bisect Configuration:**
- `--bisect-strategy=<BINARY|LINEAR>` - Algorithm for finding culprit commits (default: `BINARY`)
  - `BINARY`: Binary search - O(log n) time, 3-5x faster for typical builds
  - `LINEAR`: Linear search - O(n) time, more predictable but slower
  - Binary search automatically falls back to linear if too many build failures occur

- `--lookback-days=<days>` - Number of days to look back for upstream changes (default: `7`)
  - Determines how far back to clone Quarkus repository commits
  - Only relevant for first run; subsequent runs use history

- `--from=<date>` - Reference date/time to look back from (default: now)
  - Accepts formats: `dd.MM.yyyy` (e.g., `10.1.2026`), `yyyy-MM-dd` (e.g., `2026-01-10`), or ISO-8601 instant
  - Combined with `--lookback-days`, defines the commit search window

### Usage Examples

#### Analyze local test failures and save report to file

```bash
./test-failure-detector LOCAL_DIRECTORY target/failsafe-reports --output-file=failure-report.txt
```

#### Analyze GitHub workflow with custom lookback period

```bash
./test-failure-detector GITHUB_ACTION_ARTIFACTS \
  https://github.com/quarkus-qe/quarkus-test-suite/actions/workflows/daily.yaml \
  --lookback-days=14 \
  --output-file=reports/$(date +%Y-%m-%d)-failures.txt
```

#### Analyze failures from a specific date range

```bash
# Check failures between Jan 1 and Jan 15, 2026
./test-failure-detector LOCAL_DIRECTORY target/failsafe-reports \
  --from=2026-01-15 \
  --lookback-days=14 \
  --bisect-strategy=BINARY
```

#### Use linear search strategy (for debugging)

```bash
./test-failure-detector LOCAL_DIRECTORY target/failsafe-reports \
  --bisect-strategy=LINEAR
```

#### Daily build workflow (typical usage)

```bash
# Run daily, tracking history and identifying new failures
./test-failure-detector GITHUB_ACTION_ARTIFACTS \
  https://github.com/quarkus-qe/quarkus-test-suite/actions/workflows/daily.yaml \
  --history-file=daily-history.json \
  --output-file=reports/daily-$(date +%Y-%m-%d).txt \
  --lookback-days=7
```

### How It Works

1. **Failure Detection**: Scans Maven Failsafe reports for test failures
2. **Root Cause Analysis**: Groups failures by common root causes
3. **History Tracking**: Compares with previous runs to identify NEW, EXISTING, or RESOLVED failures
4. **Git Bisect**: For NEW failures, performs binary search through Quarkus commits to find the culprit
   - Clones only recent commits (based on lookback window)
   - Builds Quarkus with `./mvnw -T1C -DskipTests ...` for each commit
   - Runs the failing test against each build
   - Identifies the first commit where the test fails
5. **Reporting**: Generates a detailed report with upstream commits and PR numbers

## GitHub Workflow

TODO
