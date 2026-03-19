import numpy as np
import os

# ==============================
# CONFIG
# ==============================
SAMPLES = 12000
WINDOW_SIZE = 50
FEATURES = 6

CLASSES = {
    0: "MINOR",
    1: "SEVERE",
    2: "EXTREME"
}

np.random.seed(42)

# ==============================
# BASE MOTION (PRE-CRASH)
# ==============================
def generate_base_motion():
    t = np.linspace(0, 2*np.pi, WINDOW_SIZE)

    seq = np.zeros((WINDOW_SIZE, FEATURES))

    # accelerometer (smooth motion)
    for i in range(3):
        seq[:, i] = np.sin(t + np.random.rand()) * np.random.uniform(0.5, 1.5)

    # gyroscope (rotation)
    for i in range(3, 6):
        seq[:, i] = np.cos(t + np.random.rand()) * np.random.uniform(0.2, 1.0)

    seq += np.random.normal(0, 0.05, seq.shape)
    return seq

# ==============================
# CRASH GENERATOR
# ==============================
def generate_crash(severity):
    seq = generate_base_motion()

    t = np.random.randint(10, 40)

    if severity == 0:  # MINOR
        spike = np.random.uniform(1.5, 3)
        decay = np.random.uniform(0.3, 0.6)

    elif severity == 1:  # SEVERE
        spike = np.random.uniform(3, 6)
        decay = np.random.uniform(0.1, 0.3)

    else:  # EXTREME
        spike = np.random.uniform(6, 10)
        decay = np.random.uniform(0.01, 0.1)

    # ===== IMPACT SPIKE =====
    for k in range(3):
        seq[t + k] += spike * np.random.randn(FEATURES)

    # ===== DIRECTIONAL IMPACT =====
    direction = np.random.choice([0, 1, 2])
    seq[t:t+3, direction] *= 2

    # ===== GYRO BURST =====
    seq[t:t+3, 3:] += np.random.uniform(2, 5)

    # ===== SUDDEN STOP =====
    seq[t+3:] *= decay

    # ===== POST IMPACT NOISE =====
    seq[t+3:] += np.random.normal(0, 0.02, seq[t+3:].shape)

    return seq

# ==============================
# DATASET GENERATION
# ==============================
def generate_dataset():
    X = []
    y = []

    for _ in range(SAMPLES):
        cls = np.random.choice([0, 1, 2])  # 3 classes

        seq = generate_crash(cls)

        X.append(seq)
        y.append(cls)

    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)

# ==============================
# SAVE
# ==============================
if __name__ == "__main__":
    print("🚀 Generating 3-class crash dataset...")

    X, y = generate_dataset()

    print("Shape:", X.shape, y.shape)
    print("Class distribution:", np.bincount(y))

    os.makedirs("dataset", exist_ok=True)

    np.save("dataset/X.npy", X)
    np.save("dataset/y.npy", y)

    print("✅ Dataset saved in /dataset")