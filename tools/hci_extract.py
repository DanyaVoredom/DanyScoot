import argparse
import struct
import sys

HEADER = b"\xA3\xA4"
RAND_OFFSET = 0x32


def crc8(data):
    crc = 0
    for value in data:
        crc ^= value
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0x8C
            else:
                crc >>= 1
    return crc & 0xFF


def decode_frame(frame):
    if len(frame) < 7 or frame[0:2] != HEADER:
        return None
    length = frame[2]
    total = length + 7
    if len(frame) < total:
        return None
    frame = frame[:total]
    if crc8(frame[:-1]) != frame[-1]:
        return None
    rand = (frame[3] - RAND_OFFSET) & 0xFF
    key = frame[4] ^ rand
    cmd = frame[5] ^ rand
    payload = bytes((value ^ rand) & 0xFF for value in frame[6:6 + length])
    return cmd, key, payload, frame


def att_values(packet, datalink):
    if datalink == 1002:
        if not packet or packet[0] != 0x02 or len(packet) < 9:
            return None
        l2cap = packet[5:]
    elif datalink == 1001:
        l2cap = packet
    else:
        return None
    if len(l2cap) < 5:
        return None
    length = struct.unpack("<H", l2cap[0:2])[0]
    cid = struct.unpack("<H", l2cap[2:4])[0]
    if cid != 0x0004:
        return None
    att = l2cap[4:4 + length]
    if len(att) < 3:
        return None
    opcode = att[0]
    if opcode in (0x12, 0x52):
        return "write", att[1:3], att[3:]
    if opcode in (0x1B, 0x1D):
        return "notify", att[1:3], att[3:]
    return None


def main():
    parser = argparse.ArgumentParser(description="Извлечение ключей Urent из btsnoop HCI лога")
    parser.add_argument("path", help="btsnoop_hci.log или файл из bugreport")
    parser.add_argument("--all", action="store_true", help="Печатать все кадры A3 A4")
    args = parser.parse_args()
    data = open(args.path, "rb").read()
    if data[:8] != b"btsnoop\0":
        raise SystemExit("Не btsnoop файл")
    version, datalink = struct.unpack(">II", data[8:16])
    print(f"btsnoop version={version} datalink={datalink}")
    offset = 16
    static_key = None
    session_key = None
    frames = 0
    while offset + 24 <= len(data):
        original, included, flags, drops, timestamp = struct.unpack(">IIIIQ", data[offset:offset + 24])
        offset += 24
        packet = data[offset:offset + included]
        offset += included
        direction = "RX" if flags & 1 else "TX"
        parsed = att_values(packet, datalink)
        if parsed is None:
            continue
        kind, handle, value = parsed
        if HEADER not in value:
            continue
        start = value.find(HEADER)
        decoded = decode_frame(value[start:])
        if decoded is None:
            continue
        cmd, key, payload, frame = decoded
        frames += 1
        text = payload.decode("ascii", "replace") if all(0x20 <= b < 0x7F for b in payload) else ""
        if args.all or cmd == 0x01:
            print(
                f"{direction} {kind} handle=0x{int.from_bytes(handle, 'little'):04X} "
                f"cmd=0x{cmd:02X} key=0x{key:02X} payload={payload.hex(' ').upper()} {text}"
            )
        if cmd == 0x01 and len(payload) >= 8 and all(0x20 <= b < 0x7F for b in payload):
            static_key = payload.decode("ascii")
        if cmd == 0x01 and len(payload) >= 2 and payload[0] == 1:
            session_key = payload[1]
    print(f"Кадров A3 A4: {frames}")
    if static_key:
        print(f"Статический ключ: {static_key}")
    if session_key is not None:
        print(f"Session key: 0x{session_key:02X}")
    if not static_key and session_key is None:
        print("Ключи не найдены. Нужен лог с полным циклом подключения и авторизации.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
