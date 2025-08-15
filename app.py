import json, os, subprocess, sys, tempfile, pathlib, shutil
import streamlit as st

REPO_URL = "https://github.com/19MisterX98/TextureRotations.git"
WORKDIR = pathlib.Path(".").resolve()
JAVA_DIR = WORKDIR / "java_src"
BIN_DIR = WORKDIR / "bin"
JAR_PATH = BIN_DIR / "TextureRotations.jar"

def run(cmd, cwd=None):
    proc = subprocess.run(cmd, cwd=cwd, text=True, capture_output=True)
    return proc.returncode, proc.stdout, proc.stderr

@st.cache_resource(show_spinner=True)
def ensure_built():
    """
    1) Clone upstream repo
    2) Patch in a small CLI entrypoint (StreamlitMain.java)
    3) Compile to a single runnable JAR
    """
    BIN_DIR.mkdir(exist_ok=True)
    JAVA_DIR.mkdir(exist_ok=True)

    # If jar already exists, stop here.
    if JAR_PATH.exists():
        return str(JAR_PATH)

    # Step 1: fetch & prepare sources
    st.info("Cloning and preparing Java sources (first run only)…")
    code, out, err = run(["bash", "builders/fetch_and_patch.sh"])
    if code != 0:
        st.error("Build preparation failed.")
        st.code(err or out)
        raise RuntimeError("fetch_and_patch.sh failed")

    # Step 2: compile with javac → fat jar using jar tool
    st.info("Compiling Java sources…")
    src_dir = JAVA_DIR
    classes_dir = JAVA_DIR / "classes"
    classes_dir.mkdir(exist_ok=True)

    # find all .java
    java_files = []
    for p in src_dir.rglob("*.java"):
        java_files.append(str(p))
    if not java_files:
        st.error("No Java files found to compile.")
        raise RuntimeError("No Java files")

    # Compile
    code, out, err = run(["javac", "-O", "-d", str(classes_dir)] + java_files)
    if code != 0:
        st.error("javac failed.")
        st.code(err or out)
        raise RuntimeError("javac failed")

    # Create runnable jar with StreamlitMain as entrypoint
    manifest = classes_dir / "MANIFEST.MF"
    manifest.write_text("Main-Class: StreamlitMain\n")
    code, out, err = run(["jar", "cmf", str(manifest), str(JAR_PATH), "-C", str(classes_dir), "."])
    if code != 0:
        st.error("jar packaging failed.")
        st.code(err or out)
        raise RuntimeError("jar failed")

    return str(JAR_PATH)

st.set_page_config(page_title="Texture Rotation Reverser UI", page_icon="🧭", layout="centered")
st.title("🧭 Texture Rotation Reverser — Streamlit UI")

st.caption("Upload a JSON of observed rotations, set a search volume and MC/Sodium mode, and get candidate coordinates.")

with st.expander("📘 JSON input format (quick)"):
    st.code("""
{
  "origin_hint": [0, 0, 0],      // your chosen origin relative to the build (optional)
  "observations": [
    // x, y, z are relative to that origin block; rotation is the number from the 'Textures to numbers' pack
    {"x": 1, "y": 0, "z": 0, "rotation": 1, "sideOnly": false},
    {"x": -2, "y": 0, "z": 3, "rotation": 0, "sideOnly": false}
  ],
  "search": {
    "xMin": -4096, "xMax": 4096,
    "yMin": 0,     "yMax": 255,
    "zMin": -4096, "zMax": 4096,
    "threads": 8
  },
  "mode": "Vanilla21_1Textures"   // VanillaTextures | Vanilla21_1Textures | Vanilla12Textures | SodiumTextures | Sodium19Textures
}
""", language="json")

# Controls
uploaded = st.file_uploader("Upload rotations JSON", type=["json"])
col1, col2 = st.columns(2)
with col1:
    mode = st.selectbox("Mode", ["VanillaTextures","Vanilla21_1Textures","Vanilla12Textures","SodiumTextures","Sodium19Textures"], index=1)
    threads = st.number_input("Threads", min_value=1, max_value=32, value=8, step=1)
with col2:
    show_raw = st.checkbox("Show raw Java output", value=False)

st.markdown("### Search bounds")
a,b,c = st.columns(3)
with a:
    xMin = st.number_input("xMin", value=-4096)
    yMin = st.number_input("yMin", value=0)
    zMin = st.number_input("zMin", value=-4096)
with b:
    xMax = st.number_input("xMax", value=4096)
    yMax = st.number_input("yMax", value=255)
    zMax = st.number_input("zMax", value=4096)
with c:
    pass

run_it = st.button("🔎 Run solver")

if run_it:
    if not uploaded:
        st.warning("Please upload a JSON first.")
        st.stop()

    jar = ensure_built()

    # Merge the uploaded JSON with UI overrides (mode/threads/bounds)
    data = json.loads(uploaded.read().decode("utf-8"))
    data["mode"] = mode
    # enforce bounds
    data.setdefault("search", {})
    data["search"]["xMin"] = int(xMin)
    data["search"]["xMax"] = int(xMax)
    data["search"]["yMin"] = int(yMin)
    data["search"]["yMax"] = int(yMax)
    data["search"]["zMin"] = int(zMin)
    data["search"]["zMax"] = int(zMax)
    data["search"]["threads"] = int(threads)

    with tempfile.TemporaryDirectory() as tmpd:
        in_path = pathlib.Path(tmpd) / "input.json"
        out_path = pathlib.Path(tmpd) / "output.json"
        in_path.write_text(json.dumps(data))

        cmd = ["java", "-jar", jar, str(in_path), str(out_path)]
        code, out, err = run(cmd)
        if show_raw:
            st.subheader("Java stdout/stderr")
            st.code((out or "") + "\n" + (err or ""))

        if code != 0:
            st.error("Java process failed.")
            st.stop()

        if not out_path.exists():
            st.error("No output produced.")
            st.stop()

        result = json.loads(out_path.read_text())
        st.success("Done!")
        st.json(result)

        # allow download
        st.download_button("⬇️ Download results JSON", data=json.dumps(result, indent=2), file_name="texture_results.json", mime="application/json")
