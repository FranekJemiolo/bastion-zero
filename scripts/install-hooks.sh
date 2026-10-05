#!/usr/bin/env bash
set -eo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$REPO_ROOT"

echo "🔧 Configuring git pre-commit hooks for Bastion Zero..."
chmod +x .githooks/pre-commit
git config core.hooksPath .githooks

echo "✅ Pre-commit hooks installed successfully!"
echo "   Active hook: .githooks/pre-commit"
