#!/bin/bash

# Bump version script for katalan project
# Usage: ./bump-version.sh [patch|minor|major]
#        SKIP_TESTS=1 ./bump-version.sh patch   (skip local mvn test)
# - patch: 1.0.0 -> 1.0.1
# - minor: 1.0.0 -> 1.1.0
# - major: 1.0.0 -> 2.0.0

set -e

# Check if bump type is provided
BUMP_TYPE=${1:-patch}

if [[ ! "$BUMP_TYPE" =~ ^(patch|minor|major)$ ]]; then
    echo "❌ Invalid bump type: $BUMP_TYPE"
    echo "Usage: $0 [patch|minor|major]"
    exit 1
fi

# Must release from an up-to-date, clean main
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "❌ You are on '$CURRENT_BRANCH'. Switch to main before bumping."
    exit 1
fi
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "❌ Working tree has uncommitted changes. Commit or stash them first."
    exit 1
fi

# Run unit tests before releasing (skip with SKIP_TESTS=1)
if [ "${SKIP_TESTS:-0}" != "1" ]; then
    if command -v mvn >/dev/null 2>&1; then
        echo "🧪 Running unit tests..."
        mvn -q test || { echo "❌ Tests failed. Fix them or re-run with SKIP_TESTS=1."; exit 1; }
    else
        echo "⚠️  mvn not found, skipping local tests (CI will still run them)."
    fi
fi

# Get current version from pom.xml
CURRENT_VERSION=$(grep -m 1 "<version>" pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

if [ -z "$CURRENT_VERSION" ]; then
    echo "❌ Could not extract current version from pom.xml"
    exit 1
fi

echo "📦 Current version: $CURRENT_VERSION"

# Parse version parts
IFS='.' read -ra VERSION_PARTS <<< "$CURRENT_VERSION"
MAJOR=${VERSION_PARTS[0]}
MINOR=${VERSION_PARTS[1]}
PATCH=${VERSION_PARTS[2]}

# Bump version based on type
case $BUMP_TYPE in
    patch)
        PATCH=$((PATCH + 1))
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
echo "🚀 New version: $NEW_VERSION"

# Update version in pom.xml
echo "📝 Updating pom.xml..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/<version>$CURRENT_VERSION<\/version>/<version>$NEW_VERSION<\/version>/" pom.xml
else
    # Linux
    sed -i "s/<version>$CURRENT_VERSION<\/version>/<version>$NEW_VERSION<\/version>/" pom.xml
fi

# Verify the change
UPDATED_VERSION=$(grep -m 1 "<version>" pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
if [ "$UPDATED_VERSION" != "$NEW_VERSION" ]; then
    echo "❌ Version update failed. Expected $NEW_VERSION, got $UPDATED_VERSION"
    exit 1
fi

echo "✅ Version updated in pom.xml"

# Release notes -> CHANGELOG.md
echo "📝 Generating release notes..."
NOTES=$(scripts/release-notes.sh "v$CURRENT_VERSION" HEAD 2>/dev/null || scripts/release-notes.sh)
TODAY=$(date +%Y-%m-%d)
CHANGELOG_ENTRY="## [$NEW_VERSION] - $TODAY

$NOTES
"

if [ ! -f CHANGELOG.md ]; then
    printf '# Changelog\n\nAll notable changes to katalan are documented here.\n\n' > CHANGELOG.md
fi

# Insert the new entry right above the first existing "## [" section (newest first)
ENTRY_FILE=$(mktemp)
TMP_FILE=$(mktemp)
printf '%s\n' "$CHANGELOG_ENTRY" > "$ENTRY_FILE"
awk -v entry_file="$ENTRY_FILE" '
    function emit() { while ((getline line < entry_file) > 0) print line; close(entry_file) }
    !inserted && /^## \[/ { emit(); inserted=1 }
    { print }
    END { if (!inserted) emit() }
' CHANGELOG.md > "$TMP_FILE"
mv "$TMP_FILE" CHANGELOG.md
rm -f "$ENTRY_FILE"

echo ""
echo "----------------------------------------"
echo "$CHANGELOG_ENTRY"
echo "----------------------------------------"

# Let the user polish the notes before they are committed (interactive shells only)
if [ -t 0 ]; then
    read -r -p "✏️  Edit CHANGELOG.md before committing? [y/N] " EDIT_ANSWER
    if [[ "$EDIT_ANSWER" =~ ^[Yy]$ ]]; then
        ${EDITOR:-vi} CHANGELOG.md
    fi
fi

# Git operations
echo "📌 Creating git tag v$NEW_VERSION..."
git add pom.xml CHANGELOG.md
git commit -m "Bump version to $NEW_VERSION"
git tag -a "v$NEW_VERSION" -m "Release v$NEW_VERSION"

echo "🚢 Pushing to remote..."
git push origin main
git push origin "v$NEW_VERSION"

echo ""
echo "✨ Version bump completed successfully!"
echo "   Old version: $CURRENT_VERSION"
echo "   New version: $NEW_VERSION"
echo "   Tag: v$NEW_VERSION"
echo ""
echo "🎉 GitHub Actions will now run tests, build, and create a release with these notes."
