#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup script, version 3.3.2
#
# Optional ENV vars
# -----------------
#   JAVA_HOME - location of a JDK home dir, required when type is not JRE
#   MVNW_REPOURL - repo url base for downloading maven distribution
#   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
#   MVNW_VERBOSE - true: enable verbose log; others: silence the output
# ----------------------------------------------------------------------------

set -euf
[ "${MVNW_VERBOSE-}" != true ] || set -x

# OS specific support.
native_path() { printf %s\\n "$1"; }
case "$(uname)" in
CYGWIN* | MINGW*)
  [ -z "${JAVA_HOME-}" ] || JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
  native_path() { cygpath --path --windows "$1"; }
  ;;
esac

# set JAVACMD
: "${JAVA_HOME:=""}"
if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/jre/sh/java" ]; then
    JAVACMD="$JAVA_HOME/jre/sh/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
  if [ ! -x "$JAVACMD" ]; then
    echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
    echo "Please set the JAVA_HOME variable in your environment to match the location of your Java installation." >&2
    exit 1
  fi
else
  JAVACMD="$(
    'set' +e
    'unset' -v JAVA_TOOL_OPTIONS
    'java' -version >/dev/null 2>&1 && command -v java
  )" || {
    echo "ERROR: Cannot find Java. Please set either \$JAVA_HOME or make sure java is in your PATH." >&2
    exit 1
  }
fi

# Find the project basedir (directory containing .mvn/)
find_maven_basedir() {
  basedir="$1"
  wdir="$1"
  while [ "$wdir" != '/' ]; do
    if [ -d "$wdir"/.mvn ]; then
      basedir=$wdir
      break
    fi
    if [ -d "${wdir}" ]; then
      wdir=$(cd "$wdir/.." || exit 1; pwd)
    fi
  done
  printf '%s' "$(cd "$basedir" || exit 1; pwd)"
}

concat_lines() {
  if [ -f "$1" ]; then
    tr '\015' '\012' < "$1"
  fi
}

log() {
  if [ "${MVNW_VERBOSE-}" = true ]; then
    printf '%s\n' "$1"
  fi
}

# Resolve script location robustly
SCRIPT="$0"
while [ -h "$SCRIPT" ]; do
  SCRIPT_DIR=$(cd "$(dirname "$SCRIPT")" && pwd)
  SCRIPT="$(readlink "$SCRIPT")"
  [ "${SCRIPT#/}" = "$SCRIPT" ] && SCRIPT="$SCRIPT_DIR/$SCRIPT"
done

BASE_DIR=$(find_maven_basedir "$(dirname "$SCRIPT")")
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-"$BASE_DIR"}"
export MAVEN_PROJECTBASEDIR
log "$MAVEN_PROJECTBASEDIR"

MAVEN_OPTS="$(concat_lines "$MAVEN_PROJECTBASEDIR/.mvn/jvm.config") ${MAVEN_OPTS-}"

# Download maven-wrapper.jar if missing
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
if [ -r "$MAVEN_WRAPPER_JAR" ]; then
  log "Found $MAVEN_WRAPPER_JAR"
else
  log "Downloading maven-wrapper.jar ..."

  WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

  # Read wrapperUrl from properties if defined
  while IFS="=" read -r key value; do
    case "$key" in
      wrapperUrl) WRAPPER_URL="$value"; break ;;
    esac
  done < "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

  log "Downloading from: $WRAPPER_URL"

  if command -v wget > /dev/null; then
    [ "${MVNW_VERBOSE-}" = true ] && WGET_QUIET="" || WGET_QUIET="--quiet"
    if [ -z "${MVNW_USERNAME-}" ] && [ -z "${MVNW_PASSWORD-}" ]; then
      wget $WGET_QUIET "$WRAPPER_URL" -O "$MAVEN_WRAPPER_JAR" || rm -f "$MAVEN_WRAPPER_JAR"
    else
      wget $WGET_QUIET --http-user="${MVNW_USERNAME}" --http-password="${MVNW_PASSWORD}" \
        "$WRAPPER_URL" -O "$MAVEN_WRAPPER_JAR" || rm -f "$MAVEN_WRAPPER_JAR"
    fi
  elif command -v curl > /dev/null; then
    [ "${MVNW_VERBOSE-}" = true ] && CURL_QUIET="" || CURL_QUIET="--silent"
    if [ -z "${MVNW_USERNAME-}" ] && [ -z "${MVNW_PASSWORD-}" ]; then
      curl $CURL_QUIET -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL" -f || rm -f "$MAVEN_WRAPPER_JAR"
    else
      curl $CURL_QUIET --user "${MVNW_USERNAME}:${MVNW_PASSWORD}" \
        -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL" -f || rm -f "$MAVEN_WRAPPER_JAR"
    fi
  else
    echo "ERROR: Cannot download maven-wrapper.jar. Install wget or curl." >&2
    exit 1
  fi
fi

if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
  echo "ERROR: Cannot find $MAVEN_WRAPPER_JAR" >&2
  exit 1
fi

exec "$JAVACMD" \
  ${MAVEN_OPTS} \
  ${MAVEN_DEBUG_OPTS-} \
  -classpath "$MAVEN_WRAPPER_JAR" \
  "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
  org.apache.maven.wrapper.MavenWrapperMain \
  "$@"
