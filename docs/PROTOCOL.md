# OPO earbud control protocol

How OnePlus, OPPO and realme earbuds are controlled, as far as this app needs it.
Everything here was checked against real OnePlus Buds 3 traffic; the example
frames are verbatim captures.

## Transport

**Classic Bluetooth RFCOMM, not BLE.** Open an RFCOMM socket to the paired
earbuds using SDP service UUID:

| UUID | Notes |
|---|---|
| `0000079A-D102-11E1-9B23-00025B00A5A5` | The one that works on OnePlus Buds 3 / Buds 4. Try first. |
| `00001107-D102-11E1-9B23-00025B00A5A5` | Referenced by HeyMelody for older models; refused by Buds 3. |
| raw RFCOMM channel 15 | Last resort if SDP lookup fails. |

Only one client can hold the channel. If HeyMelody is running it usually owns
it and every connect attempt fails, so force-stop it.

### Traps we hit

- **BLE GATT looks plausible but does nothing on Buds 3.** Service
  `0000079A-…` also appears as a BLE GATT service on some models, and older
  write-ups describe a BLE variant. Buds 3 needs RFCOMM.
- **`00001107` is the UUID HeyMelody's code references** for its classic-Bluetooth
  path, but Buds 3 refuses it (`read failed, socket might closed or timeout`).
  The same error appears when another app holds the channel, so it's easy to
  blame the wrong cause.
- **A raw channel can connect to the wrong service.** Connecting to channel 1
  succeeds but the earbuds never answer on it. A connection with no replies at
  all means you're on the wrong channel.

## Frame format

All multi-byte fields are little-endian. There is no checksum.

```
offset  size  field
0       1     0xAA               start of frame
1       1     length             bytes after this field = 7 + payload length
2       2     00 00              flags (always zero for a single frame)
4       2     command
6       1     sequence           increments per request; replies echo it
7       2     payload length
9       n     payload
```

A reply carries the request's command with bit 15 set: `0x0404` → `0x8404`.
Unsolicited events use command `0x0204`, with the event type in the first
payload byte.

Example, set transparency:

```
AA  0A  00 00  04 04  04  03 00  01 01 04
SOF len flags  cmd    seq plen   payload
```

(command `0x0404`, sequence 4, 3-byte payload `01 01 04`).

## Session setup

The earbuds ignore commands until they see:

1. **Handshake** `0x0100`, empty payload → reply `0x8100`.
2. **Register events** `0x0205`, payload `03 01 02 03` (count, then event
   types: 01 battery, 02 wearing, 03 noise mode) → reply `0x8205`.

Skipping these gives well-formed commands that are silently ignored.

## Commands used by this app

### Set noise mode — `0x0404`

Payload `01 01 <mask>`. One bit selects the mode:

| Mode | Mask |
|---|---|
| Off | `01` |
| Transparency | `04` |
| Noise cancelling — deep | `10` |
| Noise cancelling — medium | `20` |
| Noise cancelling — light | `40` |
| Noise cancelling — smart | `80` |
| Noise cancelling — adaptive | `00 08` (bit 11, second mask byte) |

Which noise-cancelling levels exist depends on the model. The reply
`0x8404` has status `00` on success:

```
AA 08 00 00 04 84 04 01 00 00
```

### Query noise mode — `0x010C`

Payload `01 01`. Reply `0x810C`, payload `status 01 01 <mask16>`:

```
AA 0C 00 00 0C 81 03 05 00 00 01 01 08 00     → off
```

**The reported mask uses different bits from the set mask:**

| Reported mask | Mode |
|---|---|
| `0x0008` | Off |
| `0x0100` | Transparency |
| anything else | Noise cancelling (bit depends on level) |

### Query battery — `0x0106`

Empty payload. Reply `0x8106`, payload `status count (id level)…`. id 1 = left,
2 = right, 3 = case; level is a percentage with bit 7 set while charging.

```
AA 0F 00 00 06 81 F0 08 00 00 03 01 14 02 14 03 01    → L 20%, R 20%, case 1%
```

## Events — `0x0204`

After registering, the earbuds push changes, including ones made with the
stem gestures. The first payload byte is the event type.

| Type | Payload | Meaning |
|---|---|---|
| `01` | `01 count (id level)…` | Battery, same pairs as the query reply |
| `02` | `02 count (id state)…` | Wearing / in-case status |
| `03` | `03 01 01 <mask16>` | Noise mode, same bits as the query reply |

```
AA 0C 00 00 04 02 FF 05 00 03 01 01 00 01    → transparency
AA 0C 00 00 04 02 FF 05 00 03 01 01 10 00    → noise cancelling (deep)
```

Type `03` also carries other fields (e.g. `03 02 01 02 00`); only the
`03 01 01` form is the current mode.

## Sources

- [HeyMelody](https://play.google.com/store/apps/details?id=com.heytap.headset)
  (`com.heytap.headset`), decompiled: frame layout (`OPOv1Wrapper`, `Packet`),
  command numbers.
- [QuickBuds](https://github.com/spizganed/QuickBuds): the RFCOMM UUID order,
  the session setup and the set-mode masks, all verified on OnePlus hardware.
- [OppoPodsManager](https://github.com/Zhaoyi-ya/OppoPodsManager): origin of
  many command numbers and masks.
- [cracked-oneplus-buds](https://github.com/AasheeshLikePanner/cracked-oneplus-buds):
  the BLE variant for Nord Buds.
