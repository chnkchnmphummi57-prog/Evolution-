#!/usr/bin/env sh
cd "$(dirname "$0")" || exit 1
command -v python3 >/dev/null 2>&1 || { echo "Install Python 3 first."; exit 1; }
python3 -m pip install --quiet numpy
python3 train.py "$@"
