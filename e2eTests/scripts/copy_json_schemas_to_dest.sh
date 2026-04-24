#!/bin/bash

cd schemas || exit 1

#Download schemas from the NeIC repository

curl -s "https://api.github.com/repos/neicnordic/sensitive-data-archive/contents/sda/schemas/federated" \
  | grep '"download_url"' \
  | cut -d '"' -f 4 \
  | xargs -n 1 curl -O

# interceptor
mkdir -p /volumes/interceptor-schemas/ &&
  cp ./* /volumes/interceptor-schemas/
