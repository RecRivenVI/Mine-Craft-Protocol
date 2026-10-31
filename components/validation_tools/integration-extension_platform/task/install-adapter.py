"""Install only declared inputs and built packages into this validation's stopped owned client."""

import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import tomllib

parser = argparse.ArgumentParser()
parser.add_argument("--target", required=True)
parser.add_argument("--installed", action="store_true")
parser.add_argument("--role", choices=("client", "server"), default="client")
parser.add_argument("--adapter", choices=("platform_probe", "computercraft", "modern_ui", "emi", "ae2", "create", "exposure", "littletiles"), required=True)
args = parser.parse_args()
repo = pathlib.Path(__file__).resolve().parents[4]
client = repo / "validations/integration-extension_platform/instance"
if args.installed:
    client = client / "installed"
client = client / args.target / args.role
owner = json.loads((client / "extension-platform-owner.json").read_text())
if owner != {"owner": "extension-platform", "target": args.target}:
    raise RuntimeError("Owned validation client required")
if (client / "minecraft-protocol/endpoint.json").exists():
    raise RuntimeError("Stop the client before installing")
declared_inputs = tomllib.loads((repo / "inputs.toml").read_text(encoding="utf-8"))
registered_targets = ("1.20.1-forge", "1.21.1-neoforge", "26.1.2-neoforge", "26.3-neoforge", "26.3-fabric")
if args.target not in registered_targets:
    raise RuntimeError("Registered Target required")
selection = {(target, adapter): prefix + "-" + target.replace(".", "-")
             for target in registered_targets
             for adapter, prefix in (("computercraft", "computercraft"), ("modern_ui", "modernui"), ("emi", "emi"), ("ae2", "ae2"), ("create", "create"), ("exposure", "exposure"), ("littletiles", "littletiles"))
             if prefix + "-" + target.replace(".", "-") in declared_inputs}

key = selection.get((args.target, args.adapter))
sources = {}
if args.adapter != "platform_probe":
    if key is None:
        raise RuntimeError("No pinned upstream input for this adapter/Target yet")
    spec = json.loads((repo / "components/extension_packages/packages" / args.adapter / "package.json").read_text())
    keys = [key] + [prefix + "-" + args.target.replace(".", "-") for prefix in spec.get("dependencyPrefixes", [])]
    local = tomllib.loads((repo / "local.toml").read_text(encoding="utf-8"))["inputs"]
    for selected in keys:
        source = pathlib.Path(local[selected])
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        if digest not in declared_inputs[selected]["sha256"]:
            raise RuntimeError("Declared input hash mismatch: " + selected)
        sources[selected] = (source, digest)


def recycle(path):
    if path.is_symlink() or not path.resolve().is_relative_to(client.resolve()):
        raise RuntimeError("Unsafe validation path")
    if path.exists():
        command = (
            "Add-Type -AssemblyName Microsoft.VisualBasic; "
            "[Microsoft.VisualBasic.FileIO.FileSystem]::"
            + ("DeleteDirectory" if path.is_dir() else "DeleteFile")
            + "($env:MCP_EXTENSION_RECYCLE,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,"
            "[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)"
        )
        import os

        subprocess.run(
            ["pwsh", "-NoProfile", "-Command", command],
            env={**os.environ, "MCP_EXTENSION_RECYCLE": str(path)},
            check=True,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )


mods = client / "mods"
mods.mkdir(exist_ok=True)
for known in set(selection.values()) | {key for key in declared_inputs if key.startswith(("guideme-", "creativecore-"))}:
    recycle(mods / f"extension-input-{known}.jar")
for selected, (source, digest) in sources.items():
    shutil.copyfile(source, mods / f"extension-input-{selected}.jar")
root = client / "minecraft-protocol/extensions"
approvals_file = root / "approvals.json"
approvals = json.loads(approvals_file.read_text(encoding="utf-8"))
for known in json.loads((repo / "components/extension_packages/packages/index.json").read_text()):
    recycle(root / "packages" / known)
    approvals["packages"].pop(known, None)
if sources:
    package = repo / "components/extension_packages/build/packages" / args.adapter
    manifest_bytes = (package / "extension.json").read_bytes()
    manifest = json.loads(manifest_bytes)
    if args.target not in manifest["variants"]:
        raise RuntimeError("Package does not declare the selected Target")
    shutil.copytree(package, root / "packages" / args.adapter)
    approvals["packages"][args.adapter] = {
        "contentIdentity": hashlib.sha256(manifest_bytes).hexdigest(),
        "scopes": manifest["requestedScopes"],
    }
approvals_file.write_text(json.dumps(approvals, indent=2) + "\n", encoding="utf-8")
print(f"Prepared {args.adapter}" + (f"; verified {len(sources)} declared upstream inputs" if sources else "; no third-party inputs"))
