# Third-party notices

W4ME Station contains unmodified WASM-4 cartridges used as bundled examples or
test fixtures, and the WASM-4 console font. The W4ME Station source-code license
does not cover these files.

## Console font

`src/main/resources/w4font.bin` is the built-in WASM-4 font, 224 glyphs of 8x8
pixels for character codes 32 through 255. It is a byte-for-byte copy of the
`font` array in `runtimes/native/src/framebuffer.c` of
<https://github.com/aduros/wasm4>, verified by hash, and ships in both release
JAR variants.

- 1792 bytes, SHA-256
  `2ec51a575549b0d08aaeaf9235f59a71bee21b2975d4bae2497ad38cc97bc39d`
- Copyright (c) Bruno Garcia
- ISC License:

```text
Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
```

The application icon `src/main/resources/icon.png` is original work by the W4ME
Station authors and is covered by the project's MIT license.

## Cartridge terms

WASM-4 states that cartridges published on wasm4.org are provided under the
[Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International
license](https://creativecommons.org/licenses/by-nc-sa/4.0/), unless the
copyright holder releases a cartridge elsewhere under different terms.

## Release cartridges

These thirteen files are included in both release JAR variants, packaged under
`cartridges/` inside the JAR. The order matches the library screen.

| File                                | Work and author                                         | Upstream page                               | Upstream source                                                          | SHA-256                                                            |
| ----------------------------------- | ------------------------------------------------------- | ------------------------------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------ |
| `cartridges/sokoban.wasm`           | Sokoban: Lars Hamre                                     | <https://wasm4.org/play/sokoban/>           | <https://github.com/chemecse/w4-sokoban> (MIT)                           | `090c346dda166c5a92d9c6bd9fbd360f6fb2f4bfb088cd669bc2640b2a041948` |
| `cartridges/wasm-wars.wasm`         | Wasm Wars: Daniel Kiyoshi Hashimoto Vouzella de Andrade | <https://wasm4.org/play/wasm-wars/>         | <https://github.com/Kiyoshi364/wasm-wars> (MIT)                          | `aa88450ef73c4b900673c2368e9f06597ae000fdfb806030b86c9fb17263cab6` |
| `cartridges/annoyingrobots.wasm`    | Annoying Robots: Shimon Ulewicz                         | <https://wasm4.org/play/annoyingrobots/>    | not published                                                            | `5ddf10cb816a64527f9b15f1bd29d3ee04343d8a508f4e81a9afce67a1a3017c` |
| `cartridges/waternet.wasm`          | Waternet: Davy Willems                                  | <https://wasm4.org/play/waternet/>          | <https://github.com/joyrider3774/waternet_wasm4> (MIT)                   | `739f355da8e90cfd25c0c677cb5397f27affca171ae7ed731fafc51f008caa93` |
| `cartridges/dragon-poker-draw.wasm` | Dragon Poker Draw: LoneGrayWolf                         | <https://wasm4.org/play/dragon-poker-draw/> | not published                                                            | `33d20f18c2ad3a6862c8f234ce4b4cac6137ce355c2e24db0529e1beb4023656` |
| `cartridges/tictactoe.wasm`         | Tic Tac Toe: Christopher Kleine                         | <https://wasm4.org/play/tictactoe/>         | <https://github.com/christopher-kleine/tic-tac-toe-wasm4> (BSD-2-Clause) | `99caf1e44523f77598e8384476aba234d35670c370eabea5e815d2680c13ac4f` |
| `cartridges/watris.wasm`            | Watris: Bruno Garcia                                    | <https://wasm4.org/play/watris/>            | <https://github.com/aduros/wasm4> `examples/watris` (ISC)                | `d66521048add571396bcaf7c80c2feb83fed0d5db3741f23f096bf389210d2d4` |
| `cartridges/glowfish-chess.wasm`    | Glowfish Chess: Analog Hors                             | <https://wasm4.org/play/glowfish-chess/>    | <https://github.com/analog-hors/glowfish> (MIT)                          | `2804cc53da22eb62d54fd67f8d0c986bb8b12321aab07eb8486009701416e159` |
| `cartridges/duck-maze.wasm`         | Duck Maze: Julia Marques Sanches                        | <https://wasm4.org/play/duck-maze/>         | not published                                                            | `72805af4802d8f46d7f4a1f4a2edb97e9a5f5e587e17b234eda2e1b654d7dec8` |
| `cartridges/untangle.wasm`          | Untangle: Mota Link                                     | <https://wasm4.org/play/untangle/>          | <https://github.com/Mota-Link/untangle-rs> (MIT)                         | `f2923336ede479ca4b47cb3fae75d4e252439908ab680d6dcb82a4f0ac0bfb62` |
| `cartridges/nyancat.wasm`           | Nyan Cat: Jake Ledoux                                   | <https://wasm4.org/play/nyancat/>           | not published                                                            | `42befc2b97c26ab4e0c792824741547229c4e3973614dc05351159388c8dd069` |
| `cartridges/sound-demo.wasm`        | Sound Demo: Bruno Garcia                                | <https://wasm4.org/play/sound-demo/>        | <https://github.com/aduros/wasm4> (ISC)                                  | `cd6e1219f2c9a95b21984ffd78fe0933c76a2f89b4391e41f8d6549935ca09f9` |
| `cartridges/plasma-cube.wasm`       | Plasma Cube: unnick                                     | <https://wasm4.org/play/plasma-cube/>       | not published                                                            | `b15a4cc80dacd759b85b471557a803216231a9b1cf0c4fae96e661127daaa0c9` |

## Test-only cartridges

These files stay in `cartridges/` for the regression suite, the corpus profiler
and the phoneME benchmark. They are not part of the release catalog and are not
packaged in the release JARs.

| File                                       | Work and author                        | Upstream page                                                                                             | SHA-256                                                            |
| ------------------------------------------ | -------------------------------------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `cartridges/mandelbrot.wasm`               | Mandelbrot: LukeGrahamLandry           | <https://wasm4.org/play/mandelbrot/>                                                                      | `400cef4768736233c2a3420817f2a27834614d2698116a089807fd8848fb3d0a` |
| `cartridges/rubido.wasm`                   | Rubido: Davy Willems                   | <https://wasm4.org/play/rubido/> / <https://github.com/joyrider3774/rubido_wasm4> (MIT)                   | `2b4b5d1c888d9286b87193d11420171eaeff3aff0bcb376b4396c9533ad115fd` |
| `cartridges/sound-test.wasm`               | Sound Test: Mr. Rafael                 | <https://wasm4.org/play/sound-test/>                                                                      | `8cf4c5dd51f47b4ed8738b673dbeb0caeecd6da75085afa6acbdf4e43cf9116a` |
| `cartridges/tankle.wasm`                   | Tankle: Milky Natas                    | <https://wasm4.org/play/tankle/>                                                                          | `3a99b6f7867d0f74db26333a317553885b9dce0cbedd9f922d7b8fc3df77aa48` |
| `cartridges/game-of-life-zig-edition.wasm` | Game of Life: Zig Edition: David Roman | <https://wasm4.org/play/game-of-life-zig-edition/> / <https://github.com/davidroman0O/wasm4-game-of-life> | `ca57b23b8bda728a6f92848f8981cfb7837c1c389639cc568c29fddca597d4d3` |

Additional upstream source links:

- Mandelbrot:
  <https://github.com/LukeGrahamLandry/franca/blob/f46e3b82d3a99a5a84a7be76695fdf5ca5216c59/tests/external/wasm4.fr#L721-L826>
- Tankle: <https://github.com/Milky2018/tankle-mbt>

Where an upstream repository is listed with a license, that license is the
author's own and is more permissive than the wasm4.org archive terms. The binary
copies retained here are the files published on the WASM-4 cartridge pages and
are attributed under the terms shown there.

## phoneME

phoneME is GPL-2.0-only software. The optional local rig uses modified phoneME
binaries, but their complete corresponding source is not public yet. The
binaries are gitignored, excluded from release JARs, and must not be added to a
public repository or source archive until that source can be published. See
[performance documentation](docs/performance.md).
