#!/usr/bin/env bash

set -eEuCo pipefail

( cd modules/scala-native ; sbt publishLocal )

