import tensorflow as tf
from tensorflow.keras import layers, models
import numpy as np
import os
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report

# ==============================
# CONFIG
# ==============================
WINDOW_SIZE = 50
FEATURES = 6
CLASSES = ['MINOR', 'SEVERE', 'EXTREME']

np.random.seed(42)
tf.random.set_seed(42)

# ==============================
# LOAD DATA
# ==============================
def load_data():
    X = np.load("dataset/X.npy")
    y = np.load("dataset/y.npy")
    return X, y

# ==============================
# NORMALIZATION (IMPORTANT)
# ==============================
def normalize(X):
    mean = np.mean(X, axis=(0,1), keepdims=True)
    std = np.std(X, axis=(0,1), keepdims=True) + 1e-6
    return (X - mean) / std

# ==============================
# MODEL (slightly improved)
# ==============================
def create_model():
    model = models.Sequential([
        layers.Input(shape=(WINDOW_SIZE, FEATURES)),

        layers.Conv1D(64, 3, activation='relu'),
        layers.BatchNormalization(),

        layers.Conv1D(64, 3, activation='relu'),
        layers.Dropout(0.3),

        layers.Conv1D(32, 3, activation='relu'),

        layers.GlobalAveragePooling1D(),

        layers.Dense(32, activation='relu'),
        layers.Dropout(0.3),

        layers.Dense(len(CLASSES), activation='softmax')
    ])

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )

    return model

# ==============================
# EXPORT TFLITE (UNCHANGED)
# ==============================
def export_to_tflite(model, output_path):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    with open(output_path, 'wb') as f:
        f.write(tflite_model)

    print(f"✅ Model exported to {output_path}")

# ==============================
# MAIN
# ==============================
if __name__ == "__main__":
    print("🚀 Loading dataset...")
    X, y = load_data()

    print("📊 Raw shape:", X.shape, y.shape)

    print("🔄 Normalizing...")
    X = normalize(X)

    print("🔀 Splitting...")
    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=0.2, random_state=42
    )

    print("🧠 Building model...")
    model = create_model()
    model.summary()

    print("🏋️ Training...")
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=25,
        batch_size=32
    )

    print("📈 Evaluating...")
    y_pred = model.predict(X_val)
    y_pred = np.argmax(y_pred, axis=1)

    print("\nClassification Report:")
    print(classification_report(y_val, y_pred))

    # ==============================
    # SAVE MODEL
    # ==============================
    os.makedirs('models', exist_ok=True)
    model.save('models/crash_severity_model.keras')

    # ==============================
    # EXPORT TO ANDROID
    # ==============================
    android_assets_path = "../app/src/main/assets/model.tflite"
    export_to_tflite(model, android_assets_path)