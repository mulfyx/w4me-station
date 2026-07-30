#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

BUILD_DIR="${ROOT_DIR}/build/midlet"
CLASSES_DIR="${BUILD_DIR}/classes"
BASE_CLASSES_DIR="${BUILD_DIR}/base-classes"
DIST_DIR="${ROOT_DIR}/dist"

rm -rf -- "${BUILD_DIR}" "${DIST_DIR}"
mkdir -p -- "${CLASSES_DIR}" "${DIST_DIR}"

# The distributable MIDlets use the accepted counterless production config.
# Host tests keep compiling the regular diagnostic config so profiling and
# deterministic counter assertions remain available outside release artifacts.
find "${ROOT_DIR}/src/main/java" \
    -name '*.java' \
    ! -path "${ROOT_DIR}/src/main/java/w4me/wasm/InterpreterBuildConfig.java" \
    -print | sort >"${BUILD_DIR}/sources.list"
printf '%s\n' \
    "${ROOT_DIR}/bench/configs/timed/java/w4me/wasm/InterpreterBuildConfig.java" \
    >>"${BUILD_DIR}/sources.list"
javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -encoding UTF-8 \
    -bootclasspath "${J2ME_BOOTCLASSPATH}" \
    -classpath "${MIDP_API_JAR}" \
    -d "${CLASSES_DIR}" \
    @"${BUILD_DIR}/sources.list"

cp -R -- "${ROOT_DIR}/src/main/resources/." "${CLASSES_DIR}/"
# Release catalog, in the order LibraryList presents it. Cartridges kept in
# cartridges/ but absent here are test and benchmark fixtures only: Mandelbrot,
# Sound Test, Tankle and Game of Life are too expensive per frame, or unusable
# without a pointer, to belong in a handset-facing library.
for cartridge in \
    sokoban \
    wasm-wars \
    annoyingrobots \
    waternet \
    dragon-poker-draw \
    tictactoe \
    watris \
    glowfish-chess \
    duck-maze \
    untangle \
    nyancat \
    sound-demo \
    plasma-cube; do
    mkdir -p -- "${CLASSES_DIR}/cartridges"
    cp -- "${ROOT_DIR}/cartridges/${cartridge}.wasm" \
        "${CLASSES_DIR}/cartridges/${cartridge}.wasm"
done

mkdir -p -- "${CLASSES_DIR}/META-INF"
cp -- "${ROOT_DIR}/src/main/manifest/MANIFEST.MF" \
    "${CLASSES_DIR}/META-INF/MANIFEST.MF"
cp -- "${ROOT_DIR}/LICENSE" "${CLASSES_DIR}/META-INF/LICENSE"
cp -- "${ROOT_DIR}/THIRD_PARTY_NOTICES.md" \
    "${CLASSES_DIR}/META-INF/THIRD-PARTY-NOTICES.md"
# JAR entry timestamps otherwise make byte-identical source builds produce
# different phone artifacts and invalidate artifact-bound emulator receipts.
find "${CLASSES_DIR}" -exec touch -h -t 198001010000.00 -- {} +

cp -R -- "${CLASSES_DIR}" "${BASE_CLASSES_DIR}"
find "${BASE_CLASSES_DIR}/w4me/midp" \
    -name 'Jsr75FileSystem*.class' -delete
sed -i \
    's/, javax\.microedition\.io\.Connector\.file\.read$//' \
    "${BASE_CLASSES_DIR}/META-INF/MANIFEST.MF"

package_variant() {
    classes_dir="$1"
    stem="$2"
    include_jsr75="$3"
    raw_jar_path="${BUILD_DIR}/${stem}-unverified.jar"
    jar_path="${DIST_DIR}/${stem}.jar"
    jad_path="${DIST_DIR}/${stem}.jad"
    normalized_dir="${BUILD_DIR}/${stem}-normalized"
    normalized_jar_path="${BUILD_DIR}/${stem}-normalized.jar"

    jar cfM "${raw_jar_path}" -C "${classes_dir}" .

    java -jar "${PROGUARD_HOME}/lib/proguard.jar" \
        -injars "${raw_jar_path}" \
        -outjars "${jar_path}" \
        -libraryjars "${JDK8_HOME}/jre/lib/rt.jar" \
        -libraryjars "${MIDP_API_JAR}" \
        -dontshrink \
        -dontoptimize \
        -dontobfuscate \
        -dontwarn w4me.midp.Jsr75FileSystem \
        -dontnote w4me.midp.FileSystemAccessFactory \
        -microedition \
        -keepattributes '*'

    # ProGuard writes fresh timestamps even when every input entry is normalized.
    # Repack the preverified output in a sorted order without host-specific ZIP
    # attributes so a rebuild can be tied to the same emulator evidence by SHA-256.
    mkdir -p -- "${normalized_dir}"
    unzip -q "${jar_path}" -d "${normalized_dir}"
    find "${normalized_dir}" -exec touch -h -t 198001010000.00 -- {} +
    (
        cd -- "${normalized_dir}"
        find . -type f -print | LC_ALL=C sort |
            zip -X -q "${normalized_jar_path}" -@
    )
    mv -- "${normalized_jar_path}" "${jar_path}"

    jar_size="$(stat -c '%s' -- "${jar_path}")"
    {
        printf '%s\n' 'MIDlet-Name: W4ME Station'
        printf '%s\n' 'MIDlet-Version: 1.1.0'
        printf '%s\n' 'MIDlet-Vendor: W4ME'
        # Keep these in step with src/main/manifest/MANIFEST.MF. The icon path is
        # what a handset shows in its application menu and install dialog; an empty
        # icon field leaves the generic Java placeholder.
        printf '%s\n' 'MIDlet-Description: Run unmodified WASM-4 cartridges on Java ME phones'
        printf '%s\n' 'MIDlet-Info-URL: https://github.com/mulfyx/w4me-station'
        printf '%s\n' 'MIDlet-Icon: /icon.png'
        printf '%s\n' 'MIDlet-1: W4ME Station,/icon.png,w4me.midp.W4MeMidlet'
        printf '%s\n' 'MicroEdition-Configuration: CLDC-1.1'
        printf '%s\n' 'MicroEdition-Profile: MIDP-2.0'
        if [ "${include_jsr75}" = true ]; then
            printf '%s\n' 'MIDlet-Permissions-Opt: javax.microedition.io.Connector.http, javax.microedition.io.Connector.https, javax.microedition.io.Connector.file.read'
        else
            printf '%s\n' 'MIDlet-Permissions-Opt: javax.microedition.io.Connector.http, javax.microedition.io.Connector.https'
        fi
        printf 'MIDlet-Jar-URL: %s\n' "$(basename -- "${jar_path}")"
        printf 'MIDlet-Jar-Size: %s\n' "${jar_size}"
    } >"${jad_path}"

    "${ROOT_DIR}/tools/verify.sh" jar "${jar_path}"
    printf 'Built %s (%s bytes, Java ME preverified)\n' "${jar_path}" "${jar_size}"
}

package_variant "${CLASSES_DIR}" "w4me-station" true
package_variant "${BASE_CLASSES_DIR}" "w4me-station-base" false
