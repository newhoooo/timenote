#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .test-build
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -Xlint:-options -d .test-build \
  app/src/main/java/app/moment/tracker/core/TimerMode.java \
  app/src/main/java/app/moment/tracker/core/SessionClock.java \
  app/src/main/java/app/moment/tracker/core/ReminderWindow.java \
  app/src/main/java/app/moment/tracker/core/UsageMath.java \
  tests/CoreBehaviorTest.java
java -cp .test-build CoreBehaviorTest
