#!/bin/bash

# Bump version script for katalan project
# Usage: ./bump-version.sh [patch|minor|major]
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

# Git operations
echo "📌 Creating git tag v$NEW_VERSION..."
git add pom.xml
git commit -m "Bump version to $NEW_VERSION"
git tag "v$NEW_VERSION"

echo "🚢 Pushing to remote..."
git push origin main
git push origin "v$NEW_VERSION"

echo ""
echo "✨ Version bump completed successfully!"
echo "   Old version: $CURRENT_VERSION"
echo "   New version: $NEW_VERSION"
echo "   Tag: v$NEW_VERSION"
echo ""
echo "🎉 GitHub Actions will now build and create a release automatically."
