import csv
import hashlib
import json
import sys
from pathlib import Path

import verify_frames

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent


def read_csv(name, required):
    path = ROOT / name
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = [column for column in required if column not in (reader.fieldnames or [])]
        if missing:
            raise AssertionError(f"{name}: missing columns {missing}")
        rows = list(reader)
    if not rows:
        raise AssertionError(f"{name}: empty")
    return rows


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def main():
    manifests = json.loads((ROOT / "manifests.json").read_text(encoding="utf-8"))
    if manifests.get("schema") != "danyurent.manifests/v1":
        raise AssertionError("manifests.json: schema")
    apps = manifests["apps"]
    app_ids = [item["id"] for item in apps]
    if len(app_ids) != len(set(app_ids)):
        raise AssertionError("manifests.json: duplicate id")
    for item in apps:
        counts = item["component_counts"]
        total = sum(counts[key] for key in ("activity", "activity_alias", "service", "receiver", "provider"))
        if counts["total"] != total:
            raise AssertionError(f"manifests.json: {item['id']} total")
        if item["permission_count"] < 1:
            raise AssertionError(f"manifests.json: {item['id']} permissions")

    inventory = read_csv(
        "apks.csv",
        [
            "app",
            "file",
            "tracked",
            "package",
            "version_name",
            "version_code",
            "min_sdk",
            "target_sdk",
            "compile_sdk",
            "size_bytes",
            "sha256",
            "signature",
            "signature_sha256",
            "source",
            "notes",
        ],
    )
    inventory_ids = [row["app"] for row in inventory]
    if len(inventory_ids) != len(set(inventory_ids)):
        raise AssertionError("apks.csv: duplicate app")
    for row in inventory:
        if row["tracked"] != "yes":
            continue
        path = REPO / row["source"]
        if not path.is_file():
            raise AssertionError(f"apks.csv: missing {row['source']}")
        if path.stat().st_size != int(row["size_bytes"]):
            raise AssertionError(f"apks.csv: size {row['app']}")
        if sha256(path) != row["sha256"].upper():
            raise AssertionError(f"apks.csv: sha256 {row['app']}")

    permissions = read_csv(
        "permissions.csv",
        ["app", "permission", "max_sdk", "flags", "declared_count", "category", "risk", "source"],
    )
    for row in permissions:
        if row["app"] not in app_ids:
            raise AssertionError(f"permissions.csv: unknown app {row['app']}")
        if int(row["declared_count"]) < 1:
            raise AssertionError(f"permissions.csv: count {row['permission']}")
        if row["category"] not in {
            "bluetooth",
            "location",
            "network",
            "camera",
            "audio",
            "foreground",
            "notification",
            "notifications",
            "storage",
            "overlay",
            "install",
            "packages",
            "contacts",
            "phone",
            "sms",
            "nfc",
            "system",
            "device",
            "internal",
            "vpn",
            "other",
        }:
            raise AssertionError(f"permissions.csv: category {row['permission']}")
        if row["risk"] not in {"low", "medium", "high"}:
            raise AssertionError(f"permissions.csv: risk {row['permission']}")

    components = read_csv(
        "components.csv",
        [
            "app",
            "type",
            "name",
            "exported",
            "permission",
            "foreground_service_type",
            "process",
            "launcher",
            "actions",
            "categories",
            "data",
            "source",
        ],
    )
    for row in components:
        if row["app"] not in app_ids:
            raise AssertionError(f"components.csv: unknown app {row['app']}")
        if row["type"] not in {"activity", "activity-alias", "service", "receiver", "provider"}:
            raise AssertionError(f"components.csv: type {row['type']}")
        if not row["name"]:
            raise AssertionError(f"components.csv: empty name {row['app']}")
        if row["launcher"] not in {"", "yes"}:
            raise AssertionError(f"components.csv: launcher {row['name']}")
        if row["launcher"] == "yes":
            if "android.intent.action.MAIN" not in row["actions"] or "android.intent.category.LAUNCHER" not in row["categories"]:
                raise AssertionError(f"components.csv: launcher filter {row['name']}")
        if row["data"]:
            data = json.loads(row["data"])
            if not isinstance(data, list):
                raise AssertionError(f"components.csv: data {row['name']}")

    opcodes = read_csv(
        "opcodes.csv",
        [
            "code_hex",
            "code_dec",
            "name",
            "direction",
            "tx_payload",
            "tx_source",
            "rx_decode",
            "rx_source",
            "danyurent",
            "confidence",
        ],
    )
    seen = set()
    for row in opcodes:
        code = int(row["code_hex"], 16)
        if code != int(row["code_dec"]):
            raise AssertionError(f"opcodes.csv: code {row['code_hex']}")
        if code in seen:
            raise AssertionError(f"opcodes.csv: duplicate {row['code_hex']}")
        seen.add(code)
        if row["direction"] not in {"BIDIR", "SET", "NOTIF"}:
            raise AssertionError(f"opcodes.csv: direction {row['code_hex']}")
        if row["danyurent"] not in {"yes", "decoder", "manual"}:
            raise AssertionError(f"opcodes.csv: danyurent {row['code_hex']}")
        if row["confidence"] not in {"confirmed", "inferred", "catalog"}:
            raise AssertionError(f"opcodes.csv: confidence {row['code_hex']}")

    opcodes_json = json.loads((ROOT / "opcodes.json").read_text(encoding="utf-8"))
    if opcodes_json.get("schema") != "danyurent.opcodes/v1":
        raise AssertionError("opcodes.json: schema")
    if opcodes_json.get("count") != len(opcodes) or opcodes_json.get("count") != len(opcodes_json["opcodes"]):
        raise AssertionError("opcodes.json: count")
    opcode_by_code = {int(row["code_dec"]): row for row in opcodes}
    for item in opcodes_json["opcodes"]:
        row = opcode_by_code.get(int(item["code"]))
        if row is None:
            raise AssertionError(f"opcodes.json: unknown code {item['code']}")
        if item["code_hex"] != row["code_hex"] or item["name"] != row["name"]:
            raise AssertionError(f"opcodes.json: mismatch {item['code_hex']}")

    builders = read_csv(
        "builders.csv",
        ["code_hex", "code_dec", "name", "variant", "payload_template", "builder_source", "vector", "status", "notes"],
    )
    builder_keys = set()
    for row in builders:
        code = int(row["code_hex"], 16)
        if code != int(row["code_dec"]) or code not in seen:
            raise AssertionError(f"builders.csv: code {row['code_hex']}")
        key = (code, row["variant"])
        if key in builder_keys:
            raise AssertionError(f"builders.csv: duplicate {row['code_hex']} {row['variant']}")
        builder_keys.add(key)
        if row["status"] not in {"confirmed", "inferred", "catalog", "notification"}:
            raise AssertionError(f"builders.csv: status {row['code_hex']}")

    rx_fields = read_csv(
        "rx_fields.csv",
        ["cmd", "condition", "offset", "size", "encoding", "formula", "field", "values", "source", "confidence"],
    )
    for row in rx_fields:
        if int(row["cmd"], 16) not in seen:
            raise AssertionError(f"rx_fields.csv: cmd {row['cmd']}")
        if row["offset"] and row["offset"].isdigit():
            int(row["offset"])
        if row["size"] and row["size"].isdigit():
            int(row["size"])
        if row["confidence"] not in {"confirmed", "inferred", "catalog"}:
            raise AssertionError(f"rx_fields.csv: confidence {row['cmd']}")

    uuids = read_csv("uuids.csv", ["app", "role", "uuid", "short", "source", "confidence"])
    for row in uuids:
        if row["app"] not in app_ids:
            raise AssertionError(f"uuids.csv: unknown app {row['app']}")
        if row["confidence"] not in {"confirmed", "unresolved"}:
            raise AssertionError(f"uuids.csv: confidence {row['app']}")

    ble_map = read_csv(
        "ble_map.csv",
        [
            "app",
            "transport",
            "service_uuid",
            "write_uuid",
            "notify_uuid",
            "read_uuid",
            "auth",
            "encryption",
            "source",
            "confidence",
        ],
    )
    for row in ble_map:
        if row["app"] not in app_ids:
            raise AssertionError(f"ble_map.csv: unknown app {row['app']}")

    ble_commands = read_csv(
        "ble_commands.csv",
        ["app", "command", "builder", "frame", "notes", "source", "confidence"],
    )
    for row in ble_commands:
        if row["app"] not in app_ids:
            raise AssertionError(f"ble_commands.csv: unknown app {row['app']}")

    frames = json.loads((ROOT / "frames.json").read_text(encoding="utf-8"))
    for key in ("crc8", "crc16", "tx", "rx", "malformed"):
        if key not in frames:
            raise AssertionError(f"frames.json: missing {key}")
    verify_frames.main()

    print(f"apps {len(apps)}")
    print(f"apk {len(inventory)}")
    print(f"permissions {len(permissions)}")
    print(f"components {len(components)}")
    print(f"opcodes {len(opcodes)}")
    print(f"uuids {len(uuids)}")
    print(f"ble_map {len(ble_map)}")
    print(f"ble_commands {len(ble_commands)}")
    print("ok")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, KeyError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        sys.exit(1)
