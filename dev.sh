#!/bin/bash

function start() {
  ./gradlew clean &&
    bash -c "./gradlew start-docker-containers"
}

function stop() {
  ./gradlew stop-docker-containers
}

function rebuild_and_deploy_proxy() {
  ./gradlew :service:localega-tsd-proxy:clean > /dev/null &&
  ./gradlew :service:localega-tsd-proxy:assemble > /dev/null &&
  docker rm proxy -f > /dev/null &&
  docker rmi tsd-proxy:latest -f > /dev/null &&
  cd e2eTests &&
  docker compose up -d proxy > /dev/null &&
  cd .. &&
  echo "Task done ✅ Built and redeployed proxy."
}

function rebuild_and_deploy_heartbeat_sub() {
  docker rm heartbeat-sub -f > /dev/null &&
  docker rmi ghcr.io/elixir-no/pipeline-heartbeat:latest -f > /dev/null &&
  cd e2eTests &&
  docker compose up -d heartbeat-sub > /dev/null &&
  cd .. &&
  echo "Task done ✅ Built and redeployed heartbeat-sub."
}

function rebuild_and_deploy_heartbeat_pub() {
  docker rm heartbeat-pub -f > /dev/null &&
  docker rmi ghcr.io/elixir-no/pipeline-heartbeat:latest -f > /dev/null &&
  cd e2eTests &&
  docker compose up -d heartbeat-pub > /dev/null &&
  cd .. &&
  echo "Task done ✅ Built and redeployed heartbeat-pub." &&
  echo "Note that if you have any static config changes (mapped via e2eTests/confs), you should manually map it again."
}

function rebuild_and_deploy_tsd() {
  ./gradlew :service:tsd-api-mock:clean > /dev/null &&
  ./gradlew :service:tsd-api-mock:assemble > /dev/null &&
  docker rm tsd -f > /dev/null &&
  docker rmi tsd-api-mock:latest -f > /dev/null &&
  cd e2eTests &&
  docker compose up -d tsd > /dev/null &&
  cd .. &&
  echo "Task done ✅ Built and redeployed tsd."
}

function rebuild_clearinghouse() {
  ./gradlew :lib:clearinghouse:clean > /dev/null &&
  ./gradlew :lib:clearinghouse:assemble > /dev/null &&
  echo "Task done ✅ clearinghouse is used by localega-tsd-proxy." &&
  if ask "Do you want to redeploy proxy?" "y"; then
    rebuild_and_deploy_proxy
  fi
}

function rebuild_tsd_file_api_client() {
  ./gradlew :lib:tsd-file-api-client:clean > /dev/null &&
  ./gradlew :lib:tsd-file-api-client:assemble > /dev/null &&
  echo "Task done ✅ tsd-file-api-client is used by localega-tsd-proxy." &&
  if ask "Do you want to redeploy proxy?" "y"; then
    rebuild_and_deploy_proxy
  fi
}

function rebuild_crypt4gh() {
  ./gradlew :lib:crypt4gh:clean > /dev/null &&
  ./gradlew :lib:crypt4gh:assemble > /dev/null &&
  echo "Task done ✅ crypt4gh is rebuilt."
}

function restart_docker_daemon() {
  echo "Running 'sudo systemctl restart docker'..." &&
    sudo systemctl restart docker
}

function apply_all_spotless_checks() {
  ./gradlew :lib:clearinghouse:spotlessApply \
    :lib:crypt4gh:spotlessApply \
    :lib:tsd-file-api-client:spotlessApply \
    :services:localega-tsd-proxy:spotlessApply \
    :services:tsd-api-mock:spotlessApply \
    :e2eTests:spotlessApply
}

# Reusable ask function to get a yes/no answer
# from the user with a default value
function ask() {
    local question=$1
    local default=$2
    local prompt=""
    if [[ "$default" =~ ^[Yy]$ ]]; then
        prompt="(Y/n)"
    elif [[ "$default" =~ ^[Nn]$ ]]; then
        prompt="(y/N)"
    else
        prompt="(y/n)"
    fi
    while true; do
        read -p "$question $prompt: " answer
        answer=${answer:-$default}
        case $answer in
            [Yy]* ) return 0;;
            [Nn]* ) return 1;;
            * ) echo "Please answer yes or no.";;
        esac
    done
}

# Function to display the interactive menu
function show_menu() {
  echo "Please choose an option:"
  select option in "Start services" "Stop services" "Rebuild and deploy proxy" "Rebuild and deploy TSD" "Rebuild clearinghouse" "Rebuild TSD file API client" "Rebuild crypt4gh" "Restart Docker Daemon" "Apply all Spotless Checks" "Rebuild & deploy heartbeat-sub" "Rebuild & deploy heartbeat-pub" "Exit"; do
    case $REPLY in
      1) start; break;;
      2) stop; break;;
      3) rebuild_and_deploy_proxy; break;;
      4) rebuild_and_deploy_tsd; break;;
      5) rebuild_clearinghouse; break;;
      6) rebuild_tsd_file_api_client; break;;
      7) rebuild_crypt4gh; break;;
      8) restart_docker_daemon; break;;
      9) apply_all_spotless_checks; break;;
      10) rebuild_and_deploy_heartbeat_sub; break;;
      11) rebuild_and_deploy_heartbeat_pub; break;;
      12) echo "Exiting..."; exit 0;;
      *) echo "Invalid option. Please try again.";;
    esac
  done
}

# Check if a function name is provided
if [ -z "$1" ]; then
  show_menu
else
  # Get the function name from the first argument
  FUNC_NAME=$1

  # Check if the function exists and execute it
  if declare -f "$FUNC_NAME" > /dev/null; then
    $FUNC_NAME
  else
    echo "Function '$FUNC_NAME' not found."
    exit 1
  fi
fi
