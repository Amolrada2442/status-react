#!/usr/bin/env bash

# This script takes care of generating/updating the nix files in the directories below

set -e

GIT_ROOT=$(cd "${BASH_SOURCE%/*}" && git rev-parse --show-toplevel)
_current_dir=$(cd "${BASH_SOURCE%/*}" && pwd)
inputs2nix=$(realpath --relative-to="$_current_dir" $GIT_ROOT/nix/tools/maven/maven-inputs2nix.sh)

_nix_shell_opts="-I nixpkgs=https://github.com/status-im/nixpkgs/archive/db492b61572251c2866f6b5e6e94e9d70e7d3021.tar.gz"

echo "Regenerating Nix files, this process should take 5-10 minutes"
nix-shell --run "$_current_dir/fetch-maven-deps.sh" \
          --pure $_current_dir/shell.nix
# nix-shell --run "$_current_dir/fetch-maven-deps.sh > $_inputs_file_path" \
#           --pure $_current_dir/shell.nix

pushd $_current_dir
# for f in `find . -name maven-inputs.txt`; do
#   dir=$(dirname $f)
#   echo "Generating $dir/default.nix from $f..."
#   $inputs2nix $f > $dir/default.nix
# done
echo "Done"
popd