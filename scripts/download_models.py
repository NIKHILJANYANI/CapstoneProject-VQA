#!/usr/bin/env python3
"""
Download required TFLite models for the Visual Assistant app.

Usage:
    python scripts/download_models.py

Models downloaded:
    1. efficientdet_lite0.tflite  - Object detection (EfficientDet-Lite0, ~6MB)
    2. coco_labels.txt            - COCO class labels

Place them in: app/src/main/assets/
"""

import os
import sys
import urllib.request
import hashlib

ASSETS_DIR = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets')

MODELS = [
    {
        "name": "efficientdet_lite0.tflite",
        "url": "https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/metadata/1?lite-format=tflite",
        "description": "EfficientDet-Lite0 INT8 object detector (COCO 80 classes, ~6MB)"
    },
]

LABELS = {
    "name": "coco_labels.txt",
    "content": """person
bicycle
car
motorcycle
airplane
bus
train
truck
boat
traffic light
fire hydrant
stop sign
parking meter
bench
bird
cat
dog
horse
sheep
cow
elephant
bear
zebra
giraffe
backpack
umbrella
handbag
tie
suitcase
frisbee
skis
snowboard
sports ball
kite
baseball bat
baseball glove
skateboard
surfboard
tennis racket
bottle
wine glass
cup
fork
knife
spoon
bowl
banana
apple
sandwich
orange
broccoli
carrot
hot dog
pizza
donut
cake
chair
couch
potted plant
bed
dining table
toilet
tv
laptop
mouse
remote
keyboard
cell phone
microwave
oven
toaster
sink
refrigerator
book
clock
vase
scissors
teddy bear
hair drier
toothbrush"""
}

def ensure_assets_dir():
    os.makedirs(ASSETS_DIR, exist_ok=True)
    print(f"Assets directory: {os.path.abspath(ASSETS_DIR)}")

def download_model(model_info):
    dest_path = os.path.join(ASSETS_DIR, model_info["name"])
    if os.path.exists(dest_path):
        size_mb = os.path.getsize(dest_path) / (1024 * 1024)
        print(f"  ✓ {model_info['name']} already exists ({size_mb:.1f} MB)")
        return

    print(f"  ↓ Downloading {model_info['name']}...")
    print(f"    {model_info['description']}")
    print(f"    URL: {model_info['url']}")

    try:
        def progress_hook(block_num, block_size, total_size):
            downloaded = block_num * block_size
            if total_size > 0:
                percent = min(100, downloaded * 100 // total_size)
                mb_downloaded = downloaded / (1024 * 1024)
                mb_total = total_size / (1024 * 1024)
                print(f"\r    {percent}% ({mb_downloaded:.1f}/{mb_total:.1f} MB)", end='', flush=True)

        urllib.request.urlretrieve(model_info["url"], dest_path, progress_hook)
        print()  # newline after progress

        size_mb = os.path.getsize(dest_path) / (1024 * 1024)
        print(f"  ✓ Downloaded {model_info['name']} ({size_mb:.1f} MB)")
    except Exception as e:
        print(f"\n  ✗ Failed to download {model_info['name']}: {e}")
        print(f"    Please download manually from: {model_info['url']}")
        print(f"    And place it at: {dest_path}")

def write_labels():
    dest_path = os.path.join(ASSETS_DIR, LABELS["name"])
    if os.path.exists(dest_path):
        print(f"  ✓ {LABELS['name']} already exists")
        return

    with open(dest_path, 'w') as f:
        f.write(LABELS["content"].strip())
    print(f"  ✓ Created {LABELS['name']}")

def main():
    print("=" * 60)
    print("Visual Assistant — Model Setup")
    print("=" * 60)

    ensure_assets_dir()
    print()

    print("Downloading models:")
    for model in MODELS:
        download_model(model)

    print()
    print("Creating label files:")
    write_labels()

    print()
    print("=" * 60)
    print("Setup complete!")
    print()
    print("Next steps:")
    print("  1. Open the project in Android Studio")
    print("  2. Let Gradle sync")
    print("  3. Build and run on a physical Android device (API 26+)")
    print()
    print("IMPORTANT: Enable offline speech recognition on your device:")
    print("  Settings → General Management → Language → Text-to-speech")
    print("  Or: Settings → Accessibility → Text-to-speech → Offline languages")
    print("=" * 60)

if __name__ == "__main__":
    main()
