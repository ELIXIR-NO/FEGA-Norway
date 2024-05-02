# E2E Test Setup

This Gradle submodule focuses on running the e2e test setup of
FEGA Norway. The following figure depicts the high-level overview
of how this setup works.

![FEGA Norway E2E Test Setup Module](figure-1.png)

[Edit this figure at tldraw.com](https://www.tldraw.com/r/hQuNVXYht2-H6QRZcMh28?v=-3234,-969,4361,2023&p=page)

# How to run the setup?

**To start the orchestration:**
```bash
# Navigate to the project root first.
# Then execute:
./gradlew start-docker-containers
```

**To stop the orchestration:**
```bash
./gradlew stop-docker-containers
```

# Troubleshooting

If the orchestration fail to start for some reason. We suggest trying the fixes below.

##### Unable to execute `start-docker-containers`?

1. Ensure `docker` is installed.
2. Ensure `docker-compose` (V2) is installed.

##### Remove stale containers

If for some reason it complaints about conflicting container
names, we suggest you manually remove the existing containers.

```bash
docker rm tsd db mq proxy interceptor postgres ingest verify finalize mapper doa cegamq cegaauth
```

##### Open ports issues

The following ports **must** be free when running the setup. 
`5432 5672 5433 80 5673 15672 25672` if any other service is
running please make sure you stop them.

```bash
for port in 5432 5672 5433 80 5673 15672 25672; do lsof -ti:$port | xargs kill -9; done
```

##### Remove stale images

```bash
docker rmi tsd-proxy:latest tsd-api-mock:latest mq-interceptor:latest --force
```

##### Docker Compose issues

First, verify that `docker compose` is correctly installed. Ensure you have Compose V2.

For migration guidance to Docker Compose, visit [migrate to docker compose](https://docs.docker.com/compose/migrate/).

In some cases, particularly on older Ubuntu distributions, you might find that you have `docker-compose` (V2) installed but not the `docker compose` subcommand. To resolve this, you can create a symbolic link in the `cli-plugins` directory by executing the following commands:

```bash
mkdir -p ~/.docker/cli-plugins
ln -sfn /usr/local/bin/docker-compose ~/.docker/cli-plugins/docker-compose
```

For further discussions and troubleshooting, refer to the GitHub issue at https://github.com/docker/compose/issues/8630.

##### Maybe try cleaning everything?

This prunes the docker system and restarts the docker daemon.

```bash
docker stop $(docker ps -aq) && \
docker rm $(docker ps -aq) && \
docker rmi tsd-proxy:latest tsd-api-mock:latest mq-interceptor:latest --force && \
for port in 5432 5672 5433 80 5673 15672 25672; do lsof -ti:$port | xargs kill -9; done && \
echo 'y' | docker system prune &&
sudo systemctl restart docker
```
