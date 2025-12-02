#!/bin/bash
# Returns the full version string (major.minor.patch)

FILE="version.properties"

if [ ! -f "$FILE" ]; then
  echo "version.properties not found!" >&2
  exit 1
fi

get_prop() {
    grep "^$1=" "$FILE" | cut -d'=' -f2 | tr -d '[:space:]'
}

MAJOR=$(get_prop major)
MINOR=$(get_prop minor)
PATCH=$(get_prop patch)

echo "$MAJOR.$MINOR.$PATCH"
