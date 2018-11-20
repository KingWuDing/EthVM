#!/bin/bash -e

# Give script sane defaults
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR=$(cd ${SCRIPT_DIR}/..; pwd)

# DEFAULT VARS
PROJECTS=("bolt", "explorer", "api", "kafka-connect", "kafka-ethvm-init", "mongodb-install", "zookeeper")

ORG="enkryptio"
DOCKER_PATH="docker/images"

# Usage prints the help for this command.
usage() {
  >&2 echo "Usage:"
  >&2 echo "    docker-build <command>"
  >&2 echo ""
  >&2 echo "Commands:"
  >&2 echo "    build <project>  Build a docker image from this repo. Valid values: [${PROJECTS[*]}]"
  >&2 echo "    push  <project>  Push the built image to the docker registry. Valid values: [${PROJECTS[*]}]"
  exit 1
}

# ensure checks that whe have corresponding utilities installed
ensure() {
  if ! [ -x "$(command -v jq)" ]; then
    >&2 echo "jq is necessary to be installed to run this script!"
    exit 1
  fi
}

# Build builds the docker image and tags it with the git sha and branch.
build() {
  local name="$1"
  local version="$2"
  local dockerfile="$3"
  local path="$4"
  docker build -t "$ORG/$name:$version" -f $dockerfile $path
}

# Push pushes all of the built docker images.
push() {
  local repo="$1"
  docker push "$repo"
}

prop() {
  grep $1 $2 | cut -d '=' -f2
}

run() {
ensure
  case "$1" in
    build)
      case "$2" in
        bolt) build "$2" "$(prop 'version' "apps/$2/version.properties")" "apps/$2/Dockerfile" "apps/$2" ;;
        explorer) build "$2" "$(jq .version apps/ethvm/package.json -r)" "apps/ethvm/Dockerfile" "apps/" ;;
        api) build "$2" "$(jq .version apps/server/package.json -r)" "apps/server/Dockerfile" "apps/" ;;
        kafka-connect|kafka-ethvm-init|mongodb-install|zookeeper) build "$2" "$(prop 'version' "${DOCKER_PATH}/$2/version.properties")" "${DOCKER_PATH}/$2/Dockerfile" "${DOCKER_PATH}/$2/" ;;
      esac
      ;;
    push)
      case "$2" in
        bolt) push "$ORG/$2:$(prop 'version' "apps/$2/version.properties")" ;;
        explorer) push "$ORG/$2:$(jq .version apps/ethvm/package.json -r)" ;;
        api) push "$ORG/$2:$(jq .version apps/server/package.json -r)" ;;
        kafka-connect|kafka-ethvm-init|mongodb-install|zookeeper) push "$ORG/$2:$(prop 'version' "${DOCKER_PATH}/$2/version.properties")" ;;
      esac
      ;;
    *) usage ;;
  esac
}
run "$@"
