generate_preview.py
-------------------

This small script creates a thematic preview image for the mod and writes
it to build/preview.png.

Usage (Windows PowerShell):

    python -m pip install -r tools/requirements.txt
    python tools/generate_preview.py

Output: `build/preview.png`

If `arial.ttf` is not available, the script falls back to the default font.
