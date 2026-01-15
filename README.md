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

### Example usage

TODO

## GitHub Workflow

TODO
