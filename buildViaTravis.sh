#!/bin/bash
# This script will build the project.

if [ "$TRAVIS_PULL_REQUEST" != "false" ] || [ "$GRADLE_PUBLISH" == "false" ]; then
  echo -e "Build Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  ./gradlew build
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ]; then
  echo -e 'Build Branch with Snapshot => Branch ['$TRAVIS_BRANCH']'
  ./gradlew -Prelease.travisci=true -PnetflixOss.username="$NETFLIX_OSS_REPO_USERNAME" -PnetflixOss.password="$NETFLIX_OSS_REPO_PASSWORD"  -Psonatype.signingPassword="$NETFLIX_OSS_SIGNING_PASSWORD" -Prelease.scope=patch build snapshot
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
  echo -e 'Build Branch for Release => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']'
  case "$TRAVIS_TAG" in
  *-rc\.*)
    ./gradlew -Prelease.travisci=true  -Prelease.useLastTag=true  -PnetflixOss.username="$NETFLIX_OSS_REPO_USERNAME" -PnetflixOss.password="$NETFLIX_OSS_REPO_PASSWORD"  -Psonatype.signingPassword="$NETFLIX_OSS_SIGNING_PASSWORD" candidate
    ;;
  *)
    ./gradlew -Prelease.travisci=true  -Prelease.useLastTag=true -PnetflixOss.username="$NETFLIX_OSS_REPO_USERNAME" -PnetflixOss.password="$NETFLIX_OSS_REPO_PASSWORD"  -Psonatype.username="$NETFLIX_OSS_SONATYPE_USERNAME" -Psonatype.password="$NETFLIX_OSS_SONATYPE_PASSWORD" -Psonatype.signingPassword="$NETFLIX_OSS_SIGNING_PASSWORD" final
    ;;
  esac
else
  echo -e 'WARN: Should not be here => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']  Pull Request ['$TRAVIS_PULL_REQUEST']'
  ./gradlew build
fi
