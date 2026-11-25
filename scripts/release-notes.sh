#!/bin/bash
# Generate markdown release notes from git commits between two refs.
#
# Usage: scripts/release-notes.sh [FROM_REF] [TO_REF]
#   FROM_REF  default: the latest v* tag reachable from TO_REF (excluding TO_REF itself)
#   TO_REF    default: HEAD
#
# Commits are grouped by the first word of the subject:
#   add/feat/new/implement          -> ✨ Added
#   fix/bugfix/hotfix/perbaikan     -> 🐛 Fixed
#   anything else                   -> 🔧 Changed
# "Bump version" and merge commits are skipped.

set -e

TO_REF=${2:-HEAD}
FROM_REF=$1

if [ -z "$FROM_REF" ]; then
    # Latest tag strictly before TO_REF (so it works both before and after tagging)
    FROM_REF=$(git describe --tags --abbrev=0 --match 'v*' "${TO_REF}^" 2>/dev/null || true)
fi

if [ -n "$FROM_REF" ]; then
    RANGE="$FROM_REF..$TO_REF"
else
    RANGE="$TO_REF"
fi

ADDED=""
FIXED=""
CHANGED=""

while IFS=$'\t' read -r HASH SUBJECT; do
    [ -z "$HASH" ] && continue
    case "$SUBJECT" in
        "Bump version"*|"Merge pull request"*|"Merge branch"*) continue ;;
    esac
    LINE="- ${SUBJECT} (${HASH})"
    FIRST_WORD=$(echo "$SUBJECT" | awk '{print tolower($1)}' | sed 's/[:(].*//')
    case "$FIRST_WORD" in
        add|adds|added|feat|feature|new|implement|implements) ADDED+="$LINE"$'\n' ;;
        fix|fixes|fixed|bugfix|hotfix|perbaikan)              FIXED+="$LINE"$'\n' ;;
        *)                                                    CHANGED+="$LINE"$'\n' ;;
    esac
done < <(git log --no-merges --format='%h%x09%s' "$RANGE")

if [ -z "$ADDED$FIXED$CHANGED" ]; then
    echo "_No notable changes._"
    exit 0
fi

[ -n "$ADDED" ]   && printf '### ✨ Added\n%s\n'   "$ADDED"
[ -n "$FIXED" ]   && printf '### 🐛 Fixed\n%s\n'   "$FIXED"
[ -n "$CHANGED" ] && printf '### 🔧 Changed\n%s\n' "$CHANGED"

if [ -n "$FROM_REF" ]; then
    echo "_Changes since ${FROM_REF}._"
fi
