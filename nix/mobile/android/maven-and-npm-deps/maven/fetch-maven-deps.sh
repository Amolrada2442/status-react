#!/usr/bin/env bash

# This script takes care of generating/updating the nix files in this directory.
# For this, we start with a clean cache (in ./.m2~/repository/) and call cljsbuild
#  to cause it to download all the artifacts. At the same time, we note them
#  in lein-project-deps-maven-inputs.txt so that we can use that as an input
#  to maven-inputs2nix.sh

GIT_ROOT=$(cd "${BASH_SOURCE%/*}" && git rev-parse --show-toplevel)
_current_dir=$(cd "${BASH_SOURCE%/*}" && pwd)
_repo_path='.m2~'
_gradle_cmd="gradle app:dependencies --console plain --no-build-cache --no-daemon"

googleUrl='https://dl.google.com/dl/android/maven2'
jcenterUrl='https://jcenter.bintray.com'

function getPath() {
  local tokens=("$@")
  local groupId=${tokens[0]}
  local artifactId=${tokens[1]}
  local version=${tokens[2]}

  groupId=$(echo $groupId | tr '.' '/')
  echo "$groupId/$artifactId/$version/$artifactId-$version"
}

function urlExists() {
  local url="$1"

  if curl --output /dev/null --silent --head --fail "$url.pom"; then
    echo "$url"
  fi
}

function determineArtifactUrl() {
  local tokens=("$@")
  local groupId=${tokens[0]}
  local artifactId=${tokens[1]}
  local version=${tokens[2]}

  set +e

  local path=$(getPath "${tokens[@]}")
  if urlExists "$googleUrl/$path"; then
    return
  fi
  if urlExists "$jcenterUrl/$path"; then
    return
  fi
  echo ""
}

echo "Computing maven dependencies with \`$_gradle_cmd\`..." > /dev/stderr

pushd $GIT_ROOT/android
deps=$($_gradle_cmd \
        | grep -e "---" \
        | sed -E "s;.*--- ([^ ]+).*;\1;" \
        | sort -u)
popd

for dep in ${deps[@]}; do
  if [ $dep = 'unspecified' ] || [ $dep = 'project' ] || [[ $dep == *"["* ]] || [[ $dep == *"+"* ]]; then
    continue
  fi
  
  IFS=':' read -ra tokens <<< "$dep"
  groupId=${tokens[0]}
  artifactId=${tokens[1]}
  version=${tokens[2]}
  artifactUrl=$(determineArtifactUrl "${tokens[@]}")
  echo " - groupId=$groupId artifactId=$artifactId version=$version path=$artifactUrl"
done