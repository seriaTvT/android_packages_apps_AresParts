# AresParts

Device feature app for the Redmi K40 Gaming (ares) on LineageOS 23.2:
magnetic shoulder triggers (detection + per-game touch mapping), the RGB
light strips, and vibration strength.

Clean rewrite following the LineageOS device-parts conventions
(SettingsLib UI nested under Settings → System, package
`org.lineageos.settings.ares`, platform-signed system_ext priv-app in the
`system_app` SELinux domain).

## Hardware facts (ares, lineage-23.2 kernel)

- `/dev/gamekey`: char 10,110; reads must be exactly 4 bytes:
  `[hall_left, hall_right, key_left, key_right]`, each 0/1.
- Trigger evdev: `xm_gamekey`, KEY_F3/F4 = left slider open/close,
  KEY_F5/F6 = right slider open/close, KEY_F1/F2 = trigger presses.
- RGB LED: `/sys/class/leds/{red,green,blue}/` with `brightness`,
  `blink`, `trigger` (no `breath` on this kernel); nodes are labeled
  `sysfs_rgb_led` via genfs_contexts (mt6893-common).
- Vibrator: aw8697; strength node is
  `/sys/devices/platform/11d03000.i2c7/i2c-7/7-005a/vmax` (raw register,
  not millivolts). Unlabeled as of M0 — needs sepolicy work before use.

## Build

`m AresParts` — selected in `device/xiaomi/ares/device.mk` via the
`ARES_PARTS_APP` switch during the migration off the legacy XiaomiParts.

Development referenced [AYIKxD/Parts-ares](https://github.com/AYIKxD/Parts-ares).
