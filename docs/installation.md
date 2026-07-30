# Installing W4ME Station

W4ME Station ships as two matching JAD/JAR pairs:

| Pair | Use it when |
| --- | --- |
| `w4me-station.jad` / `w4me-station.jar` | The phone implements JSR-75 FileConnection and should browse local `.wasm` files |
| `w4me-station-base.jad` / `w4me-station-base.jar` | The full build is rejected, or the phone does not implement JSR-75 |

Both variants contain the same runtime and bundled cartridges. The base variant
removes the optional local-file browser, its JSR-75 classes, and its file-read
permission declaration.

## Transfer and install

1. Keep the selected `.jad` and `.jar` together without renaming either file.
2. Transfer both files to the phone over USB, Bluetooth, a memory card, or the
   handset's normal application-installation tool.
3. Open the `.jad` from the phone's application manager or file manager.
4. If JAD installation is not supported, transfer and open the matching `.jar`
   directly.
5. Accept optional network and file-read permissions only if external cartridge
   installation is needed. Bundled cartridges run without network access.

The JAD records the exact JAR file name and size. Do not combine a JAD from one
release with a JAR from another.

## Choosing a variant

Try the full build first. Use the base build when installation fails with a
missing `javax.microedition.io.file` or FileConnection error, or when the phone
does not expose local-file browsing.

An installed cartridge is copied into RMS. It can be relaunched without the
original file or network connection.

## Troubleshooting

- **Invalid application or JAR size mismatch:** transfer the matching JAD and
  JAR again, without modifying either file.
- **Missing FileConnection class:** install the base variant.
- **No sound:** open `Sound settings`, keep `Sound` enabled, and try
  `Compatible` audio mode.
- **Music stutters while sound effects work:** this is a confirmed limitation on
  the tested Nokia E71. `Compatible` mode may change which MMAPI path is used,
  but the current release does not guarantee gapless music on physical phones.
- **Nyan Cat clicks in Automatic mode:** this has been reported on J2ME Loader.
  Switch to `Compatible` mode; exact MMAPI behavior remains implementation-
  dependent.
- **Very slow cartridge:** return to the library and try Sokoban, Wasm Wars,
  Duck Maze, or another turn-based cartridge. Performance depends on the
  handset VM.
- **Installation permission prompt:** network and file access are optional;
  denying them only disables the corresponding external-loading path.

Release checksums are stored in `SHA256SUMS`. From an extracted release
directory, verify them with:

```sh
sha256sum -c SHA256SUMS
```
