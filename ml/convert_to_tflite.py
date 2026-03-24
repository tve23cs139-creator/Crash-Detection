import tensorflow as tf

# Load model
model = tf.keras.models.load_model("lstm_model.h5")

# Convert
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# ✅ IMPORTANT FIXES
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS  # allow TensorFlow ops
]

converter._experimental_lower_tensor_list_ops = False

# Optional optimization
converter.optimizations = [tf.lite.Optimize.DEFAULT]

# Convert
tflite_model = converter.convert()

# Save
output_path = "../app/src/main/assets/model.tflite"

with open(output_path, "wb") as f:
    f.write(tflite_model)

print("TFLite model saved at:", output_path)