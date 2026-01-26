# Test Failure Detector

## CLI tool

This CLI tool can be used to:

* detect if a project source (such as a local directory or a GitHub workflow) contains FailSafe reports with failures
* bisect [Quarkus git project](https://github.com/quarkusio/quarkus.git) and determine which git commit caused the failure
* report the bisect result

## Installation

### Download Pre-built Binary (Recommended)

Download the latest binary executable for your operating system from the [releases page](https://github.com/quarkus-qe/test-failure-detector/releases):

**Linux (x86_64):**
```bash
# Download the binary
curl -L -o test-failure-detector https://github.com/quarkus-qe/test-failure-detector/releases/latest/download/test-failure-detector-linux-x86_64

# Make it executable
chmod +x test-failure-detector

# Move to a directory in your PATH (optional)
sudo mv test-failure-detector /usr/local/bin/
```

**macOS (Apple Silicon M-series):**
```bash
# Download the binary
curl -L -o test-failure-detector https://github.com/quarkus-qe/test-failure-detector/releases/latest/download/test-failure-detector-macos-aarch64

# Make it executable
chmod +x test-failure-detector

# Move to a directory in your PATH (optional)
sudo mv test-failure-detector /usr/local/bin/
```

After installation, verify it works:
```bash
test-failure-detector --help
```

### Build from Source

If you prefer to build from source:

```bash
git clone https://github.com/quarkus-qe/test-failure-detector.git
cd test-failure-detector
./mvnw package -Dnative
# Binary will be at: target/test-failure-detector-*-runner
```

### Prerequisites

The pre-built binaries include all dependencies. For building from source, you need:

* [GitHub CLI](https://cli.github.com/)
* valid GitHub credentials with read permissions to the GitHub project with the tested workflow and Quarkus GitHub project:
  * export `GITHUB_TOKEN`
  * or authenticate with the [gh auth](https://cli.github.com/manual/gh_auth) command
* git
* JDK 25
* Maven 3.9.9+

## Quick Start

### Example usage for a specific workflow run

```bash
test-failure-detector GITHUB_ACTION_ARTIFACTS https://github.com/quarkus-qe/quarkus-test-suite/actions/runs/20946183562
```

### Example usage for a specific workflow

```bash
test-failure-detector GITHUB_ACTION_ARTIFACTS https://github.com/quarkus-qe/quarkus-test-suite/actions/workflows/daily.yaml
```

### Example usage for local test results

```bash
test-failure-detector LOCAL_DIRECTORY target/failsafe-reports
```

### Command-Line Options

The tool supports various options to customize the failure detection and analysis:

```bash
test-failure-detector [PROJECT_SOURCE_TYPE] [PROJECT_SOURCE] [OPTIONS]
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
test-failure-detector LOCAL_DIRECTORY target/failsafe-reports --output-file=failure-report.txt
```

#### Analyze GitHub workflow with custom lookback period

```bash
test-failure-detector GITHUB_ACTION_ARTIFACTS \
  https://github.com/quarkus-qe/quarkus-test-suite/actions/workflows/daily.yaml \
  --lookback-days=14 \
  --output-file=reports/$(date +%Y-%m-%d)-failures.txt
```

#### Analyze failures from a specific date range

```bash
# Check failures between Jan 1 and Jan 15, 2026
test-failure-detector LOCAL_DIRECTORY target/failsafe-reports \
  --from=2026-01-15 \
  --lookback-days=14 \
  --bisect-strategy=BINARY
```

#### Use linear search strategy (for debugging)

```bash
test-failure-detector LOCAL_DIRECTORY target/failsafe-reports \
  --bisect-strategy=LINEAR
```

#### Daily build workflow (typical usage)

```bash
# Run daily, tracking history and identifying new failures
test-failure-detector GITHUB_ACTION_ARTIFACTS \
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

## Creating a Release

To create a new release:

1. Go to the [Actions tab](https://github.com/quarkus-qe/test-failure-detector/actions/workflows/release.yaml)
2. Click "Run workflow"
3. Enter the version number (e.g., `1.2.0`)
4. Optionally add release notes
5. Click "Run workflow"

The workflow will automatically:
- Update the version in pom.xml
- Commit and push the change
- Create a git tag
- Create a GitHub release
- Build native executables for Linux x86_64 and macOS aarch64
- Upload all native executables as release assets

The entire process takes about 10-15 minutes (native builds run in parallel).

## Daily Failure Analysis

This repository includes an automated daily analysis workflow that monitors the [Quarkus Test Suite daily builds](https://github.com/quarkus-qe/quarkus-test-suite/actions/workflows/daily.yaml) for test failures.

### How It Works

The workflow runs automatically every day at 12:00 PM UTC (after the Quarkus Test Suite daily builds finish at ~11:30 AM UTC) and:
1. Analyzes the latest Quarkus Test Suite daily build
2. Identifies test failures and their root causes
3. Uses git bisect to find the upstream Quarkus commit that introduced each failure
4. Maintains history across runs to track when failures are introduced and resolved
5. Stores both the detailed report and history as artifacts

**Important limitations**:

- **JDK-specific failures**: The Quarkus Test Suite daily builds test against multiple JDK versions (currently JDK 17 and 21). However, this failure analysis workflow bisects and tests using only JDK 21. Therefore, **failures that only occur on JDK 17 may not be detected**. If a test fails only on JDK 17 but passes on JDK 21, the bisect process will not reproduce the failure. Note that many failures are not JDK-specific and will be detected regardless.

- **Platform-specific failures**: The analysis runs on Linux x86_64 only. **Windows-specific failures and aarch64-specific failures will not be detected**. The Quarkus Test Suite daily builds include Windows and potentially other architectures, but the bisect process currently only reproduces the Linux x86_64 environment.

- **Native builder image**: For native mode tests, the tool always uses `ubi9-quarkus-mandrel-builder-image:jdk-21` regardless of which builder image was used in the original test suite run. Failures specific to other builder images (e.g., `ubi-quarkus-mandrel-builder-image:jdk-21` with UBI8 compatibility) may not be reproduced.

- **Quarkus CLI tests**: The tool always uses Quarkus CLI version 999-SNAPSHOT when running tests. This means it can only detect failures when testing with the latest Quarkus snapshot. CLI-specific failures related to released versions cannot be bisected.

- **Matrix specifics ignored**: The Quarkus Test Suite runs with various matrix configurations (different profiles, JDK versions, builder images). The bisect process uses a single configuration: JDK 21 on Linux x86_64, with either JVM or Native mode detection based on the artifact name.

These limitations may be addressed in future versions of the tool.

### Viewing Results

Results are available as workflow artifacts:
- **failure-analysis-report-[run-number]**: The complete failure analysis report
- **failure-analysis-history**: The persistent history file used for tracking failures over time

To view the latest results:
1. Go to [Actions > Daily Failure Analysis](https://github.com/quarkus-qe/test-failure-detector/actions/workflows/daily-failure-analysis.yaml)
2. Click on the most recent run
3. Download the artifacts to view the report and history

### Manual Triggers

You can manually trigger the analysis with custom parameters:

1. Go to [Actions > Daily Failure Analysis](https://github.com/quarkus-qe/test-failure-detector/actions/workflows/daily-failure-analysis.yaml)
2. Click "Run workflow"
3. Configure optional parameters:
   - **from**: Reference date to look back from (format: `dd.MM.yyyy` or `yyyy-MM-dd`, default: today)
   - **lookback_days**: Number of days to look back for upstream changes (default: `2`)
4. Click "Run workflow"

**Default behavior**: Analyzes the last 48 hours of changes (today looking back 2 days).

**Custom example**: Set `from=25.01.2026` and `lookback_days=3` to analyze 3 days of changes starting from January 25th, 2026.
