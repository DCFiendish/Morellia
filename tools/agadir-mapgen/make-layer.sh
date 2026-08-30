#!/bin/bash
# Converts a set of .schem files into a real WorldPainter .layer file via MakeLayer.java.
# Usage: ./make-layer.sh "<Layer Name>" output.layer schem1.schem schem2.schem ...
# Requires a JDK (javac/java on PATH or set JAVA_HOME below) and WorldPainter installed at
# WP_HOME. Only needs to compile once; re-run java directly for subsequent conversions if you
# want to skip the javac step.
set -e

WP_HOME="${WP_HOME:-C:\\Program Files\\WorldPainter}"
LIB="$WP_HOME\\lib"
JAVA_HOME_DIR="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.10.7-hotspot}"
JAVAC="$JAVA_HOME_DIR/bin/javac.exe"
JAVA="$JAVA_HOME_DIR/bin/java.exe"

CP=".;$LIB\\WPCore.jar;$LIB\\JNBT.jar;$LIB\\vecmath.jar;$LIB\\Utils.jar;$LIB\\guava.jar;$LIB\\WPValueObjects.jar;$LIB\\common-lang.jar;$LIB\\slf4j-api.jar;$LIB\\logback-classic.jar;$LIB\\logback-core.jar;$LIB\\jackson-databind.jar;$LIB\\jackson-core.jar;$LIB\\jackson-annotations.jar;$LIB\\common-io.jar;$LIB\\SwingUtils.jar;$LIB\\common-image.jar;$LIB\\snakeyaml.jar;$LIB\\jide-common.jar;$LIB\\failureaccess.jar;$LIB\\listenablefuture.jar;$LIB\\jcl-over-slf4j.jar;$LIB\\jul-to-slf4j.jar;$LIB\\log4j-api.jar;$LIB\\log4j-to-slf4j.jar"

cd "$(dirname "$0")"
"$JAVAC" -cp "$CP" -d . MakeLayer.java
"$JAVA" -cp "$CP" MakeLayer "$@"
