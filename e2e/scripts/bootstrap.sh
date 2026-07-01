#!/bin/bash

source env.sh

# Utility functions --

# Check the existence of a passed command but discard
# the outcome through redirection whether its successful
# or erroneous.
function exists() {
  command -v "$1" 1>/dev/null 2>&1
}

function escape_special_chars() {
  echo "$1" | sed -e 's/[]\/$*.^[]/\\&/g'
}

# Find and replace all the strings matching target
# in a specified file.
function frepl() {
  local search=$(escape_special_chars "$1")
  local replace=$(escape_special_chars "$2")
  sed -i.bak "s/$search/$replace/g" "$3"
  rm -rf "$3.bak"
}

# Functionalities --

function apply_configs() {

  # Check if the source template file exists
  if [ -f "docker-compose.template.yml" ]; then
    # Copy the content of docker-compose.template.yml to docker-compose.yml
    cp docker-compose.template.yml ./docker-compose.yml
    rm -rf docker-compose.yml.bak >/dev/null 2>&1
    echo "docker-compose.yml has been successfully created from the template."
  else
    echo "Error: docker-compose.template.yml does not exist."
    return 1
  fi

  local f=docker-compose.yml
  local missing_vars=()

  # Automatically detect and replace all template variables in the format <<VAR_NAME>>.
  # Previously, each variable had to be set manually; now, values are pulled from the environment.
  # - If a value exists, it is replaced in the file.
  # - If not, it is added to a list of missing variables, and a warning is shown
  #   with export commands to set them in env.sh.
  local template_vars=($(grep -o '<<[^>]*>>' "$f" | sed 's/<<\(.*\)>>/\1/' | sort -u))

  echo "Found ${#template_vars[@]} template variables to replace..."

  for var in "${template_vars[@]}"; do
    if [ -n "${!var}" ]; then
      frepl "<<$var>>" "${!var}" "$f"
      echo "✓ Replaced <<$var>>"
    else
      missing_vars+=("$var")
      echo "✗ Missing: $var"
    fi
  done

  if [ ${#missing_vars[@]} -gt 0 ]; then
    echo ""
    echo "WARNING: The following variables are not set:"
    printf "  %s\n" "${missing_vars[@]}"
    echo ""
    echo "Please set these variables in your env.sh file:"
    for var in "${missing_vars[@]}"; do
      echo "export $var=\"\""
    done
  else
    echo "All template variables have been successfully replaced!"
  fi
}

function check_requirements() {

    # Check if Docker is running
    if ! docker info &> /dev/null; then
        echo "Docker is not running"
        return 1
    fi

    # Check if the current user can execute Docker commands without sudo
    if ! docker ps &> /dev/null; then
        echo "The current user cannot execute Docker commands without sudo"
        return 1
    fi

    # Check if Docker is installed
    if ! command -v docker &> /dev/null; then
        echo "Docker is not installed"
        return 1
    fi

    # Check if Docker Compose is available
    if ! docker compose version &> /dev/null; then
        echo "Docker Compose is not available"
        return 1
    fi

    echo "All requirements satisfied"
    return 0

}

function cleanup_workspace() {
    rm -f *.raw *.raw.enc &&
      ../gradlew clean
}

# Entrypoint --

usage="[check_requirements|apply_configs]"
if [ $# -ge 1 ]; then
  # Parse the action argument and perform
  # the corresponding action
  case "$1" in
  "check_requirements")
    check_requirements
    ;;
  "apply_configs")
    apply_configs
    ;;
  "cleanup_workspace")
    cleanup_workspace
    ;;
  *)
    echo "Invalid action. Usage: $0 $usage"
    exit 1
    ;;
  esac
fi
