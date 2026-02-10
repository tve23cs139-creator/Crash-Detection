
# Crash Detection Android App 🚗💥

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![GitHub stars](https://img.shields.io/github/stars/your-username/your-repo?style=social)
![GitHub forks](https://img.shields.io/github/forks/your-username/your-repo?style=social)

A native Android application designed for automatic vehicle crash detection and emergency alert notification. The app runs a persistent background service to monitor the device's accelerometer for sudden, high-G-force impacts and, if a likely crash is detected, automatically sends an SMS alert with the user's location to a pre-configured emergency contact.

## 📜 Project Description

The primary goal of this project is to provide a safety net for drivers. In the event of a serious accident that might leave the driver incapacitated, this app can automatically call for help. It uses the built-in accelerometer to detect forces consistent with a vehicle collision. To prevent false alarms, it initiates a 10-second countdown with audible and visual warnings, giving the user a chance to cancel the alert if they are safe. If the alert is not canceled, the app sends a detailed SMS message, including the severity of the impact and a Google Maps link of their current location, to an emergency contact.

## ✨ Features

- **Automatic Crash Detection**: Utilizes the accelerometer to detect high G-force impacts (threshold > 5 Gs).
- **Background Monitoring**: Runs as a persistent Foreground Service to ensure monitoring continues even when the app is not in the foreground.
- **Configurable Countdown**: A 10-second countdown with an audible alarm begins after a potential crash is detected, allowing the user to cancel false alarms.
- **Emergency SMS Alerts**: Automatically sends an SMS to a designated emergency contact if the countdown is not canceled.
- **Location Sharing**: The alert SMS includes the device's latitude and longitude as a Google Maps link for easy location pinpointing.
- **Crash Severity Classification**: Classifies the impact as "MINOR", "SEVERE", or "EXTREME" based on the measured G-force.
- **Reactive UI**: A clean, simple UI built with Jetpack Compose that provides real-time status updates (e.g., "Monitoring", "Possible Crash Detected", "Crash Confirmed").
- **Wake Lock Management**: Ensures the CPU remains active to process sensor data, even when the device screen is off.

## ⚙️ How It Works

1.  **Service Initialization**: Upon granting necessary permissions, the app starts the `CrashMonitorService`, which runs as a foreground service.
2.  **Sensor Monitoring**: The service initializes the `CrashDetector`, which registers a listener for the device's accelerometer at a high frequency (`SENSOR_DELAY_GAME`).
3.  **Impact Detection**: The `CrashDetector` continuously calculates the total G-force. If the force exceeds the pre-defined threshold (`CRASH_G_FORCE_THRESHOLD`), it triggers the `onCrashDetected` callback.
4.  **Countdown Sequence**: The service starts a 10-second countdown, updates the main activity UI to a warning state, and plays a loud alert tone.
5.  **User Intervention**: During the countdown, the user can press the "I'M SAFE (CANCEL)" button on the screen to abort the emergency alert.
6.  **Crash Confirmation**: If the countdown completes without being canceled, the crash is confirmed.
7.  **Alert Dispatch**: The `LocationHelper` fetches the last known GPS coordinates, and the `SmsHelper` sends a formatted SMS message to the emergency contact. The message looks like this: `EMERGENCY: A SEVERE vehicle crash (G-Force: 15.2) has been detected. Location: https://www.google.com/maps?q=lat,lon`

## 🛠️ Tech Stack & Dependencies

-   **Core**: Kotlin
-   **UI**: Jetpack Compose
-   **Asynchronous Programming**: Native Threads for the countdown sequence.
-   **Android Components**: `Service`, `BroadcastReceiver`, `SensorManager`, `NotificationManager`.
-   **Location**: Google Play Services Location (`com.google.android.gms:play-services-location`).
-   **UI Icons**: Material Icons Extended (`androidx.compose.material:material-icons-extended`).

## 📋 Prerequisites

-   Android Studio Iguana | 2023.2.1 or later
-   Android SDK targeting API 33 or higher
-   A physical Android device with an accelerometer is recommended for accurate testing.

## 🚀 Installation and Setup

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-username/Crash-Detection.git
    ```
2.  **Open in Android Studio**:
    Open the cloned directory as a new project in Android Studio.
3.  **Set the Emergency Contact**:
    This is a critical step. The emergency contact number is currently hardcoded for demonstration purposes.
    -   Navigate to `app/src/main/java/com/example/crashdetection/MainActivity.kt`.
    -   In the `onCreate` method, find the line: `ContactStore.save(this, "8075459692")`.
    -   **Replace `"8075459692"` with the desired emergency phone number.**
4.  **Build and Run**:
    Let Gradle sync the dependencies, then build and run the app on your Android device or emulator.

## 📱 Usage

1.  Launch the app for the first time.
2.  The app will immediately request necessary permissions (SMS, Location, Notifications). You must **grant all permissions** for the app to function correctly.
3.  You will also be prompted to grant background location access. For the app to be able to get your location during a crash when the app is not open, you must select **"Allow all the time"**.
4.  Once permissions are granted, the service starts, and the main screen will show "System Active" and "Monitoring for crashes...".
5.  The app is now active. It can be closed, and the monitoring will continue in the background. A persistent notification will indicate that the `CrashMonitorService` is running.

## ⚠️ Permissions Required

This app requires several sensitive permissions to perform its core functions:

| Permission                         | Reason                                                                                   |
| ---------------------------------- | ---------------------------------------------------------------------------------------- |
| `SEND_SMS`                         | To send the emergency alert SMS to your contact.                                         |
| `ACCESS_FINE_LOCATION`             | To get the precise GPS coordinates to include in the alert message.                      |
| `ACCESS_COARSE_LOCATION`           | To get an approximate location if fine location is unavailable.                          |
| `ACCESS_BACKGROUND_LOCATION`       | **Crucial**: To fetch the location for the alert even if the app is not on screen.       |
| `POST_NOTIFICATIONS`               | To show the persistent notification for the foreground service and display crash alerts. |
| `FOREGROUND_SERVICE`               | To allow the monitoring service to run continuously in the background.                   |
| `FOREGROUND_SERVICE_SPECIAL_USE`   | Required for safety-critical applications running in the foreground.                     |
| `HIGH_SAMPLING_RATE_SENSORS`       | To get accelerometer data at a high frequency for accurate detection.                    |
| `WAKE_LOCK`                        | To ensure the sensor processing continues when the phone screen is turned off.           |
| `USE_FULL_SCREEN_INTENT`           | To wake the device and show the countdown screen immediately after a potential crash.    |

## 🧪 Testing

The project includes boilerplate unit and instrumentation tests from the Android Studio new project template.

-   `ExampleUnitTest.kt`: Basic local unit tests.
-   `ExampleInstrumentedTest.kt`: Basic on-device instrumentation tests.

**Note**: Dedicated tests for the core crash detection logic, service lifecycle, and permission handling have not yet been implemented.

## 🖼️ Screenshots

*(Screenshots have not been added yet. This section is a placeholder.)*

## 🚀 Future Improvements

This project has a solid foundation, but there are many opportunities for enhancement:

-   **Contact Management UI**: Build a settings screen where users can add, edit, and remove emergency contacts directly within the app, removing the need to hardcode the number.
-   **Contact Picker Integration**: Allow users to select a contact directly from their phone's address book.
-   **Adjustable Sensitivity**: Add a setting to adjust the G-force threshold to better suit different vehicles or user preferences.
-   **Emergency Call**: Add a button to directly call emergency services (e.g., 911, 112) after a crash is confirmed.
-   **Advanced Algorithm**: Incorporate other sensors like the gyroscope and magnetometer to create a more robust crash detection algorithm that can reduce false positives (e.g., by detecting a sudden stop followed by a roll).
-   **Localization**: Add support for multiple languages.

## 🙌 How to Contribute

Contributions are welcome! If you have ideas for new features or improvements, please follow these steps:

1.  Fork the Project.
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the Branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request.

Please open an issue first to discuss what you would like to change.

## 📄 License

This project is distributed under the MIT License. See `LICENSE` for more information. (Note: A `LICENSE` file has not yet been added to the repository).

## 🙏 Credits & Acknowledgements

-   This project was created to serve as a practical example of using Android services and sensors for a real-world safety application.
-   Inspiration from various vehicle safety technologies.
