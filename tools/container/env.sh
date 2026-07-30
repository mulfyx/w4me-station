#!/usr/bin/env bash
# Canonical project toolchain environment. Project scripts source this file;
# when invoked from the host they re-exec themselves in the pinned Docker image.

if [[ "${W4ME_TOOLCHAIN_CONTAINER:-}" != "1" ]]; then
    root_dir="$(readlink -f -- "$(dirname -- "${BASH_SOURCE[0]}")/../..")"
    if command -v docker > /dev/null 2>&1; then
        script="$(readlink -f -- "$0")"
        exec "${root_dir}/tools/container/run.sh" exec "${script}" "$@"
    fi
    printf 'error: docker command not found; see tools/container/README.md\n' >&2
    exit 1
fi

# Keep host-installed tools out of project commands. JDK 8 remains first so
# release scripts cannot accidentally compile with the analysis JDK.
export PATH="/opt/jdk8/bin:/opt/proguard/bin:/opt/maven/bin:/opt/node/bin:/opt/quality/bin:/opt/quality/venv/bin:/opt/quality/node-tools/node_modules/.bin:/usr/local/bin:/usr/bin:/bin"

export JAVA_HOME="/opt/jdk8"
export JDK8_HOME="/opt/jdk8"
export QUALITY_JAVA_HOME="/opt/jdk21"
export MAVEN_HOME="/opt/maven"
export NPM_CONFIG_UPDATE_NOTIFIER="false"
export NODE_HOME="/opt/node"
export OPENSPEC_TELEMETRY="0"
export KEMU_HOME="/opt/kemu"
export PROGUARD_HOME="/opt/proguard"
export CLDC_API_JAR="${CLDC_API_JAR:-/opt/j2me-api/cldcapi11-2.0.4.jar}"
export MIDP_API_STUB_JAR="${MIDP_API_STUB_JAR:-/opt/j2me-api/midpapi20-2.0.4.jar}"
export J2ME_BOOTCLASSPATH="${CLDC_API_JAR}:${MIDP_API_STUB_JAR}"
export MIDP_API_JAR="/opt/kemu/KEmulator.jar"
export J2ME_SOURCE="1.3"
export J2ME_TARGET="1.3"
