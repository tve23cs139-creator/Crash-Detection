# Crash Detection System

End-to-end crash monitoring system with:
- Android app for on-device crash detection + emergency SMS alerts
- Firebase Realtime Database for incident logging
- React dashboard for live SOC monitoring and rescue dispatch visualization
- Python ML pipeline for synthetic data generation and severity model training

## Repository Structure

```text
Crash-Detection/
  app/                 Android app (Kotlin + Compose + TFLite + Firebase logging)
  dashboard/           React/Vite SOC dashboard (Firebase + Google Maps)
  ml/                  Dataset generation + model training/export scripts
  README.md
```

## Full Functionality Breakdown

### 1) Android Crash Detection App (`app/`)

Core runtime flow:
1. `MainActivity` requests permissions (SMS, location, notifications), initializes logging, and starts `CrashMonitorService`.
2. `CrashMonitorService` runs as a foreground service with a persistent notification and acquires a partial wake lock.
3. `SensorFusionCrashDetector` continuously listens to accelerometer + gyroscope data (`SENSOR_DELAY_GAME`).
4. Detection logic uses staged confirmation:
   - impact threshold (`gForce > 5.0`)
   - orientation change threshold (`gyro magnitude > 3.0 rad/s`)
   - confirmation window (`300 ms`)
5. On detected candidate crash:
   - starts 10s countdown
   - sends UI updates to activity via broadcast
   - plays warning tones
6. If user cancels, monitoring resumes.
7. If not canceled:
   - severity is predicted with TFLite model (`model.tflite`) when available
   - fallback severity rules are used if model fails/unavailable
   - emergency SMS is sent with Google Maps location URL
   - event is logged to Firebase under `reported_accidents`

Main Android components:
- `MainActivity.kt`: UI state, permission flow, cancel action, registers simulation receiver.
- `CrashMonitorService.kt`: long-running monitoring service, countdown/alert logic, SMS + DB logging.
- `SensorFusionCrashDetector.kt`: sensor fusion detector (accel + gyro).
- `CrashSeverityModel.kt`: TFLite load, health check, preprocessing, inference.
- `LocationHelper.kt`: fused last known location fetch.
- `SmsHelper.kt`: sends SMS with `SmsManager`.
- `FirebaseLogger.kt`: writes crash payloads to Firebase Realtime Database.
- `ContactStore.kt`: stores emergency number in SharedPreferences.
- `SimulationReceiver.kt`: accepts simulated crash broadcasts for logging.

Detection thresholds and timing currently in code:
- Impact threshold: `5.0 G`
- Gyroscope threshold: `3.0 rad/s`
- Confirmation window: `300 ms`
- Debounce between events: `1500 ms`
- User cancel countdown: `10 s`

### 2) SOC Dashboard (`dashboard/`)

Features:
- Subscribes to Firebase `reported_accidents` in real time.
- Displays:
  - total incidents
  - max recorded g-force
  - active alerts count (`SEVERE` + `EXTREME`)
  - severity distribution bar chart
  - recent incident table with Google Maps deep links
- `MapComponent` functionality:
  - plots latest crash location
  - discovers nearest hospitals via Places API (within 10 km)
  - calculates live driving route/ETA via Directions API
  - allows dispatch selection and logs dispatch records to Firebase `dispatches`

Main files:
- `src/App.jsx`: live dashboard analytics + incident table
- `src/MapComponent.jsx`: map, hospital discovery, routing, dispatch workflow
- `src/firebase.js`: dashboard Firebase config

### 3) ML Pipeline (`ml/`)

Provided scripts:
- `generate_dataset.py`
  - creates synthetic 3-class crash sequences (`MINOR`, `SEVERE`, `EXTREME`)
  - outputs `dataset/X.npy` and `dataset/y.npy`
- `train.py`
  - trains 1D CNN model on generated windows (`50 x 6` features)
  - saves Keras model (`models/crash_severity_model.keras`)
  - exports TFLite model to Android assets (`../app/src/main/assets/model.tflite`)

Model metadata lives at:
- `app/src/main/assets/model_meta.json`

## Setup

### 1) Android App

Prerequisites:
- Android Studio (API 34 compile SDK, min SDK 33)
- Physical Android device recommended (sensor + SMS testing)

Run:
1. Open project in Android Studio.
2. Ensure emergency number is set in `MainActivity`:
   - `ContactStore.save(this, "9562153025")`
3. Grant runtime permissions on first launch:
   - SMS
   - Fine/Coarse location
   - Notifications
   - Background location
4. Start app and keep foreground service active.

### 2) Dashboard

Prerequisites:
- Node.js 18+
- Google Maps JavaScript API key with Places + Directions + Geometry enabled

Run:
```bash
cd dashboard
npm install
npm run dev
```

Environment:
- Set `VITE_GOOGLE_MAPS_API_KEY` for map features.
- Firebase credentials are currently hardcoded in `dashboard/src/firebase.js`.

### 3) ML Training

```bash
cd ml
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python generate_dataset.py
python train.py
```

## Firebase Data Model

Realtime Database paths used by this repo:
- `reported_accidents/{id}`
  - `timestamp`
  - `maxG`
  - `severity`
  - `latitude`
  - `longitude`
- `dispatches/{id}`
  - crash info
  - selected hospital info
  - ETA/distance
  - `dispatchedAt`

## Permissions Used by Android App

- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`
- `HIGH_SAMPLING_RATE_SENSORS`
- `WAKE_LOCK`
- `USE_FULL_SCREEN_INTENT`
- `SEND_SMS`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`

## Important Notes

- `FirebaseLogger.kt` contains placeholder constants:
  - `API_KEY = "REPLACE_WITH_FIREBASE_API_KEY"`
  - `APPLICATION_ID = "REPLACE_WITH_FIREBASE_APP_ID"`
  Replace these for reliable Android-side Firebase writes.
- `com.google.gms.google-services` is declared in `app/build.gradle.kts` with `apply false`; if you want automatic Firebase Android config, apply it normally in the app module.
- Emergency contact is hardcoded in `MainActivity` during startup.
- Automated tests are currently template-level only; crash detection and service workflows are not covered by dedicated tests.

## Quick Simulation (Android)

You can simulate a crash broadcast from ADB:

```bash
adb shell am broadcast -a com.example.crashdetection.SIMULATE_CRASH --ef maxG 14.2 --es severity SEVERE
```

This triggers Firebase logging path(s) for test data.
