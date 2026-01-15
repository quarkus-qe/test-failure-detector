#!/bin/bash
set -euo pipefail

WORKFLOW_OR_RUN_URL="$1"
OUTPUT_DIR="$2"

extract_repo() {
    local url="$1"
    # Extract owner/repo from URLs like:
    # https://github.com/quarkus-qe/quarkus-test-suite/actions/workflows/daily.yaml
    # https://github.com/quarkus-qe/quarkus-test-suite/actions/runs/20946183562
    echo "$url" | sed -E 's|https://github.com/([^/]+/[^/]+)/.*|\1|'
}

extract_workflow() {
    local url="$1"
    # Extract workflow file name from URLs like:
    # https://github.com/quarkus-qe/quarkus-test-suite/actions/workflows/daily.yaml
    if echo "$url" | grep -q "/workflows/"; then
        echo "$url" | sed -E 's|.*/workflows/([^/]+)$|\1|'
    else
        echo ""
    fi
}

extract_run_id() {
    local url="$1"
    # Extract run ID from URLs like:
    # https://github.com/quarkus-qe/quarkus-test-suite/actions/runs/20946183562
    if echo "$url" | grep -q "/runs/"; then
        echo "$url" | sed -E 's|.*/runs/([0-9]+).*|\1|'
    else
        echo ""
    fi
}

REPO=$(extract_repo "$WORKFLOW_OR_RUN_URL")

if [ -z "$REPO" ]; then
    echo "Error: Could not extract repository from URL: $WORKFLOW_OR_RUN_URL" >&2
    exit 1
fi

echo "Repository: $REPO"

# Determine if this is a workflow URL or a run URL
RUN_ID=$(extract_run_id "$WORKFLOW_OR_RUN_URL")

if [ -z "$RUN_ID" ]; then

    WORKFLOW_FILE=$(extract_workflow "$WORKFLOW_OR_RUN_URL")

    if [ -z "$WORKFLOW_FILE" ]; then
        echo "Error: Could not extract workflow file from URL: $WORKFLOW_OR_RUN_URL" >&2
        exit 1
    fi

    echo "Workflow file: $WORKFLOW_FILE"
    echo "Finding latest completed run for workflow..."

    # Get the latest completed run for this workflow
    RUN_ID=$(gh run list \
        --repo "$REPO" \
        --workflow "$WORKFLOW_FILE" \
        --status completed \
        --limit 1 \
        --json databaseId \
        --jq '.[0].databaseId')

    if [ -z "$RUN_ID" ] || [ "$RUN_ID" = "null" ]; then
        echo "Error: No completed runs found for workflow $WORKFLOW_FILE" >&2
        exit 1
    fi

    echo "Latest completed run ID: $RUN_ID"
else
    echo "Run ID: $RUN_ID"
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Create a temporary download directory
TEMP_DOWNLOAD_DIR="$OUTPUT_DIR/.temp-download"
mkdir -p "$TEMP_DOWNLOAD_DIR"

echo "Downloading artifacts to temporary directory '$TEMP_DOWNLOAD_DIR'"

cd "$TEMP_DOWNLOAD_DIR"

if ! gh run download "$RUN_ID" --repo "$REPO"; then
    echo "Error: Failed to download artifacts from run $RUN_ID" >&2
    rm -rf "$TEMP_DOWNLOAD_DIR"
    exit 1
fi

echo "Successfully downloaded artifacts from run $RUN_ID"

echo "Extracting artifacts..."

for artifact_dir in "$TEMP_DOWNLOAD_DIR"/*; do
    if [ -d "$artifact_dir" ]; then
        artifact_name=$(basename "$artifact_dir")
        echo "Processing artifact: $artifact_name"

        # Find and extract all zip files in this artifact directory
        find "$artifact_dir" -name "*.zip" -o -name "*.tar.gz" -o -name "*.tgz" | while read -r archive; do
            echo "  Extracting: $(basename "$archive")"
            case "$archive" in
                *.zip)
                    unzip -q "$archive" -d "$artifact_dir"
                    rm "$archive"
                    ;;
                *.tar.gz|*.tgz)
                    tar -xzf "$archive" -C "$artifact_dir"
                    rm "$archive"
                    ;;
            esac
        done

        if [ -d "$artifact_dir" ]; then
            cp -r "$artifact_dir"/* "$OUTPUT_DIR/" 2>/dev/null || true
        fi
    fi
done

rm -rf "$TEMP_DOWNLOAD_DIR"

echo "All artifacts extracted to: $OUTPUT_DIR"

echo "OUTPUT_DIR=$(cd "$OUTPUT_DIR" && pwd)"
