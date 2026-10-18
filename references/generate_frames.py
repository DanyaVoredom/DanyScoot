import json
from pathlib import Path


def crc8(data):
    crc = 0
    for value in data:
        crc ^= value
        for _ in range(8):
            crc = ((crc >> 1) ^ 0x8C) & 0xFF if crc & 1 else crc >> 1
    return crc


def crc16(data):
    crc = 0xFFFF
    for value in data:
        crc ^= value
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return crc & 0xFFFF


def frame(cmd, payload, key=0, rand=1):
    out = bytearray(
        [
            0xA3,
            0xA4,
            len(payload),
            (rand + 0x32) & 0xFF,
            (key ^ rand) & 0xFF,
            (cmd ^ rand) & 0xFF,
        ]
    )
    out += bytes((value ^ rand) & 0xFF for value in payload)
    out.append(crc8(out))
    return out


def vector(identifier, cmd, payload, key=0, rand=1, note=""):
    raw = frame(cmd, payload, key, rand)
    return {
        "id": identifier,
        "cmd": f"{cmd:02X}",
        "key": key,
        "rand": rand,
        "wire_rand": (rand + 0x32) & 0xFF,
        "payload": payload.hex().upper(),
        "frame": raw.hex().upper(),
        "note": note,
    }


tx = [
    vector("handshake_ninehack", 0x01, b"4BKNwi77", 0, 1, "ключ NineHack, key=0"),
    vector("handshake_lostbot", 0x01, b"w4S3Hqn5", 0, 1, "ключ LostBot, key=0"),
    vector("unlock_ninehack", 0x05, bytes([1] * 10), 0, 1, "NineHack unlock"),
    vector("unlock_lostbot", 0x05, bytes([1] * 10), 0x42, 0x10, "LostBot unlock, sessionKey 0x42"),
    vector("lock", 0x15, bytes([1]), 0x42, 0x10, "lock"),
    vector("spam", 0x05, bytes([1, 0x31, 0x31, 0x32, 0x39, 0x65, 0x53, 0xF1, 0, 0]), 0x42, 0x10, "t=1700000000"),
    vector("device_info", 0x31, bytes([1]), 0x42, 0x10, "запрос device info"),
    vector("scooter_info", 0x60, bytes([1]), 0x42, 0x10, "запрос телеметрии"),
    vector("realtime", 0x63, bytes([1]), 0x42, 0x10, "запрос realtime"),
    vector("set_on", 0x61, bytes([2, 2, 2, 0]), 0x42, 0x10, "свет on, режим med, газ on"),
    vector("set_off", 0x61, bytes([1, 1, 1, 0]), 0x42, 0x10, "свет off, режим low, газ off"),
    vector("find1", 0xA2, bytes([1]), 0x42, 0x10, "find car"),
    vector("find2", 0xA2, bytes([2]), 0x42, 0x10, "find car"),
    vector("find3", 0xA2, bytes([3]), 0x42, 0x10, "find car"),
    vector("find6", 0xA2, bytes([6]), 0x42, 0x10, "find car"),
    vector("eject", 0x81, bytes([5]), 0x42, 0x10, "eject battery"),
    vector("battery_cover", 0xC0, bytes([1]), 0x42, 0x10, "battery cover"),
    vector("iot_restart", 0x92, bytes([1]), 0x42, 0x10, "IoT restart"),
    vector("clear_bt", 0x54, bytes([1]), 0x42, 0x10, "clear paired users"),
    vector("power_off", 0x91, bytes([1]), 0x42, 0x10, "power off"),
    vector("rfid_read", 0x85, bytes([1]), 0x42, 0x10, "read RFID"),
    vector("rfid_delete", 0x86, bytes.fromhex("123456789ABCDEF0"), 0x42, 0x10, "delete two RFID"),
    vector("color", 0x82, bytes([1, 0, 0x7C, 0x6A, 0xE8, 0, 0, 0]), 0x42, 0x10, "mode 1, RGB 7C6AE8"),
    vector("wheel", 0x64, bytes([0, 250]), 0x42, 0x10, "wheel 250 мм"),
    vector("calibrate", 0x72, bytes.fromhex("6553F10003"), 0x42, 0x10, "epoch 1700000000, tz +3"),
    vector("meter_reset", 0x73, bytes([1, 1]), 0x42, 0x10, "meter reset"),
    vector("accessory_saddle", 0xC3, bytes([1, 1]), 0x42, 0x10, "saddle open"),
    vector("accessory_tail", 0xC3, bytes([3, 2]), 0x42, 0x10, "tail-box close"),
    vector("accessory_helmet", 0xC3, bytes([8, 1]), 0x42, 0x10, "helmet open"),
    vector("switch_sim1", 0xC5, bytes([1]), 0x42, 0x10, "SIM slot 1"),
    vector("switch_sim2", 0xC5, bytes([2]), 0x42, 0x10, "SIM slot 2"),
    vector("next_sim", 0xC6, bytes([1]), 0x42, 0x10, "next SIM"),
    vector("cert", 0xC4, bytes([1]), 0x42, 0x10, "update certificate"),
    vector("gsm_off", 0x50, bytes([0]), 0x42, 0x10, "GSM disable"),
    vector("gsm_on", 0x50, bytes([1]), 0x42, 0x10, "GSM enable"),
    vector("gsm_detect", 0x83, bytes([1]), 0x42, 0x10, "GSM auto-detect"),
    vector("audio_on", 0x75, bytes([1]), 0x42, 0x10, "audio on"),
    vector("audio_off", 0x75, bytes([0xFF, 1]), 0x42, 0x10, "audio off"),
    vector("start_test", 0x76, bytes([1]), 0x42, 0x10, "enter test mode"),
    vector("clear_mileage", 0x77, bytes([1]), 0x42, 0x10, "clear mileage"),
    vector("screen_off", 0x79, bytes([1]), 0x42, 0x10, "auto screen off"),
    vector("qr_tag0", 0xA1, bytes([0]) + b"ABC", 0x42, 0x10, "QR tag 0 + ABC"),
    vector("vehicle_raw", 0xA0, b"HELLO", 0x42, 0x10, "vehicle raw command"),
    vector("avas_on", 0xDC, b"*HBCS,NB,123456789012345,V2,1,,,,,#\x00", 0x42, 0x10, "AVAS on"),
    vector("avas_off", 0xDC, b"*HBCS,NB,123456789012345,V2,2,,,,,#\x00", 0x42, 0x10, "AVAS off"),
    vector("fw_info", 0xFA, b"", 0x42, 0x10, "firmware info"),
    vector("fw_start", 0xFB, bytes([0, 0, 1, 0x4B, 0x37, 7]) + b"w4S3Hqn5", 0x42, 0x10, "START OTA, 1 pack, CRC 4B37"),
    vector("fw_read", 0xFC, bytes([0, 5, 7]), 0x42, 0x10, "read config pack 5, devType 7"),
]

rx = [
    vector("handshake_ack_0", 0x01, bytes([1, 0]), 0, 1, "sessionKey 0"),
    vector("handshake_ack_42", 0x01, bytes([1, 0x42]), 0, 1, "sessionKey 0x42"),
    vector("error_lock", 0x10, bytes([0x15, 0x02]), 0, 1, "lock rejected, reason 2"),
    vector("unlock_ok_1", 0x05, bytes([1]), 0, 1, "unlock status 1"),
    vector("unlock_ok_2", 0x05, bytes([2]), 0, 1, "unlock status 2"),
    vector("lock_ok_0", 0x15, bytes([0]), 0, 1, "lock status 0"),
    vector("device_info_bin", 0x31, bytes.fromhex("10680100010402"), 0, 1, "4200, flags 01, fw 1.4.2"),
    vector("device_info_text", 0x31, b"IMEI:860123456789012", 0, 1, "IoT network string"),
    vector("scooter_info", 0x60, bytes.fromhex("450200BB007C05FA"), 0, 1, "69%, mode 2, 18.7 km/h, trip 1.24 km, range 15.30 km"),
    vector("realtime", 0x63, bytes.fromhex("4500BB007B03"), 0, 1, "69%, 18.7 km/h, 1.23 A, flags 3"),
    vector("rfid", 0x85, bytes.fromhex("12345678"), 0, 1, "RFID 12345678"),
    vector("rfid_pair", 0x86, bytes.fromhex("123456789ABCDEF0"), 0, 1, "two RFID"),
    vector("color", 0x82, bytes.fromhex("01007C6AE8"), 0, 1, "mode 1, RGB 7C6AE8"),
    vector("accessory", 0xC3, bytes([1, 1]), 0, 1, "saddle open"),
    vector("fw_ready", 0xFB, bytes([1]), 0, 1, "READY"),
    vector("fw_complete", 0xFB, bytes([2]), 0, 1, "COMPLETE"),
    vector("fw_timeout", 0xFB, bytes([3]), 0, 1, "timeout"),
    vector("fw_crc_error", 0xFB, bytes([4]), 0, 1, "total CRC error"),
    vector("fw_pack_request", 0xFC, bytes([0, 5]), 0, 1, "request pack 5"),
]

pack = bytes([0, 5]) + bytes(range(16))
rx.append(vector("fw_pack_data", 0xFC, pack + crc16(pack).to_bytes(2, "big"), 0, 1, "pack 5 + CRC16"))

malformed = [
    {"id": "bad_crc", "frame": "A3A40133011400CF", "note": "CRC mismatch: expected CE"},
    {"id": "bad_len", "frame": "A3A40233011400CE", "note": "length mismatch: expected 9 got 8"},
    {"id": "bad_header", "frame": "A2A40133011400CE", "note": "invalid header"},
    {"id": "too_short", "frame": "A3A4013301", "note": "shorter than 7"},
]

data = {
    "crc8": {"poly_reflected": "8C", "init": "00", "check": "A1", "empty": "00"},
    "crc16": {"poly_reflected": "A001", "init": "FFFF", "check": "4B37"},
    "tx": tx,
    "rx": rx,
    "malformed": malformed,
}

Path(__file__).with_name("frames.json").write_text(
    json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
