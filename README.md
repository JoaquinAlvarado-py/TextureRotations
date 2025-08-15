# Texture Rotation Reverser — Streamlit UI

A Streamlit frontend that runs the Java-based **TextureRotations** solver with a clean JSON I/O.

- Upstream Java repo: 19MisterX98/TextureRotations. Inputs are `RotationInfo` entries and you must set search bounds and a mode (Vanilla / Sodium variants). This UI mirrors that, without editing Java each run. [See README sections “How to input data” and “Search parameters”.]  
  Sources: GitHub README excerpts. :contentReference[oaicite:1]{index=1}

## Deploy locally
```bash
# python 3.10+
pip install -r requirements.txt

# first run compiles Java from upstream (needs git + JDK)
streamlit run app.py
