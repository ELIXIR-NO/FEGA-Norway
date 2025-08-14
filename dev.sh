#!/bin/bash

source ./e2eTests/env.sh

# Cross-platform compatibility checks
OS="$(uname)"
case $OS in
  'Linux')
    OS='Linux'
    DOCKER_CMD='systemctl'
    ;;
  'Darwin')
    OS='Mac'
    DOCKER_CMD='brew'
    ;;
  *)
    OS='Unknown'
    DOCKER_CMD='systemctl'
    ;;
esac

# Color definitions (works on both Linux and macOS)
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    PURPLE='\033[0;35m'
    CYAN='\033[0;36m'
    WHITE='\033[1;37m'
    BOLD='\033[1m'
    DIM='\033[2m'
    NC='\033[0m'
else
    # No colors for non-interactive terminals
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    PURPLE=''
    CYAN=''
    WHITE=''
    BOLD=''
    DIM=''
    NC=''
fi

# Logging functions

function log_info() {
    printf "${BLUE}[INFO]${NC} %s\n" "$1"
}

function log_success() {
    printf "${GREEN}[SUCCESS]${NC} %s\n" "$1"
}

function log_warning() {
    printf "${YELLOW}[WARNING]${NC} %s\n" "$1"
}

function log_error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1"
}

function log_step() {
    printf "${CYAN}[STEP]${NC} %s...\n" "$1"
}

function show_header() {
    clear
    printf "${PURPLE}${BOLD}"
    printf "===============================================================\n"
    printf "                   FEGA Development Tools                      \n"
    printf "                       Platform: [%s]                          \n" "$OS"
    printf "===============================================================\n"
    printf "${NC}"
}

# Service management functions

function start() {
    show_header
    log_step "Starting development environment"

    if ./gradlew clean && bash -c "./gradlew start-docker-containers"; then
        log_success "Development environment started successfully!"
    else
        log_error "Failed to start development environment"
        return 1
    fi
}

function stop() {
    show_header
    log_step "Stopping development environment"

    if ./gradlew stop-docker-containers; then
        log_success "Development environment stopped successfully!"
    else
        log_error "Failed to stop development environment"
        return 1
    fi
}

function reexecute_tests_in_container() {
    show_header
    log_step "Rebuilding and reexecuting E2E tests"

    ./gradlew :e2eTests:clean > /dev/null &&
    ./gradlew :e2eTests:assemble > /dev/null &&
    docker rm e2e-tests -f > /dev/null 2>&1 &&
    docker rmi fega-norway-e2e-tests:latest -f > /dev/null 2>&1 &&
    cd e2eTests &&
    docker compose up -d e2e-tests > /dev/null &&
    cd .. &&
    log_success "E2E tests rebuilt and reexecuted!"
}

function rebuild_and_deploy_proxy() {
    show_header
    log_step "Rebuilding and deploying proxy service"

    ./gradlew :service:localega-tsd-proxy:clean > /dev/null &&
    ./gradlew :service:localega-tsd-proxy:assemble > /dev/null &&
    docker rm proxy -f > /dev/null 2>&1 &&
    docker rmi tsd-proxy:latest -f > /dev/null 2>&1 &&
    cd e2eTests &&
    docker compose up -d proxy > /dev/null &&
    cd .. &&
    log_success "Proxy service rebuilt and deployed!"
}

function rebuild_and_deploy_mq_interceptor() {
    show_header
    log_step "Rebuilding and deploying MQ interceptor"

    ./gradlew :service:mq-interceptor:clean > /dev/null &&
    ./gradlew :service:mq-interceptor:assemble > /dev/null &&
    docker rm interceptor -f > /dev/null 2>&1 &&
    docker rmi mq-interceptor:latest -f > /dev/null 2>&1 &&
    cd e2eTests &&
    docker compose up -d interceptor > /dev/null &&
    cd .. &&
    log_success "MQ interceptor rebuilt and deployed!"
}

function rebuild_and_deploy_heartbeat_sub() {
    show_header
    log_step "Rebuilding and deploying heartbeat subscriber"

    docker rm heartbeat-sub -f > /dev/null 2>&1 &&
    docker rmi ghcr.io/elixir-no/pipeline-heartbeat:latest -f > /dev/null 2>&1 &&
    cd e2eTests &&
    docker compose up -d heartbeat-sub > /dev/null &&
    cd .. &&
    log_success "Heartbeat subscriber rebuilt and deployed!"
}

function rebuild_and_deploy_heartbeat_pub() {
    show_header
    log_step "Rebuilding and deploying heartbeat publisher"

    docker rm heartbeat-pub -f > /dev/null 2>&1 &&
    docker rmi ghcr.io/elixir-no/pipeline-heartbeat:latest -f > /dev/null 2>&1 &&
    cd e2eTests &&
    docker compose up -d heartbeat-pub > /dev/null &&
    cd .. &&
    log_success "Heartbeat publisher rebuilt and deployed!"
    log_warning "Note: If you have static config changes (mapped via e2eTests/confs), manually map them again."
}

function rebuild_and_deploy_tsd() {
    show_header
    log_step "Rebuilding and deploying TSD API mock"

    ./gradlew :service:tsd-api-mock:clean > /dev/null &&
    ./gradlew :service:tsd-api-mock:assemble > /dev/null &&
    docker rm tsd -f > /dev/null 2>&1 &&
    docker rmi tsd-api-mock:latest -f > /dev/null 2>&1 &&
    cd e2eTests &&
    docker compose up -d tsd > /dev/null &&
    cd .. &&
    log_success "TSD API mock rebuilt and deployed!"
}

function rebuild_clearinghouse() {
    show_header
    log_step "Rebuilding clearinghouse library"

    ./gradlew :lib:clearinghouse:clean > /dev/null &&
    ./gradlew :lib:clearinghouse:assemble > /dev/null &&
    log_success "Clearinghouse library rebuilt!"
    log_info "This library is used by localega-tsd-proxy."

    if ask "Do you want to redeploy proxy?" "y"; then
        rebuild_and_deploy_proxy
    fi
}

function rebuild_tsd_file_api_client() {
    show_header
    log_step "Rebuilding TSD file API client"

    ./gradlew :lib:tsd-file-api-client:clean > /dev/null &&
    ./gradlew :lib:tsd-file-api-client:assemble > /dev/null &&
    log_success "TSD file API client rebuilt!"
    log_info "This library is used by localega-tsd-proxy."

    if ask "Do you want to redeploy proxy?" "y"; then
        rebuild_and_deploy_proxy
    fi
}

function rebuild_crypt4gh() {
    show_header
    log_step "Rebuilding crypt4gh library"

    ./gradlew :lib:crypt4gh:clean > /dev/null &&
    ./gradlew :lib:crypt4gh:assemble > /dev/null &&
    log_success "Crypt4gh library rebuilt!"
}

function restart_docker_daemon() {
    show_header
    log_step "Restarting Docker daemon"

    case $OS in
        'Linux')
            log_warning "This requires sudo privileges on Linux"
            if sudo systemctl restart docker; then
                log_success "Docker daemon restarted successfully!"
            else
                log_error "Failed to restart Docker daemon"
                return 1
            fi
            ;;
        'Mac')
            log_info "On macOS, please restart Docker Desktop manually"
            log_info "You can do this from the Docker Desktop application menu"
            log_info "Or run: osascript -e 'quit app \"Docker Desktop\"' && open -a Docker"
            ;;
        *)
            log_warning "Unknown OS. Attempting Linux method..."
            if sudo systemctl restart docker; then
                log_success "Docker daemon restarted successfully!"
            else
                log_error "Failed to restart Docker daemon"
                return 1
            fi
            ;;
    esac
}

function apply_all_spotless_checks() {
    show_header
    log_step "Applying Spotless code formatting to all modules"

    log_info "Formatting the following modules:"
    printf "  - lib:clearinghouse\n"
    printf "  - lib:crypt4gh\n"
    printf "  - lib:tsd-file-api-client\n"
    printf "  - services:localega-tsd-proxy\n"
    printf "  - services:tsd-api-mock\n"
    printf "  - e2eTests\n\n"

    if ./gradlew :lib:clearinghouse:spotlessApply \
        :lib:crypt4gh:spotlessApply \
        :lib:tsd-file-api-client:spotlessApply \
        :services:localega-tsd-proxy:spotlessApply \
        :services:tsd-api-mock:spotlessApply \
        :e2eTests:spotlessApply; then
        log_success "All Spotless checks applied successfully!"
    else
        log_error "Some Spotless checks failed"
        return 1
    fi
}

function cleanup_environment() {
    local verbose=true

    log_verbose() {
        if [[ "$verbose" == true ]]; then
            log_info "$1"
        fi
    }

    run_command() {
        local cmd="$1"
        local description="$2"

        log_verbose "Running: $description"
        if [[ "$verbose" == true ]]; then
            eval "$cmd"
        else
            eval "$cmd" > /dev/null 2>&1 || true
        fi
    }

    show_header
    log_step "Cleaning up FEGA Norway Docker environment"

    # Check if Docker is running
    if ! docker info &>/dev/null; then
        log_error "Docker is not running or not accessible"
        return 1
    fi

    # Determine compose command
    local compose_cmd=""
    if command -v docker-compose &>/dev/null; then
        compose_cmd="docker-compose"
    elif docker compose version &>/dev/null; then
        compose_cmd="docker compose"
    else
        log_warning "Neither 'docker-compose' nor 'docker compose' found"
        log_info "Proceeding with manual cleanup..."
    fi

    printf "\n"
    log_warning "This will remove:"
    printf "  • All containers from the fega-norway project\n"
    printf "  • Associated networks and volumes\n"
    printf "  • All project data (certificates, databases, etc.)\n"
    printf "  • Custom built images\n"
    printf "\n"

    if ! ask "Do you want to continue with the cleanup?" "n"; then
        log_info "Cleanup cancelled."
        return 0
    fi

    printf "\n"
    log_step "Stopping and removing containers"

    # Container names
    local containers=(
        "tsd"
        "mq"
        "proxy"
        "interceptor"
        "postgres"
        "db"
        "ingest"
        "verify"
        "finalize"
        "mapper"
        "intercept"
        "doa"
        "cegamq"
        "cegaauth"
        "heartbeat-pub"
        "heartbeat-sub"
        "redis"
        "file-orchestrator"
        "e2e-tests"
    )

    # Use docker-compose if available
    if [[ -n "$compose_cmd" ]]; then
        log_verbose "Using $compose_cmd for cleanup"
        cd e2eTests 2>/dev/null || true
        run_command "$compose_cmd down --remove-orphans" "Stopping compose services"
        run_command "$compose_cmd down --volumes --remove-orphans" "Removing volumes with compose"
        cd .. 2>/dev/null || true
    fi

    # Manual container cleanup for any remaining containers
    for container in "${containers[@]}"; do
        if docker ps -a --format "table {{.Names}}" | grep -q "^${container}$"; then
            run_command "docker stop $container" "Stopping container: $container"
            run_command "docker rm $container" "Removing container: $container"
        fi
    done

    log_step "Removing project volumes"

    local volumes=(
        "fega-norway_tsd-inbox"
        "fega-norway_tsd-vault"
        "fega-norway_tsd-certs"
        "fega-norway_interceptor-certs"
        "fega-norway_mq-confs-and-certs"
        "fega-norway_proxy-certs"
        "fega-norway_postgres-data"
        "fega-norway_postgres-confs"
        "fega-norway_db-client-certs"
        "fega-norway_db-certs"
        "fega-norway_db-data"
        "fega-norway_sda-certs"
        "fega-norway_doa-certs"
        "fega-norway_cegamq-certs"
        "fega-norway_cegamq-confs"
        "fega-norway_heartbeat-confs"
        "fega-norway_storage"
    )

    for volume in "${volumes[@]}"; do
        if docker volume ls --format "table {{.Name}}" | grep -q "^${volume}$"; then
            run_command "docker volume rm $volume" "Removing volume: $volume"
        fi
    done

    log_step "Removing project networks"

    # Remove project-specific networks (try both naming conventions)
    local network_patterns=("fega-norway" "e2etests")
    for pattern in "${network_patterns[@]}"; do
        local networks=$(docker network ls --filter "name=${pattern}" --format "{{.Name}}" 2>/dev/null || true)
        if [[ -n "$networks" ]]; then
            for network in $networks; do
                run_command "docker network rm $network" "Removing network: $network"
            done
        fi
    done

    log_step "Removing custom built images"

    local images=(
        "tsd-api-mock:latest"
        "tsd-proxy:latest"
        "mq-interceptor:latest"
        "cega-mock:latest"
        "fega-norway-e2e-tests:latest"
        "e2etests-e2e-tests:latest"
    )

    for image in "${images[@]}"; do
        if docker images --format "table {{.Repository}}:{{.Tag}}" | grep -q "^${image}$"; then
            run_command "docker rmi $image" "Removing image: $image"
        fi
    done

    log_step "Attempting to run general Docker cleanup"
    printf "\n"
    log_warning "If you say yes, I will:"
    printf "  • Remove dangling containers\n"
    printf "  • Remove unused volumes\n"
    printf "  • Remove unused networks\n"
    printf "  • Remove unused images\n"

    if ask "Do you want to do a general Docker cleanup?" "n"; then
        # Clean up dangling resources
        run_command "docker system prune -f" "Removing dangling containers, networks, and images"
        run_command "docker volume prune -f" "Removing unused volumes"
        log_success "General Docker cleanup completed!"
    else
        log_info "Okay... I didn't touch anything!"
    fi

    printf "\n"
    log_success "Environment cleanup completed!"
    printf "\n"
    log_info "Summary:"
    printf "  • Stopped and removed all containers\n"
    printf "  • Removed project volumes and data\n"
    printf "  • Removed project networks\n"
    printf "  • Removed custom built images\n"
    printf "\n"
    log_info "Note: Downloaded base images (postgres, rabbitmq, etc.) were preserved"
    log_info "      Run 'docker image prune -a' manually if you want to remove them too"
}

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
        printf "${CYAN}%s ${GREEN}%s${NC}: " "$question" "$prompt"
        read -r answer
        answer=${answer:-$default}
        case $answer in
            [Yy]* ) return 0;;
            [Nn]* ) return 1;;
            * ) log_warning "Please answer yes or no.";;
        esac
    done
}

function replace_root_ca() {
    show_header
    log_step "Replacing root CA certificate"

    local container_name="$1"
    local cert_path_in_container="$2"
    local alias_name="$3"
    local temp_cert_file="temp_rootCA.pem"

    if [[ -z "$container_name" || -z "$cert_path_in_container" || -z "$alias_name" ]]; then
        log_error "Usage: replace_root_ca <container_name> <cert_path_in_container> <alias_name>"
        return 1
    fi

    # Determine keystore path based on OS
    local keystore_path
    local keystore_password="changeit"

    case $OS in
        'Mac')
            if [[ -n "$JAVA_HOME" ]]; then
                keystore_path="$JAVA_HOME/lib/security/cacerts"
            else
                # Try common macOS Java locations
                for java_path in "/Library/Java/JavaVirtualMachines/"*/Contents/Home "/System/Library/Frameworks/JavaVM.framework/Home"; do
                    if [[ -f "$java_path/lib/security/cacerts" ]]; then
                        keystore_path="$java_path/lib/security/cacerts"
                        break
                    fi
                done
            fi
            ;;
        'Linux')
            if [[ -n "$JAVA_HOME" ]]; then
                keystore_path="$JAVA_HOME/lib/security/cacerts"
            else
                keystore_path="/etc/ssl/certs/java/cacerts"
            fi
            ;;
        *)
            keystore_path="$JAVA_HOME/lib/security/cacerts"
            ;;
    esac

    if [[ ! -f "$keystore_path" ]]; then
        log_error "Could not find Java keystore at: $keystore_path"
        log_info "Please set JAVA_HOME environment variable"
        return 1
    fi

    log_info "Copying certificate from container..."
    if ! docker cp "$container_name:$cert_path_in_container" "$temp_cert_file"; then
        log_error "Failed to copy certificate from container"
        return 1
    fi

    log_info "Deleting existing certificate alias (if exists)..."
    sudo keytool -delete -alias "$alias_name" -keystore "$keystore_path" -storepass "$keystore_password" 2>/dev/null || true

    log_info "Importing the new certificate..."
    if sudo keytool -import -trustcacerts -file "$temp_cert_file" -alias "$alias_name" -keystore "$keystore_path" -storepass "$keystore_password" -noprompt; then
        log_success "Certificate updated successfully with alias '$alias_name'!"
    else
        log_error "Failed to import the certificate"
        rm -f "$temp_cert_file"
        return 1
    fi

    log_info "Cleaning up temporary files..."
    rm -f "$temp_cert_file"
}

function show_menu() {
    show_header

    printf "${BOLD}${WHITE}Available Operations:${NC}\n\n"

    printf " 1) Start services\n"
    printf " 2) Stop services\n"
    printf " 3) Rebuild and deploy proxy\n"
    printf " 4) Rebuild and deploy TSD\n"
    printf " 5) Rebuild clearinghouse\n"
    printf " 6) Rebuild TSD file API client\n"
    printf " 7) Rebuild crypt4gh\n"
    printf " 8) Restart Docker Daemon\n"
    printf " 9) Apply all Spotless Checks\n"
    printf "10) Rebuild & deploy heartbeat-sub\n"
    printf "11) Rebuild & deploy heartbeat-pub\n"
    printf "12) Rebuild & deploy mq-interceptor\n"
    printf "13) Reexecute E2E tests\n"
    printf "14) Replace RootCA\n"
    printf "15) Deep cleanup Docker environment\n"
    printf "16) Exit\n"

    while true; do
        printf "\n${CYAN}${BOLD}Select an option (1-15): ${NC}"
        read -r choice

        case $choice in
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
            12) rebuild_and_deploy_mq_interceptor; break;;
            13) reexecute_tests_in_container; break;;
            14) replace_root_ca file-orchestrator /storage/certs/rootCA.pem fega; break;;
            15) cleanup_environment; break;;
            16)
                printf "\n"
                printf "${DIM}Goodbye!${NC}\n"
                exit 0
                ;;
            *)
                log_warning "Invalid option. Please select a number between 1 and 15."
                ;;
        esac

        printf "\n${DIM}Press Enter to return to menu...${NC}"
        read -r
        show_menu
    done
}

# Main execution logic

if [ -z "$1" ]; then
    show_menu
else
    FUNC_NAME=$1
    if declare -f "$FUNC_NAME" > /dev/null; then
        show_header
        $FUNC_NAME
    else
        log_error "Function '$FUNC_NAME' not found."
        printf "\n"
        log_info "Available functions:"
        declare -F | grep -v "declare -f ask\|declare -f log_\|declare -f show_" | sed 's/declare -f /  - /'
        exit 1
    fi
fi
