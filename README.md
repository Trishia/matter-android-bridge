# Matter Android Bridge App Example

This is a Matter Android Bridge application that functions as a Matter Bridge,
porting the logic from the Linux Bridge App (`examples/bridge-app/linux`) to the
Android platform.

## Features

-   **Matter Bridge Functionality**: Acts as a bridge for non-Matter devices,
    exposing them to the Matter fabric.
-   **Dynamic Device Management**:
    -   **Add Devices**: Dynamically add new bridged devices (Lights,
        Temperature Sensors) via the UI.
    -   **Remove Devices**: Remove existing bridged devices.
-   **Interactive UI**:
    -   **Device List**: Visualizes all bridged devices and their status.
    -   **Control**: Toggle lights and adjust temperature sensor values directly
        from the app.
-   **Commissioning**:
    -   **QR Code**: Displays the commissioning QR code and manual pairing code
        for easy setup.

## Requirements for Building

For information about how to build the application, see the
[Building Android](../../../docs/platforms/android/android_building.md) guide.

## Preparing for Build

1.  Check out the Matter repository.
2.  Run bootstrap (**only required first time**):

    ```shell
    source scripts/bootstrap.sh
    ```

## Building & Installing the App

### Command Line Build (Recommended for C++ changes)

To build the full application including the C++ native libraries:

```shell
./scripts/build/build_examples.py --target android-arm64-bridge-app build
```

The debug Android package `app-debug.apk` will be generated at:
`examples/bridge-app/android/App/build/BridgeApp/app/outputs/apk/debug/app-debug.apk`

Install it with:

```shell
adb install examples/bridge-app/android/App/build/BridgeApp/app/outputs/apk/debug/app-debug.apk
```

### Android Studio Development

The project is configured to build the Java/Kotlin layer using Gradle in Android
Studio. However, it relies on pre-compiled C++ libraries (`.so` files) located
in `libs/jniLibs`.

**Important Workflow:**

-   **Java/Kotlin Changes**: Can be built and deployed directly from Android
    Studio.
-   **C++ Changes**: You **MUST** rebuild the native libraries from the command
    line before running in Android Studio. Android Studio will **not** rebuild
    the C++ code automatically.

**Command to rebuild C++:**

```shell
./scripts/build/build_examples.py --target android-arm64-bridge-app build
```

## Usage

1.  **Launch**: Open "Matter Bridge" on your Android device.
2.  **Permissions**: Grant the requested permissions (Bluetooth, Location).
3.  **Commissioning**:
    -   Tap the menu icon (top right) and select "Show QR Code".
    -   Scan the QR code with your Matter controller (e.g., chip-tool, Google
        Home).
4.  **Manage Devices**:
    -   **Add**: Tap the FAB (+) button to add a new device.
    -   **Remove**: Tap the delete icon on a device card.
    -   **Control**: Use the switches and buttons to control device state.
