import argparse
import hashlib
import json
import os
import shutil
import uuid
import socket
import subprocess
import time
import zipfile
from pathlib import Path

arguments = argparse.ArgumentParser()
arguments.add_argument("--skybox-source", type=Path)
options = arguments.parse_args()
head = Path(__file__).resolve().parents[1]
root = head / "build" / "capture-evidence" / str(uuid.uuid4())
root.mkdir(parents=True)
(head / "build" / "last-capture-evidence-dir.txt").write_text(str(root), encoding="utf-8")
print("CAPTURE_EVIDENCE=" + str(root), flush=True)
java_home = os.environ.get("JAVA_HOME")
java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else shutil.which("java")
if not java or not Path(java).is_file():
    raise RuntimeError("Set JAVA_HOME to the project's Java 25 JDK or make java available on PATH")
creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
launches = json.loads((head / "build/capture-launch.json").read_text(encoding="utf-8"))
scope = head / "build/capture-scope.json"
if scope.is_file():
    shutil.copyfile(scope, root / "capture-scope.json")
for launch in launches.values():
    run = Path(launch["workingDirectory"]).resolve()
    if not run.is_relative_to((head / "build/run").resolve()):
        raise RuntimeError("Capture path escaped the isolated build/run root")
    launch["args"] = [str(value) for value in launch["args"]]
    run.mkdir(parents=True, exist_ok=True)
server_run = Path(launches["runCaptureServer"]["workingDirectory"])
if (server_run / "capture-world").exists():
    raise RuntimeError("Refusing to reuse any existing capture world")
properties = (server_run / "server.properties").read_text(encoding="utf-8")
if "server-ip=127.0.0.1" not in properties or "online-mode=false" not in properties:
    raise RuntimeError("Expected a loopback-only offline isolated server")
port = int(next(line.split("=", 1)[1] for line in properties.splitlines() if line.startswith("server-port=")))
with socket.socket() as probe:
    probe.bind(("127.0.0.1", port))
texture_evidence = []
is_sky = "-Dsemiontd.capture.mode=sky" in launches["runCaptureServer"]["args"]
if is_sky:
    if options.skybox_source is None or not options.skybox_source.is_dir():
        raise RuntimeError("Sky capture requires --skybox-source pointing to read-only original PNGs")
    source = options.skybox_source.resolve()
    destination = server_run / "config/semion-td/skyboxes"
    for texture in sorted(source.rglob("*.png")):
        relative = texture.relative_to(source)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        data = texture.read_bytes()
        target.write_bytes(data)
        texture_evidence.append({"source": str(texture), "copy": str(target), "sha256": hashlib.sha256(data).hexdigest()})
    if not texture_evidence:
        raise RuntimeError("No skybox PNGs were found")
(root / "skybox-source-evidence.json").write_text(json.dumps(texture_evidence, indent=2), encoding="utf-8")
server = None
client = None
result = {"serverRunDirectory": str(server_run), "port": port,
          "clientRunDirectory": launches["runClientGameTest"]["workingDirectory"]}
try:
    with (root / "capture-server.log").open("w", encoding="utf-8") as server_log:
        spec = launches["runCaptureServer"]
        server = subprocess.Popen([str(java), *spec["args"]], cwd=spec["workingDirectory"],
                                  stdin=subprocess.PIPE, stdout=server_log, stderr=subprocess.STDOUT,
                                  creationflags=creation_flags)
        result["serverPid"] = server.pid
        print("CAPTURE_SERVER_PID=" + str(server.pid), flush=True)
        (root / "capture-processes.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        deadline = time.monotonic() + 300
        while True:
            if server.poll() is not None:
                raise RuntimeError("The isolated capture server exited with " + str(server.returncode))
            log = (root / "capture-server.log").read_text(encoding="utf-8", errors="replace")
            if "Done (" in log:
                break
            if time.monotonic() > deadline:
                raise TimeoutError("The isolated server did not finish startup within 300 seconds")
            time.sleep(1)
        if "-Dsemiontd.capture.mode=dialog" in launches["runCaptureServer"]["args"]:
            pack = server_run / "polymer/resource_pack.zip"
            deadline = time.monotonic() + 180
            while True:
                if server.poll() is not None:
                    raise RuntimeError("The isolated server exited before native card pack readiness")
                try:
                    with zipfile.ZipFile(pack) as archive:
                        paths = ["assets/semion-td/font/augment_card_dialog.json",
                                 "assets/semion-td/textures/font/augment_dialog_cards.png",
                                 "assets/semion-td/textures/font/augment_dialog_buttons.png",
                                 "assets/semion-td/textures/font/augment-icons.png"]
                        assets = {path: archive.read(path) for path in paths}
                        font = json.loads(assets[paths[0]])
                        if len(font["providers"]) != 2 or len(font["providers"][0]["chars"]) != 19 or any(
                                len(row) != 39 for row in font["providers"][0]["chars"]):
                            raise ValueError("Unexpected native card row font contract")
                        original = head / "src/main/resources/semiontd/ui/augment-icons.png"
                        if assets[paths[3]] != original.read_bytes():
                            raise ValueError("The final pack did not preserve the supplied original icon atlas")
                        evidence = {"pack": str(pack), "sha256": hashlib.sha256(pack.read_bytes()).hexdigest(),
                                    "originalAtlasPreserved": True, "entrySha256": {path: hashlib.sha256(data).hexdigest()
                                                                                  for path, data in assets.items()}}
                        (root / "native-card-pack-readiness.json").write_text(json.dumps(evidence, indent=2), encoding="utf-8")
                        print("CAPTURE_NATIVE_CARD_PACK_READY=" + evidence["sha256"], flush=True)
                        break
                except (OSError, KeyError, ValueError, zipfile.BadZipFile) as error:
                    if time.monotonic() > deadline:
                        raise TimeoutError("Native card resource pack not ready: " + str(error)) from error
                    time.sleep(1)
        with (root / "capture-client.log").open("w", encoding="utf-8") as client_log:
            spec = launches["runClientGameTest"]
            client = subprocess.Popen([str(java), *spec["args"]], cwd=spec["workingDirectory"],
                                      stdout=client_log, stderr=subprocess.STDOUT,
                                      creationflags=creation_flags)
            result["clientPid"] = client.pid
            print("CAPTURE_CLIENT_PID=" + str(client.pid), flush=True)
            (root / "capture-processes.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
            result["clientExitCode"] = client.wait(timeout=480)
            if result["clientExitCode"] != 0:
                raise RuntimeError("The capture client exited with " + str(result["clientExitCode"]))
except BaseException as error:
    result["error"] = str(error)
    raise
finally:
    if client is not None and client.poll() is None:
        client.terminate()
        client.wait(timeout=20)
    if server is not None and server.poll() is None:
        server.stdin.write(b"stop\n")
        server.stdin.flush()
        try:
            server.wait(timeout=40)
        except subprocess.TimeoutExpired:
            server.terminate()
            server.wait(timeout=20)
    result["serverExitCode"] = None if server is None else server.returncode
    result["screenshots"] = [str(path) for path in Path(result["clientRunDirectory"]).glob("screenshots/*.png")]
    (root / "capture-result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result), flush=True)
