#!/bin/bash

# Clean Daily Failure Analysis Runs and Artifacts
#
# This script deletes all workflow runs and artifacts from the daily-failure-analysis workflow.
# Use this to start from scratch while the tool is still in development.
#
# Usage:
#   ./scripts/clean-daily-runs.sh
#
# What it does:
#   1. Deletes all artifacts from daily-failure-analysis workflow (failure-analysis-history, build logs, reports)
#   2. Deletes all workflow runs from daily-failure-analysis workflow
#
# Requirements:
#   - gh CLI installed and authenticated
#   - Repository: quarkus-qe/test-failure-detector
#
# Note: This is destructive and cannot be undone!

set -e

REPO="quarkus-qe/test-failure-detector"
WORKFLOW="daily-failure-analysis.yaml"

echo "=========================================="
echo "Clean Daily Failure Analysis Runs"
echo "=========================================="
echo ""
echo "This will DELETE:"
echo "  - All artifacts from $WORKFLOW"
echo "  - All workflow runs from $WORKFLOW"
echo ""
read -p "Are you sure? (yes/no): " confirmation

if [ "$confirmation" != "yes" ]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo "Step 1: Deleting artifacts..."
echo "------------------------------"

# Get all artifacts and delete them
artifact_count=0
while IFS= read -r artifact_id; do
    if [ -n "$artifact_id" ]; then
        echo "  Deleting artifact ID: $artifact_id"
        gh api --method DELETE "/repos/$REPO/actions/artifacts/$artifact_id" || echo "    Failed to delete (may already be deleted)"
        ((artifact_count++))
    fi
done < <(gh api "/repos/$REPO/actions/artifacts?per_page=100" --jq '.artifacts[] | select(.workflow_run.workflow_name == "Daily Failure Analysis") | .id')

echo "  Deleted $artifact_count artifacts"

echo ""
echo "Step 2: Deleting workflow runs..."
echo "----------------------------------"

# Get all workflow runs and delete them
run_count=0
while IFS= read -r run_id; do
    if [ -n "$run_id" ]; then
        echo "  Deleting run ID: $run_id"
        gh api --method DELETE "/repos/$REPO/actions/runs/$run_id" || echo "    Failed to delete (may already be deleted)"
        ((run_count++))
    fi
done < <(gh api "/repos/$REPO/actions/workflows/$WORKFLOW/runs?per_page=100" --jq '.workflow_runs[] | .id')

echo "  Deleted $run_count workflow runs"

echo ""
echo "=========================================="
echo "Cleanup Complete!"
echo "=========================================="
echo ""
echo "Summary:"
echo "  - Artifacts deleted: $artifact_count"
echo "  - Workflow runs deleted: $run_count"
echo ""
echo "Next workflow run will start from scratch:"
echo "  - No previous history found"
echo "  - Will clone full lookback period (5 days)"
echo "  - Will get last 50 commits"
echo ""
