#!/bin/bash

set -uo pipefail

cd schemas || exit 1

# The SDA message schemas are fetched at runtime rather than vendored, which
# makes this the only step in the whole stack that needs the internet. Check
# every stage: on failure the directory stays empty, `cp ./*` dies with
# "cannot stat './*'", and the orchestrator never touches /storage/ready, so the
# entire stack hangs on an unhealthy gate with nothing pointing at the network.
api_url="https://api.github.com/repos/neicnordic/sensitive-data-archive/contents/sda/schemas/federated"

urls=$(curl -fsSL "$api_url" | grep '"download_url"' | cut -d '"' -f 4)
if [ $? -ne 0 ] || [ -z "$urls" ]; then
  echo "ERROR: could not list the SDA schemas from ${api_url}" >&2
  echo "       the container needs outbound HTTPS to api.github.com" >&2
  exit 1
fi

echo "$urls" | xargs -n 1 curl -fsSLO || {
  echo "ERROR: at least one schema failed to download" >&2
  exit 1
}

downloaded=$(find . -maxdepth 1 -type f | wc -l)
if [ "$downloaded" -eq 0 ]; then
  echo "ERROR: the schema listing succeeded but no files were downloaded" >&2
  exit 1
fi
echo "Downloaded ${downloaded} SDA schemas"

# interceptor
mkdir -p /volumes/interceptor-schemas/ &&
  cp ./* /volumes/interceptor-schemas/
