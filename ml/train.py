import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score, confusion_matrix
import tensorflow as tf
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense, Dropout
from tensorflow.keras.utils import to_categorical

# -------------------------------
# 1. Load dataset
# -------------------------------
data = np.load("timeseries_dataset.npz")
X = data["X"]   # (samples, time_steps, features)
y = data["y"]   # (samples,)

print("Dataset shape:", X.shape, y.shape)

# -------------------------------
# 2. Normalize data (VERY IMPORTANT)
# -------------------------------
X = X / np.max(X)

# -------------------------------
# 3. Train-test split
# -------------------------------
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)

# -------------------------------
# 4. One-hot encoding
# -------------------------------
NUM_CLASSES = 4

y_train_cat = to_categorical(y_train, num_classes=NUM_CLASSES)
y_test_cat = to_categorical(y_test, num_classes=NUM_CLASSES)

# -------------------------------
# 5. Build LSTM model
# -------------------------------
model = Sequential([
    LSTM(64, return_sequences=True, input_shape=(X.shape[1], X.shape[2])),
    Dropout(0.3),

    LSTM(32),
    Dropout(0.3),

    Dense(32, activation="relu"),
    Dense(NUM_CLASSES, activation="softmax")
])

model.compile(
    optimizer="adam",
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)

model.summary()

# -------------------------------
# 6. Train model
# -------------------------------
history = model.fit(
    X_train, y_train_cat,
    epochs=25,
    batch_size=32,
    validation_data=(X_test, y_test_cat)
)

# -------------------------------
# 7. Evaluate
# -------------------------------
y_pred = model.predict(X_test)
y_pred_classes = np.argmax(y_pred, axis=1)

print("\nFinal Accuracy:", accuracy_score(y_test, y_pred_classes))

class_names = ["no_accident", "minor", "moderate", "severe"]

print("\nClassification Report:\n")
print(classification_report(y_test, y_pred_classes, target_names=class_names))

print("\nConfusion Matrix:\n")
print(confusion_matrix(y_test, y_pred_classes))

# -------------------------------
# 8. Save model
# -------------------------------
model.save("lstm_model.h5")

# Save class mapping (IMPORTANT for Android)
np.save("class_labels.npy", class_names)

print("\nModel saved as lstm_model.h5")
print("Class labels saved as class_labels.npy")