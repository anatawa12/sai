#!/bin/sh

RHINO_REPO="https://github.com/mozilla/rhino.git"

shallow_fetch_branch() {
  git remote set-branches --add "$1" "$2"
  git fetch "$@"
}

main() {
  set -eu
  shallow_fetch_branch origin rhino-master --depth 1
  git checkout rhino-master
  git remote add rhino "$RHINO_REPO"
  shallow_fetch_branch rhino master
  git reset --hard rhino/master
  git remote rm rhino
  git push origin rhino-master
  if [ "$(git rev-parse master)" = "$(git rev-parse rhino-master)" ]; then
    echo "already up to date!"
    return 0;
  fi
  gh pr create \
    --base master \
    --head rhino-master \
    --title "sync rhino" \
    --body "sync rhino" \
    --fill
}

main
