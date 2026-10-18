# Yandex Go 5.98.2 Ninebot BLE reverse engineering

## 1. Scope and artifacts

Target: Ninebot BLE path in Yandex Go 5.98.2 base APK.

Artifacts examined:

| Artifact | Value | Evidence |
|---|---|---|
| APK | references/apks/yandex-go-5.98.2-base.apk | file exists, size 192834008 bytes |
| SHA-256 | 27a75fdf62c9ab601bddd89c6559e75231f868ac3f08fa463ae7a85d8b5c1862 | Get-FileHash on the APK |
| versionName | 5.98.2 | apktool.yml:10-12 |
| versionCode | 50128633 | apktool.yml:9-12 |
| minSdk / targetSdk | 32 / 36 | apktool.yml:6-8 |
| Dex files | 19, classes.dex plus classes2.dex through classes19.dex | archive central directory |
| Native libs | 0 entries under lib/ | archive central directory |
| Split metadata | requiredSplitTypes base__abi,base__density | AndroidManifest.xml:2 |
| Split list | only language entries | res/xml/splits0.xml:1-28 |

Decompiled tree used for all references below: C:\Users\Dmode\AppData\Local\Temp\opencode\yandex_dec. References are relative to that directory.

## 2. Confirmed facts

### 2.1 Provider structure

| Fact | Evidence |
|---|---|
| ghq0 is an abstract singleton holder for the za provider | smali_classes17/ghq0.smali:1, :15, :35-81 |
| ghq0.c() lazily creates fhq0 when the field is null | smali_classes17/ghq0.smali:35-55 |
| ghq0.b() is abstract and returns za | smali_classes17/ghq0.smali:86 |
| ghq0.a(ghq0) is a public static setter | smali_classes17/ghq0.smali:27-32 |
| fhq0.b() returns null | smali_classes17/fhq0.smali:16-21 |
| i2r0 mirrors ghq0 for the ab provider | smali_classes17/i2r0.smali:1, :35-81, :86 |
| i2r0.c() creates h2r0 | smali_classes17/i2r0.smali:51-55 |
| h2r0.b() returns null | smali_classes17/h2r0.smali:16-21 |
| za declares a(), b(), c(), d(), e(), f([B), g([B) | smali_classes17/za.smali:6-24 |
| ab declares a() only | smali_classes17/ab.smali:6 |
| ghq0 MemberClasses names Lehq0 and fhq0 | smali_classes17/ghq0.smali:6-11 |
| i2r0 MemberClasses names h2r0 and g2r0 | smali_classes17/i2r0.smali:6-11 |

No class in the 19 dex implements za or ab, and no dex defines Lehq0 or Lg2r0. See section 3.

### 2.2 GATT connection

Connection entry: com/yandex/go/scooters/ble/data/ninebot/a.smali:32-218.

| Step | Call | Evidence |
|---|---|---|
| Resolve device | BluetoothAdapter.getRemoteDevice(address) | :99-103 |
| Service UUID | za.a() into callback arg 3 | :107-117, :185-187 |
| Write characteristic UUID | za.e() into callback arg 8 | :119-129, :185-187 |
| Notify characteristic UUID | za.b() into callback arg 4 | :131-141, :185-187 |
| CCCD descriptor UUID | ab.a() into callback arg 5 | :143-153, :185-187 |
| connectGatt | autoConnect false, callback, transport 2 | :175, :189-191 |

Callback: smali_classes15/com/yandex/go/scooters/ble/utils/ScootersBleConnectExtensionsKt$awaitConnect$2$bluetoothGatt$1.smali.

| Step | Evidence |
|---|---|
| On state 2 call discoverServices() | :167-181 |
| On services discovered require status 0 | :276-278 |
| getService(service UUID) | :278 |
| getCharacteristic(notify UUID) | :282 |
| getDescriptor(CCCD UUID) | :286 |
| setCharacteristicNotification(true) | :290-292 |
| descriptor value ENABLE_NOTIFICATION_VALUE | :294-296 |
| writeDescriptor | :298 |
| On descriptor write status 0 resolve write characteristic | :228-242 |
| Build connection object tlp0(gatt, writeChar, writeListener, notifyListener) | :238-242 |

tlp0 fields: a BluetoothGatt, b write characteristic, c write listener, d notify listener. smali_classes17/tlp0.smali:7-30.

### 2.3 Frame format

Write path applies za.f before setValue:

| Step | Evidence |
|---|---|
| dc p0.a() bytes | com/yandex/go/scooters/ble/data/ninebot/a.smali:237-239 |
| za.f(payload) | :261-271 |
| setValue | :273 |
| writeCharacteristic | :275-277 |

Notify path applies za.g:

| Step | Evidence |
|---|---|
| Collect raw notification bytes | ScootersBleNinebotRepository$sendCommandWaitingResponse$response$1.smali:143-167 |
| za.g(raw) | :169 |

General non-AUTH frame, 8 bytes:

    [0] za.d() >> 8
    [1] za.d() & 0xff
    [2] 0x01
    [3] 0x00
    [4] pcp0.a (session byte from AUTH)
    [5] opcode
    [6] step or subtype
    [7] 0x00

Evidence: smali_classes17/nep0.smali:36-98 (default branch), :286-350, :353-417, :420-484, :487-551, :554-618.

AUTH frame, 15 bytes:

    [0] za.d() >> 8
    [1] za.d() & 0xff
    [2] 0x08
    [3] 0x00
    [4] 0x00
    [5] 0x01
    [6..14] UTF-8 key bytes, zero padded or overflow

Evidence: smali_classes17/nap0.smali:30-38, :52-60, :74-84, :86-92, :94-113.

### 2.4 Command table

Enum order: AUTH=0, LOCK=1, UNLOCK=2, SOUND=3, CABLE_UNLOCK=4, UNLOCK_BATTERY_COVER=5. smali_classes17/com/yandex/go/scooters/ble/domain/model/ScootersBleCommandType.smali:64-120.

Switch mapping: AUTH=1, LOCK=2, UNLOCK=3, SOUND=4, CABLE_UNLOCK=5, UNLOCK_BATTERY_COVER=6. smali_classes17/ncp0.smali:23-101.

Dispatch: smali_classes17/com/yandex/go/scooters/ble/domain/ninebot/a.smali:112-124, table at :302-310.

| Command | Dispatch target | Builder index | Opcode | Length | Payload template |
|---|---|---|---|---|---|
| AUTH | c(String) | nap0 | none | 15 | d_hi,d_lo,08,00,00,01,K0..K8 |
| LOCK step 1 | d() | nep0 index 1 | 0x15 | 8 | d_hi,d_lo,01,00,c,15,01,00 |
| LOCK step 2 | d() | nep0 index 2 | 0x15 | 8 | d_hi,d_lo,01,00,c,15,02,00 |
| UNLOCK step 1 | g() | nep0 index 5 | 0x05 | 17 | d_hi,d_lo,0A,00,c,05,01,n0,n1,n2,n3,t3,t2,t1,t0,00,00 |
| UNLOCK step 2 | g() | nep0 index 6 | 0x05 | 8 | d_hi,d_lo,01,00,c,05,02,00 |
| SOUND | e() | nep0 index 3 | 0xA2 | 8 | d_hi,d_lo,01,00,c,A2,02,00 |
| CABLE_UNLOCK | f(boolean) | nep0 index 0 | 0x81 | 8 | d_hi,d_lo,01,00,c,81,01,00 |
| UNLOCK_BATTERY_COVER | executeCommand case 6 | nep0 index 4 | 0xC0 | 8 | d_hi,d_lo,01,00,c,C0,01,00 |

Builder evidence: smali_classes17/nep0.smali:622-630 maps index 0 to :554, 1 to :487, 2 to :420, 3 to :353, 4 to :286, 5 to :111.

Non-AUTH opcode and step evidence:

| Index | Evidence |
|---|---|
| 0 | :608-616 |
| 1 | :541-549 |
| 2 | :474-482 |
| 3 | :407-415 |
| 4 | :340-348 |
| 5 | :157-283 |

UNLOCK step 1 detail: byte 2 is 0x0A, za.c()[0..3] at bytes 7..10, timestamp at bytes 11..14, bytes 15..16 zero. Evidence: :111-113, :157-161, :177-245, :247-281.

Timestamp is System.currentTimeMillis()/1000 as a signed 32-bit big-endian value. Evidence: :101-109, :247-277.

### 2.5 Command flows

AUTH:

| Step | Evidence |
|---|---|
| ecp0 required and ecp0.a must be non-null | domain/ninebot/a.smali:255-287 |
| Build nap0 with the key string | :390-400 |
| data.c waits for notification | :400 |
| Response always parsed as oap0 | :410-432 |
| Store oap0.a into pcp0.a | :511-519 |
| Missing key throws ScootersBleConnectionFailureException | :282-287 |

LOCK:

| Step | Evidence |
|---|---|
| Requires pcp0.a non-null, otherwise returns success without sending | :619-623, :805-807 |
| Send index 1 with data.c | :625-639 |
| Parse response as beq0 | :651-690 |
| Send index 2 with data.b(..., true) | :752-770 |
| FAILURE throws ScootersLockFailedException | :783-796 |

UNLOCK:

| Step | Evidence |
|---|---|
| Requires pcp0.a non-null, otherwise returns success | :1401-1405, :1591-1593 |
| Send index 5 with data.c | :1407-1423 |
| Parse response as xir0 | :1435-1491 |
| Send index 6 with data.b(..., true) | :1538-1556 |
| FAILURE throws ScootersLockFailedException | :1569-1582 |

SOUND:

| Step | Evidence |
|---|---|
| Requires pcp0.a non-null | :908-912 |
| Send index 3 with data.c | :914-930 |
| Parse response as n9r0 | :942-1036 |
| FAILURE throws ScootersSoundFailedException | :1039-1054 |

CABLE_UNLOCK:

| Step | Evidence |
|---|---|
| Requires pcp0.a non-null | :1135-1139 |
| Send index 0 with data.c | :1141-1159 |
| Parse response as oep0 | :1171-1246 |
| FAILURE logs and throws unless ignore flag is true | :1268-1301 |

UNLOCK_BATTERY_COVER:

| Step | Evidence |
|---|---|
| Requires pcp0.a non-null | :139-143 |
| Send index 4 with data.b(..., true), no response parser | :145-155 |

Post-AUTH cable unlock on open lock: connect with ecp0(password, 2), then send CABLE_UNLOCK with ecp0(null, 1) which sets ignore-failure true. Evidence: ScootersOverBleInteractor$tryOpenLockByBle$2$1.smali:143-155, ScootersOverBleInteractor$tryOpenLockByBle$2$2.smali:135-141, ecp0.smali:26-54.

### 2.6 Responses

| Parser | Rule | Evidence |
|---|---|---|
| oap0 | a = byte[7] | smali_classes17/oap0.smali:11-20 |
| beq0 | byte[6] == 1 means SUCCESS | smali_classes17/beq0.smali:11-29 |
| xir0 | byte[6] == 1 means SUCCESS | smali_classes17/xir0.smali:11-29 |
| oep0 | byte[7] == 0 means SUCCESS | smali_classes17/oep0.smali:11-27 |
| n9r0 | byte[7] == 0 means SUCCESS | smali_classes17/n9r0.smali:11-27 |

Session holder pcp0.a is a volatile Byte. smali_classes17/pcp0.smali:7. disconnect clears it. domain/ninebot/a.smali:810-817.

Parser selection in each command compares the requested class against the response instance, but the requested class is fixed per command, so the command-specific parser above is always used.

### 2.7 Timeouts

| Wait | Value | Evidence |
|---|---|---|
| Write acknowledgment | 1000 ms | data/ninebot/a.smali:279-287 |
| Notification response | 5000 ms | data/ninebot/a.smali:419-431 |
| Notification timeout result | ScootersBleResponseTimeoutException | data/ninebot/a.smali:440-467 |

data.b(cmd, true) waits only for onCharacteristicWrite, not for a notification. data.c sends with wait false, then waits 5000 ms for a notification. Evidence: :310-418.

### 2.8 AUTH key origin and encoding

| Fact | Evidence |
|---|---|
| ecp0.a is the key string | smali_classes17/ecp0.smali:7 |
| ecp0(String, int) mask bit 1 nulls the string, bit 2 clears the default boolean | smali_classes17/ecp0.smali:26-54 |
| Key is encoded with izb.a, which is UTF-8 | smali_classes17/nap0.smali:30-32; smali_classes12/izb.smali:26-32 |
| Key is copied verbatim starting at byte 6 | smali_classes17/nap0.smali:94-113 |
| No hash, prefix, or truncation is applied | smali_classes17/nap0.smali:30-113 |
| Maximum key length is 9 bytes | array length 15 at :36-38, write index 6+i at :103-107; i=9 writes index 15 |
| Shorter keys leave zero padding | array initialized to zero, only key length bytes copied, :36-38, :94-113 |
| Key string is the server field password | data/model/ScootersBluetoothDto$$serializer.smali:94-102 |
| DTO fields a and b are mac and password | data/model/ScootersBluetoothDto.smali:43-45; serializer :94-102 |
| Password reaches the BLE layer as ScootersTryByBleException.b() | domain/b.smali:2125-2133; domain/model/ScootersTryByBleException.smali:35-37, :63-68 |
| Open lock passes address and password into u0.h | domain/b.smali:2144-2158; domain/ScootersActiveOrderInteractor$openLock$3$1.smali:138-158 |
| u0.h passes them into the connect lambda | domain/u0.smali:1251, :1401-1416 |
| Lambda stores them as address and deviceKey, builds ecp0(deviceKey, 2), calls connect | domain/ScootersOverBleInteractor$tryOpenLockByBle$2$1.smali:46-63, :143-155 |

Consequence: the AUTH key is per-session server data, not a constant in the APK. The APK contains only the transport and the JSON field name password.

### 2.9 Remote config and scan

| Fact | Evidence |
|---|---|
| Experiment key is scooters_client_finish_ble | smali_classes4/ajr.smali:43 |
| Model fields: b enabled, c l10n, d scan_window, e rssi_threshold, f gatt_service_uuid, g valid_name_prefixes, h ui | experiments/model/ScootersFinishBleExperiment.smali:61-73 |
| Defaults: enabled false, scan_window 1000, rssi_threshold -100, uuid empty, prefixes empty | :179-208 |
| Serialized names: enabled, l10n, scan_window, rssi_threshold, gatt_service_uuid, valid_name_prefixes, ui | experiments/model/ScootersFinishBleExperiment$$serializer.smali:96-122 |
| Scan UUID is parsed with UUID.fromString(f), invalid gives 0 results | domain/u0.smali:408-439 |
| Duration d must be greater than 0 | :442-460 |
| Scan filter uses the config service UUID | :533-548 |
| Scan mode is 2 | :550-562 |
| RSSI below e is rejected | domain/ScootersOverBleInteractor$scanNearbyScooters$2$callback$1.smali:229-235 |
| Non-empty advertised service list must contain the config UUID | :238-277 |
| Name must start with one of g | :303-354 |
| Stored result is the advertised device name | :356-377 |

The scan UUID comes from remote config, while the connection service UUID comes from za.a(). They are independent values.

### 2.10 WIND

| Fact | Evidence |
|---|---|
| Protocol enum: NINEBOT=0, WIND=1 | ble/api/ScootersBleProtocol.smali:41-68 |
| fcp0 selects a provider by switch value 1 or 2 | fcp0.smali:104-130 |
| NINEBOT branch builds the interactor from the first provider | fcp0.smali:137-148 |
| WIND branch builds it from the second provider | fcp0.smali:118-130 |
| The WIND provider is scp0 | gto0.smali:345-354 |
| scp0 implements every method as a no-op returning Lmx41 | scp0.smali:10-29 |
| u0 always requests NINEBOT | domain/u0.smali:83-88 |

No WIND frame format, UUID, or opcode exists in this APK.

### 2.11 Ignition

Ignition is an altbeacon Region scanner, not a Ninebot GATT command channel. The static Region uses the all-zero UUID and all-zero identifiers. Evidence: smali_classes11/com/yandex/go/scooters/ignition/domain/k.smali:43-60.

## 3. Absent from the base APK

| Missing item | Proof |
|---|---|
| za implementation | rg "implements Lza;" returns 0 files |
| ab implementation | rg "implements Lab;" returns 0 files |
| Lehq0 class | only the MemberClasses reference at ghq0.smali:9; no ehq0.smali anywhere |
| Lg2r0 class | only the MemberClasses reference at i2r0.smali:9; no g2r0.smali anywhere |
| Provider setter call | rg "Lghq0;->a(Lghq0;)" and "Li2r0;->a(Li2r0;)" return no call sites |
| Service UUID | only za.a() call sites; no literal and no implementation |
| Notify characteristic UUID | only za.b() call sites |
| Write characteristic UUID | only za.e() call sites |
| CCCD descriptor UUID | only ab.a() call sites |
| za.d 16-bit identifier | only call sites; no value |
| za.c 4-byte value | only call sites; no value |
| za.f byte transform | only call site in data/ninebot/a.smali:269 |
| za.g byte transform | only call site in response$1.smali:169 |
| Ninebot UUID strings outside smali | rg -i "ninebot" over assets, res, unknown returns no matches |
| UUID literals in the scooters package | rg UUID regex over smali_classes17/com/yandex/go/scooters returns no matches |
| Native provider | 0 lib/ entries in the APK |
| Embedded dex/jar/so in assets | no matching files under assets |
| Dynamic feature with the provider | AndroidManifest.xml:2 lists only base__abi and base__density; res/xml/splits0.xml:1-28 lists only languages |

Any call into the provider dereferences null:

    Lghq0;->c() -> Lfhq0; -> b() -> null
    then za.d(), za.a(), za.b(), za.e(), za.c(), za.f(), za.g() -> NullPointerException

This applies to connect, every payload builder, and the notify transform. The BLE path in this APK cannot complete as shipped.

## 4. Inferred, not confirmed

| Inference | Basis |
|---|---|
| Byte 2 of the AUTH frame is a length field | value 0x08 equals the meaningful body length, but no consumer is visible |
| Byte 5 of the AUTH frame is a command or version selector | constant 0x01, no branch on it |
| za.d is a scooter or protocol identifier | used as the first two bytes of every frame, semantic name absent |
| za.c is a per-unlock nonce | 4 bytes inserted only in the 17-byte unlock frame |
| The 17-byte unlock frame carries a timestamp for replay protection | timestamp is current Unix seconds, big-endian |
| za.f and za.g are encryption or obfuscation transforms | applied to every TX and RX frame, implementation absent |
| Command names map to physical actions | names are the only evidence; no vendor document in the APK |

## 5. Test vectors

All vectors below are derived from the parser and builder bytecode, not from captured device traffic. Missing values are written as symbols.

| Vector | Input | Expected |
|---|---|---|
| lock success | 00 00 00 00 00 00 01 00 | beq0.a = SUCCESS |
| lock failure | 00 00 00 00 00 00 00 00 | beq0.a = FAILURE |
| unlock success | 00 00 00 00 00 00 01 00 | xir0.a = SUCCESS |
| unlock failure | 00 00 00 00 00 00 02 00 | xir0.a = FAILURE |
| cable success | 00 00 00 00 00 00 00 00 | oep0.a = SUCCESS |
| cable failure | 00 00 00 00 00 00 00 01 | oep0.a = FAILURE |
| sound success | 00 00 00 00 00 00 00 00 | n9r0.a = SUCCESS |
| sound failure | 00 00 00 00 00 00 00 FF | n9r0.a = FAILURE |
| AUTH session byte | 00 00 00 00 00 00 00 2A | oap0.a = 0x2A, pcp0.a = 0x2A |
| AUTH key ABCDEFGHI | D0 D1 08 00 00 01 41 42 43 44 45 46 47 48 49 | length 15, key at bytes 6..14 |
| AUTH key ABCDEFGHIJ | any 10-byte key | ArrayIndexOutOfBoundsException at index 15 |
| AUTH empty key | D0 D1 08 00 00 01 00 00 00 00 00 00 00 00 00 | length 15, zero key field |
| UNLOCK step 1 shape | D0 D1 0A 00 C0 05 01 C0 C1 C2 C3 T3 T2 T1 T0 00 00 | length 17 |
| LOCK step 1 shape | D0 D1 01 00 C0 15 01 00 | length 8 |
| LOCK step 2 shape | D0 D1 01 00 C0 15 02 00 | length 8 |
| UNLOCK step 2 shape | D0 D1 01 00 C0 05 02 00 | length 8 |
| SOUND shape | D0 D1 01 00 C0 A2 02 00 | length 8 |
| CABLE shape | D0 D1 01 00 C0 81 01 00 | length 8 |
| BATTERY COVER shape | D0 D1 01 00 C0 C0 01 00 | length 8 |
| Provider null | ghq0.c().b() then any za method | NullPointerException |

Symbols: D0 D1 = za.d() big-endian, C0 = pcp0.a session byte, C0..C3 = za.c()[0..3], T3..T0 = Unix seconds big-endian.

## 6. Required artifacts

1. Primary: an APK or dex set for versionCode 50128633 that actually defines Lehq0 and Lg2r0, or otherwise implements za and ab. This base APK contains only null-returning stubs. Without this, service UUID, notify UUID, write UUID, CCCD UUID, za.d, za.c, za.f, and za.g cannot be recovered statically.

2. Secondary, if the dex cannot be obtained: a full HCI snoop log (btsnoop_hci.log) from the same app version performing connect, AUTH, open lock, and unlock. The log yields the service and characteristic UUIDs, the descriptor UUID, frame bytes, and the observable effect of za.f and za.g, but not the transform keys.

3. Per-session: the server control response for open lock or where is, containing bluetooth.mac and bluetooth.password. This is the only source of the AUTH key. It is runtime data and is not stored in the APK.

4. Optional: Ninebot vendor protocol documentation covering the 0x15, 0x05, 0xA2, 0x81, and 0xC0 opcodes, which would confirm the inferred semantics.

## 7. Sources

| Path | Lines | Content |
|---|---|---|
| apktool.yml | 6-12 | version and SDK |
| AndroidManifest.xml | 2, 1220-1223 | split requirements |
| res/xml/splits0.xml | 1-28 | language-only split list |
| smali_classes17/ghq0.smali | 1-87 | za provider holder |
| smali_classes17/fhq0.smali | 1-22 | null za implementation |
| smali_classes17/i2r0.smali | 1-87 | ab provider holder |
| smali_classes17/h2r0.smali | 1-22 | null ab implementation |
| smali_classes17/za.smali | 1-25 | za interface |
| smali_classes17/ab.smali | 1-7 | ab interface |
| smali_classes17/nap0.smali | 25-117 | AUTH builder |
| smali_classes17/nep0.smali | 29-631 | all other builders |
| smali_classes17/pcp0.smali | 1-17 | session byte holder |
| smali_classes17/ecp0.smali | 1-55 | command parameter |
| smali_classes17/oap0.smali | 11-23 | AUTH response |
| smali_classes17/beq0.smali | 11-35 | lock response |
| smali_classes17/xir0.smali | 11-35 | unlock response |
| smali_classes17/oep0.smali | 11-33 | cable response |
| smali_classes17/n9r0.smali | 11-33 | sound response |
| smali_classes17/ncp0.smali | 11-109 | command switch table |
| smali_classes17/com/yandex/go/scooters/ble/domain/model/ScootersBleCommandType.smali | 64-120 | enum order |
| smali_classes17/com/yandex/go/scooters/ble/domain/ninebot/a.smali | 30-1594 | dispatch, flows, parsers |
| smali_classes17/com/yandex/go/scooters/ble/data/ninebot/a.smali | 32-473 | connect, write, timeouts |
| smali_classes15/com/yandex/go/scooters/ble/utils/ScootersBleConnectExtensionsKt$awaitConnect$2$bluetoothGatt$1.smali | 123-306 | GATT callback |
| smali_classes17/com/yandex/go/scooters/ble/data/ninebot/ScootersBleNinebotRepository$sendCommandWaitingResponse$response$1.smali | 102-174 | RX transform |
| smali_classes17/tlp0.smali | 1-31 | connection object |
| smali_classes17/fcp0.smali | 27-156 | protocol selection |
| smali_classes17/gto0.smali | 345-354 | WIND provider construction |
| smali_classes17/scp0.smali | 1-30 | WIND no-op |
| smali_classes17/icp0.smali | 11-53 | protocol switch values |
| smali_classes17/com/yandex/go/scooters/ble/api/ScootersBleProtocol.smali | 41-68 | protocol enum |
| smali_classes17/com/yandex/go/scooters/domain/u0.smali | 80-104, 371-635, 1251-1469 | protocol use, scan, BLE entry |
| smali_classes15/com/yandex/go/scooters/domain/ScootersOverBleInteractor$scanNearbyScooters$2$callback$1.smali | 216-382 | scan filtering |
| smali_classes17/com/yandex/go/scooters/domain/ScootersOverBleInteractor$tryOpenLockByBle$2$1.smali | 46-168 | key to ecp0 and connect |
| smali_classes17/com/yandex/go/scooters/domain/ScootersOverBleInteractor$tryOpenLockByBle$2$2.smali | 98-154 | cable unlock after connect |
| smali_classes17/com/yandex/go/scooters/domain/b.smali | 2077-2158 | server control response to BLE exception |
| smali_classes17/com/yandex/go/scooters/domain/model/ScootersTryByBleException.smali | 1-69 | address and password |
| smali_classes17/com/yandex/go/scooters/data/model/ScootersBluetoothDto.smali | 43-45 | mac and password fields |
| smali_classes17/com/yandex/go/scooters/data/model/ScootersBluetoothDto$$serializer.smali | 94-102 | JSON names mac and password |
| smali_classes17/com/yandex/go/scooters/ble/a.smali | 26-149 | connect then AUTH |
| smali_classes4/ajr.smali | 28-50 | experiment key |
| smali_classes17/com/yandex/go/scooters/experiments/model/ScootersFinishBleExperiment.smali | 61-208 | experiment fields and defaults |
| smali_classes17/com/yandex/go/scooters/experiments/model/ScootersFinishBleExperiment$$serializer.smali | 90-122 | experiment JSON names |
| smali_classes12/izb.smali | 23-32 | UTF-8 constant |
| smali_classes11/com/yandex/go/scooters/ignition/domain/k.smali | 43-60 | beacon Region |

## 8. Verification commands

Run from the decompiled tree root:

    rg -n "implements Lza;|implements Lab;" .
    rg -n "Lghq0;->a\(Lghq0;\)|Li2r0;->a\(Li2r0;\)" .
    rg -n "Lehq0;|Lg2r0;" .
    rg -n "Lza;->(a|b|c|d|e|f|g)\(" .
    rg -n -i "ninebot" assets res unknown
    rg -n "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}" smali_classes17/com/yandex/go/scooters
    rg -n "ScootersBleProtocol;->(NINEBOT|WIND)" .

Archive checks:

    $z=[IO.Compression.ZipFile]::OpenRead("references/apks/yandex-go-5.98.2-base.apk")
    ($z.Entries | Where-Object {$_.FullName -like "lib/*"}).Count
    ($z.Entries | Where-Object {$_.FullName -like "classes*.dex"}).Count
    $z.Dispose()

Expected: 0 lib entries, 19 dex files.
