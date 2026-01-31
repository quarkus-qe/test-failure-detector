#!/bin/bash

# Clean Daily Failure Analysis Runs and Artifacts
#
# This script cancels running workflows and deletes all workflow runs and artifacts
# from the daily-failure-analysis workflow. Use this to start from scratch while
# the tool is still in development.
#
# Usage:
#   ./scripts/clean-daily-runs.sh
#
# What it does:
#   1. Cancels any in-progress workflows
#   2. Deletes all artifacts from daily-failure-analysis workflow (failure-analysis-history, build logs, reports)
#   3. Deletes all workflow runs from daily-failure-analysis workflow
#
# Requirements:
#   - gh CLI installed and authenticated
#   - Repository: quarkus-qe/test-failure-detector
#   - Token with 'repo' and 'workflow' scopes
#
# Note: This is destructive and cannot be undone!

set -e

REPO="quarkus-qe/test-failure-detector"
WORKFLOW="daily-failure-analysis.yaml"

echo "=========================================="
echo "Clean Daily Failure Analysis Runs"
echo "=========================================="
echo ""
echo "This will:"
echo "  - Cancel any running workflows"
echo "  - Delete all artifacts from $WORKFLOW"
echo "  - Delete all workflow runs from $WORKFLOW"
echo ""
read -p "Are you sure? (yes/no): " confirmation

if [ "$confirmation" != "yes" ]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo "Step 1: Cancel running workflows..."
echo "------------------------------------"

# First, cancel any in_progress workflows
cancel_count=0
while IFS= read -r run_id; do
    if [ -n "$run_id" ]; then
        echo "  Cancelling run ID: $run_id"
        gh run cancel "$run_id" --repo "$REPO" || echo "    Failed to cancel (may already be done)"
        ((cancel_count++))
    fi
done < <(gh run list --workflow="$WORKFLOW" --repo "$REPO" --status in_progress --json databaseId --jq '.[].databaseId')

echo "  Cancelled $cancel_count workflow runs"

if [ $cancel_count -gt 0 ]; then
    echo "  Waiting 5 seconds for cancellations to complete..."
    sleep 5
fi

echo ""
echo "Step 2: Deleting artifacts..."
echo "------------------------------"

# First, collect all artifact IDs (to avoid API inconsistency during deletion)
artifact_ids=()
while IFS= read -r run_id; do
    if [ -n "$run_id" ]; then
        # Get artifacts for this run
        while IFS= read -r artifact_id; do
            if [ -n "$artifact_id" ]; then
                artifact_ids+=("$artifact_id")
            fi
        done < <(gh api "/repos/$REPO/actions/runs/$run_id/artifacts?per_page=100" --jq '.artifacts[].id')
    fi
done < <(gh api "/repos/$REPO/actions/workflows/$WORKFLOW/runs?per_page=100" --jq '.workflow_runs[].id')

# Now delete all collected artifacts
artifact_count=0
for artifact_id in "${artifact_ids[@]}"; do
    echo "  Deleting artifact ID: $artifact_id"
    gh api --method DELETE "/repos/$REPO/actions/artifacts/$artifact_id" || echo "    Failed to delete (may already be deleted)"
    ((artifact_count++))
done

echo "  Deleted $artifact_count artifacts"

echo ""
echo "Step 3: Deleting workflow runs..."
echo "----------------------------------"

# First, collect all workflow run IDs (to avoid API inconsistency during deletion)
run_ids=()
while IFS= read -r run_id; do
    if [ -n "$run_id" ]; then
        run_ids+=("$run_id")
    fi
done < <(gh api "/repos/$REPO/actions/workflows/$WORKFLOW/runs?per_page=100" --jq '.workflow_runs[].id')

# Now delete all collected runs
run_count=0
for run_id in "${run_ids[@]}"; do
    echo "  Deleting run ID: $run_id"
    gh api --method DELETE "/repos/$REPO/actions/runs/$run_id" || echo "    Failed to delete (may already be deleted)"
    ((run_count++))
done

echo "  Deleted $run_count workflow runs"

echo ""
echo "=========================================="
echo "Cleanup Complete!"
echo "=========================================="
echo ""
echo "Summary:"
echo "  - Workflow runs cancelled: $cancel_count"
echo "  - Artifacts deleted: $artifact_count"
echo "  - Workflow runs deleted: $run_count"
echo ""
echo "Next workflow run will start from scratch:"
echo "  - No previous history found"
echo "  - Will clone full lookback period"
echo ""
