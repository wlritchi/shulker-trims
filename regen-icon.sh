#!/bin/bash
set -euo pipefail

rm -f /home/wlritchi/shulker-trims/fabric/build/icons/shulker*.png
exec xvfb-run -a ./gradlew :fabric:generateIcon
