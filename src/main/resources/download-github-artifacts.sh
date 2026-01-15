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

# Get list of artifacts for this run using GitHub API
ARTIFACTS_JSON=$(gh api "repos/$REPO/actions/runs/$RUN_ID/artifacts" --jq '.artifacts')

if [ -z "$ARTIFACTS_JSON" ] || [ "$ARTIFACTS_JSON" = "null" ]; then
    echo "Error: No artifacts found for run $RUN_ID" >&2
    rm -rf "$TEMP_DOWNLOAD_DIR"
    exit 1
fi

# Download each artifact using curl (works better with snap than gh run download)
echo "$ARTIFACTS_JSON" | jq -c '.[]' | while read -r artifact; do
    ARTIFACT_NAME=$(echo "$artifact" | jq -r '.name')
    ARTIFACT_ID=$(echo "$artifact" | jq -r '.id')

    echo "Downloading artifact: $ARTIFACT_NAME (ID: $ARTIFACT_ID)"

    # Create artifact directory
    ARTIFACT_DIR="$TEMP_DOWNLOAD_DIR/$ARTIFACT_NAME"
    mkdir -p "$ARTIFACT_DIR"

    # Download using gh api
    if ! gh api "repos/$REPO/actions/artifacts/$ARTIFACT_ID/zip" > "$ARTIFACT_DIR/$ARTIFACT_NAME.zip"; then
        echo "Warning: Failed to download artifact $ARTIFACT_NAME" >&2
        continue
    fi

    echo "Downloaded: $ARTIFACT_NAME"
done

echo "Successfully downloaded artifacts from run $RUN_ID"

echo "Extracting artifacts..."

for artifact_dir in "$TEMP_DOWNLOAD_DIR"/*; do
    if [ -d "$artifact_dir" ]; then
        artifact_name=$(basename "$artifact_dir")
        echo "Processing artifact: $artifact_name"

        # GitHub API returns a zip containing another zip/tar, extract both levels
        for archive in "$artifact_dir"/*.zip "$artifact_dir"/*.tar.gz "$artifact_dir"/*.tgz; do
            [ -f "$archive" ] || continue
            echo "  Extracting outer archive: $(basename "$archive")"
            case "$archive" in
                *.zip)
                    unzip -q -o "$archive" -d "$artifact_dir"
                    rm "$archive"
                    ;;
                *.tar.gz|*.tgz)
                    tar -xzf "$archive" -C "$artifact_dir"
                    rm "$archive"
                    ;;
            esac
        done

        # Extract any nested archives that were inside
        for archive in "$artifact_dir"/*.zip "$artifact_dir"/*.tar.gz "$artifact_dir"/*.tgz; do
            [ -f "$archive" ] || continue
            echo "  Extracting inner archive: $(basename "$archive")"
            case "$archive" in
                *.zip)
                    unzip -q -o "$archive" -d "$artifact_dir"
                    rm "$archive"
                    ;;
                *.tar.gz|*.tgz)
                    tar -xzf "$archive" -C "$artifact_dir"
                    rm "$archive"
                    ;;
            esac
        done

        echo "  Copying extracted files from $artifact_name to output directory"
        if [ -d "$artifact_dir" ]; then
            cp -r "$artifact_dir"/* "$OUTPUT_DIR/" 2>/dev/null || true
        fi
    fi
done

rm -rf "$TEMP_DOWNLOAD_DIR"

echo "All artifacts extracted to: $OUTPUT_DIR"

echo "OUTPUT_DIR=$(cd "$OUTPUT_DIR" && pwd)"
