## Smithy Kotlin

Smithy code generators for Kotlin.

**WARNING: All interfaces are subject to change.**

[![License][apache-badge]][apache-url]

[apache-badge]: https://img.shields.io/badge/License-Apache%202.0-blue.svg
[apache-url]: LICENSE

## Development

### Android Integration Tests

The client-runtime is meant to be compatible with and run on Android API 16+ devices.

The `android-test` project can be run manually with the script below. 

NOTE: Set `ANDROID_SDK` environment variable beforehand.

```
#!/bin/bash
ANDROID_SDK=${ANDROID_SDK:=~/Library/Android/sdk}
PATH=$ANDROID_SDK/emulator:$ANDROID_SDK/tools:$PATH

# start the emulator
$ANDROID_SDK/emulator/emulator -avd Nexus_4_API_16 -wipe-data & EMULATOR_PID=$!

# Wait for Android to finish booting
WAIT_CMD="$ANDROID_SDK/platform-tools/adb wait-for-device shell getprop init.svc.bootanim"
until $WAIT_CMD | grep -m 1 stopped; do
  echo "Waiting..."
  sleep 1
done

# Unlock the Lock Screen
$ANDROID_SDK/platform-tools/adb shell input keyevent 82

# Clear and capture logcat
$ANDROID_SDK/platform-tools/adb logcat -c
$ANDROID_SDK/platform-tools/adb logcat > build/logcat.log &
LOGCAT_PID=$!

cd ./smithy-kotlin
# Run the tests
./gradlew -DandroidEmulatorTests=true :android-test:connectedAndroidTest -i

cd ..

# Stop the background processes
kill -9 $LOGCAT_PID
kill -9 $EMULATOR_PID
```

## License

This project is licensed under the Apache-2.0 License.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

