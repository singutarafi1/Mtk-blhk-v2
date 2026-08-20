name: 🚀 Build Android APK (Release & Debug)

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

permissions:
  contents: write
  packages: write
  actions: read

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build-android:
    name: 📱 Build Android APK
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: 📥 Checkout Repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 1

      - name: ☕ Setup Java 17 (Temurin)
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: 🐘 Setup Gradle 9.3.1
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '9.3.1'

      - name: 🔑 Restore / Generate Debug Keystore
        run: |
          if [ -f "./debug.keystore.base64" ]; then
            echo "Decoding debug.keystore from base64..."
            base64 -d ./debug.keystore.base64 > ./debug.keystore
          elif [ ! -f "./debug.keystore" ]; then
            echo "Generating debug.keystore with keytool..."
            keytool -genkey -v -keystore ./debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
          fi
          echo "Debug Keystore Status:"
          ls -la ./debug.keystore

      - name: 🔧 Grant Execute Permissions for Gradlew
        run: |
          if [ -f "./gradlew" ]; then
            chmod +x ./gradlew
          fi

      - name: 📦 Build Android APK (Debug)
        run: |
          if [ -f "./gradlew" ]; then
            ./gradlew assembleDebug --stacktrace -Djava.awt.headless=true -Dkotlin.compiler.execution.strategy=in-process
          else
            gradle assembleDebug --stacktrace -Djava.awt.headless=true -Dkotlin.compiler.execution.strategy=in-process
          fi

      - name: 📤 Upload Android APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: MTK-UnlockTool-Android-APK
          path: app/build/outputs/apk/debug/*.apk
          retention-days: 30
          if-no-files-found: error
