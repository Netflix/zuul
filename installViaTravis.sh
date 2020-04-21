#!/bin/bash
# This script will build the project.

echo -e 'Assemble Branch with Snapshot => Branch ['$TRAVIS_BRANCH']'
./gradlew -Prelease.travisci=true assemble