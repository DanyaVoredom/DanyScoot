import json
import sys
from pathlib import Path


def crc8(data):
    crc = 0
    for value in data:
        crc ^= value
        for _ in range(8):
            crc = ((crc >> 1) ^ 0x8C) & 0xFF if crc & 1 else crc >> 1
    return crc


def parse(raw):
    if len(raw) < 7:
        raise ValueError("short")
    if raw[0] != 0xA3 or raw[1] != 0xA4:
        raise ValueError("header")
    length = raw[2]
    if len(raw) != length + 7:
        raise ValueError("length")
    if crc8(raw[:-1]) != raw[-1]:
        raise ValueError("crc")
    rand = (raw[3] - 0x32) & 0xFF
    key = raw[4] ^ rand
    cmd = raw[5] ^ rand
    payload = bytes((value ^ rand) & 0xFF for value in raw[6:-1])
    return cmd, key, rand, payload


def main():
    path = Path(__file__).with_name("frames.json")
    data = json.loads(path.read_text(encoding="utf-8"))
    checked = 0
    for group in ("tx", "rx"):
        for item in data[group]:
            raw = bytes.fromhex(item["frame"])
            cmd, key, rand, payload = parse(raw)
            assert cmd == int(item["cmd"], 16), item["id"]
            assert key == item["key"], item["id"]
            assert rand == item["rand"], item["id"]
            assert payload.hex().upper() == item["payload"], item["id"]
            checked += 1
    for item in data["malformed"]:
        try:
            parse(bytes.fromhex(item["frame"]))
        except ValueError:
            checked += 1
        else:
            raise AssertionError(item["id"])
    print(f"ok {checked}")


if __name__ == "__main__":
    main()
    sys.exit(0)
