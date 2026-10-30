# Quake for Android

An Android port of Quake 1 built from [QuakeSpasm](https://github.com/sezero/quakespasm),
SDL2 and gl4es, with on-screen touch controls.

## Install

1. Download the latest APK from the **Releases** page and install it.
2. Copy your own copy of the game data into your phone's internal storage:
   `Quake/id1/pak0.pak` (and `pak1.pak` for the full game). The game data is **not**
   included and is not free.
3. Open the app. The first time, allow **All files access** so it can read the game files, then open it again.

## Music (optional)

Put music files in `Quake/id1/music/` named `track02.ogg` to `track11.ogg`
(`.ogg` and `.wav` are supported).

## Controls

- **Left side:** drag to move. Push further forward to run.
- **Right side:** drag to look around.
- **FIRE / JUMP:** hold and slide the same finger to aim while you shoot or jump.
- **WPN:** next weapon. **MENU:** the Quake menu (Esc).
- **SET:** look speed, run threshold, control size, invert look, and **Move controls**
  to drag every button where you want it. Everything is saved.

## Building

Push to `main` for a test APK (Actions tab), or push a tag such as `v1.0` to publish a release:

    git tag v1.0
    git push origin v1.0

The engine sources are git submodules in `third_party/` (SDL2, gl4es, QuakeSpasm, libogg, libvorbis).

## Licenses

QuakeSpasm and the glue code in this repository are GPL-2.0-or-later, so the source must stay public.
SDL2 is zlib-licensed, gl4es is MIT, libogg and libvorbis are BSD-3-Clause.
Quake is a trademark of id Software / ZeniMax. This project is not affiliated with or endorsed by them.
