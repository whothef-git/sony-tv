# Host protocol mock tests

`SmartProtocolChecks.java` uses only Java 17, the production pure-Java model/crypto/session/parser
classes, and a host-only org.json parser. It does not communicate with physical devices.

The test-only parser used locally was org.json `json:20240303` from Maven Central. It is not an APK
dependency and is not bundled here. Compile this test with that jar plus `DeviceType.java`,
`ConnectionState.java`, `PowerState.java`, `SmartDevice.java`, `TpLinkCrypto.java`,
`TpLinkSession.java`, `TpLinkState.java` and `TpLinkDiscoveryCrypto.java`, placing them into a separate
output directory. Then run `tv.widgetv.home.SmartProtocolChecks` with that output and parser jar on
the classpath. The last local run passed 70 synthetic checks.

Android storage/connection-manager mocks are in
`app/src/androidTest/java/tv/widgetv/home/SmartHomeIntegrationChecks.java`. Build with
`gradlew.bat assembleDebug assembleDebugAndroidTest`, install the debug app and test APK on a test
TV, then run:

```text
adb shell am instrument -w tv.widgetv.home.test/tv.widgetv.home.SmartHomeIntegrationChecks
```

These 40 Android tests use mock drivers/discovery, a controlled monotonic clock and isolated
SharedPreferences files; they do not switch real devices. They test the actual Android Keystore,
registry persistence, lifecycle coordination, pending commands, deduplication and removals. Test
preferences are cleaned after the run. Reinstall the optimized release and uninstall the temporary
`tv.widgetv.home.test` package after testing. Hardware tests are a separate activity and must never
be inferred from mock results.
