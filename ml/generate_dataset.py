import numpy as np
import random

NUM_SAMPLES_PER_CLASS = 300
TIME_STEPS = 50

def gaussian_noise(val, scale=1.0):
    return val + np.random.normal(0, scale)

def random_spike_window():
    start = random.randint(5, 30)
    duration = random.randint(2, 8)
    return start, start + duration

# -------------------------------
# NO ACCIDENT (with fake spikes)
# -------------------------------
def generate_no_accident():
    seq = []
    spike_start, spike_end = random_spike_window()

    for t in range(TIME_STEPS):
        # base normal motion
        ax = random.uniform(0.5, 4)
        ay = random.uniform(0.5, 4)
        az = random.uniform(8, 12)

        # occasional fake spike (like phone drop)
        if spike_start <= t <= spike_end:
            ax = random.uniform(8, 18)
            ay = random.uniform(8, 18)
            az = random.uniform(12, 25)

        gx = random.uniform(0.1, 1.0)
        gy = random.uniform(0.1, 1.0)
        gz = random.uniform(0.1, 1.0)

        # add noise
        ax = gaussian_noise(ax, 1.5)
        ay = gaussian_noise(ay, 1.5)
        az = gaussian_noise(az, 1.5)

        seq.append([ax, ay, az, gx, gy, gz])

    return np.array(seq)

# -------------------------------
# MINOR (overlaps with no_accident)
# -------------------------------
def generate_minor():
    seq = []
    spike_start, spike_end = random_spike_window()

    for t in range(TIME_STEPS):
        ax = random.uniform(1, 5)
        ay = random.uniform(1, 5)
        az = random.uniform(8, 12)

        if spike_start <= t <= spike_end:
            ax = random.uniform(6, 12)
            ay = random.uniform(6, 12)
            az = random.uniform(10, 18)

        gx = random.uniform(0.2, 1.2)
        gy = random.uniform(0.2, 1.2)
        gz = random.uniform(0.2, 1.2)

        ax = gaussian_noise(ax, 2.0)
        ay = gaussian_noise(ay, 2.0)
        az = gaussian_noise(az, 2.0)

        seq.append([ax, ay, az, gx, gy, gz])

    return np.array(seq)

# -------------------------------
# MODERATE (messy + multi spikes)
# -------------------------------
def generate_moderate():
    seq = []
    spike1_start, spike1_end = random_spike_window()
    spike2_start, spike2_end = random_spike_window()

    for t in range(TIME_STEPS):
        ax = random.uniform(3, 8)
        ay = random.uniform(3, 8)
        az = random.uniform(9, 15)

        if spike1_start <= t <= spike1_end or spike2_start <= t <= spike2_end:
            ax = random.uniform(10, 20)
            ay = random.uniform(10, 20)
            az = random.uniform(15, 30)

        gx = random.uniform(0.5, 2.0)
        gy = random.uniform(0.5, 2.0)
        gz = random.uniform(0.5, 2.0)

        ax = gaussian_noise(ax, 2.5)
        ay = gaussian_noise(ay, 2.5)
        az = gaussian_noise(az, 2.5)

        seq.append([ax, ay, az, gx, gy, gz])

    return np.array(seq)

# -------------------------------
# SEVERE (chaotic + overlapping)
# -------------------------------
def generate_severe():
    seq = []
    spike_start, spike_end = random_spike_window()

    for t in range(TIME_STEPS):
        ax = random.uniform(5, 15)
        ay = random.uniform(5, 15)
        az = random.uniform(10, 20)

        if spike_start <= t <= spike_end:
            ax = random.uniform(18, 35)
            ay = random.uniform(18, 35)
            az = random.uniform(20, 45)

        # chaotic movement after impact
        if t > spike_end and random.random() < 0.5:
            ax = random.uniform(8, 20)
            ay = random.uniform(8, 20)
            az = random.uniform(10, 25)

        gx = random.uniform(1.0, 4.0)
        gy = random.uniform(1.0, 4.0)
        gz = random.uniform(1.0, 4.0)

        ax = gaussian_noise(ax, 3.0)
        ay = gaussian_noise(ay, 3.0)
        az = gaussian_noise(az, 3.0)

        seq.append([ax, ay, az, gx, gy, gz])

    return np.array(seq)

# -------------------------------
# DATASET GENERATION
# -------------------------------
def generate_dataset():
    X = []
    y = []

    for _ in range(NUM_SAMPLES_PER_CLASS):
        X.append(generate_no_accident()); y.append(0)
        X.append(generate_minor()); y.append(1)
        X.append(generate_moderate()); y.append(2)
        X.append(generate_severe()); y.append(3)

    X = np.array(X)
    y = np.array(y)

    # Shuffle
    indices = np.arange(len(X))
    np.random.shuffle(indices)

    X = X[indices]
    y = y[indices]

    np.savez("timeseries_dataset.npz", X=X, y=y)

    print("Dataset saved as timeseries_dataset.npz")
    print("Shape:", X.shape, y.shape)

if __name__ == "__main__":
    generate_dataset()