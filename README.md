# 🎹 MIDI Piano – On‑Screen 88‑Key Keyboard

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

A standalone Java Swing application that displays a realistic 88‑key piano.  
It can:
- **Visualise MIDI input** – highlight keys as you play on a connected MIDI keyboard.
- **Play with your mouse** – click or drag on the on‑screen keys to hear sounds via the built‑in General MIDI synthesizer.

The UI is fully dark‑themed (including the title bar on macOS), resizes cleanly while preserving the piano’s aspect ratio, and remembers its window size and position.

---

## ✨ Features

- Full 88‑key piano (A0 – C8) with standard layout.
- Realistic ivory white keys and 3D‑shaded black keys.
- **Two modes** (toggle button):
  - **MIDI Input** – listen to any connected MIDI keyboard.
  - **Mouse Click** – play notes by clicking or dragging on the keys.
- Dark theme (macOS title bar forced dark, cross‑platform UI dark).
- Window resizing with fixed aspect ratio (no empty space).
- Preferences saved automatically (window size/position).
- Pure Java – no external libraries.

---

## 📦 Requirements

- **Java 17 or later** (JDK, not just JRE) – for `jpackage`.
- **General MIDI soundbank** – usually bundled with the JDK (for mouse‑click sound).

---

## 🚀 Quick Start (from source)

1. **Clone or download** the repository and navigate to the folder containing `MIDIPianoApp.java`.

2. **Compile**:
   ```bash
   javac MIDIPianoApp.java

---

## Run:

bash
java MIDIPianoApp
On macOS, to force the dark title bar (if system is in Light Mode):

bash
java -Dapple.awt.application.appearance=NSAppearanceNameDarkAqua MIDIPianoApp
📦 Create a Runnable JAR
bash
jar cfe MIDIPianoApp.jar MIDIPianoApp *.class
Now you can run it with:

bash
java -jar MIDIPianoApp.jar
📱 Package as Native Installer
Use jpackage (included with Java 17+) to create platform‑specific packages.

From the folder containing MIDIPianoApp.jar:

## macOS – .app bundle
bash
jpackage --type app-image --name "MIDIPiano" --input . --main-jar MIDIPianoApp.jar
Creates MIDIPiano.app – drag to Applications.

## Windows – .exe installer
bash
jpackage --type exe --name "MIDIPiano" --input . --main-jar MIDIPianoApp.jar
Produces an installer executable.

## Linux – .deb package (Debian/Ubuntu)
bash
jpackage --type deb --name "MIDIPiano" --input . --main-jar MIDIPianoApp.jar
For other distributions, use rpm or app-image.

## Note: jpackage may require additional build tools (e.g., WiX on Windows, dpkg on Debian). They are usually installed separately if needed.

🕹️ Usage
Toggle button (top‑left) – switches between modes. The button always shows the opposite mode, so you know what will happen when you click it.

MIDI Input – the on‑screen keys light up when you play a connected MIDI keyboard.

Mouse Click – click or drag on the keys to hear piano sounds via the internal synthesizer.

Resize the window freely – the piano fills the space while maintaining its proportion.

Preferences – window size and position are saved automatically and restored on next launch.

---
## 🛠️ Troubleshooting

| Issue | Solution |
|-------|----------|
| **No sound in Mouse mode** | Ensure your Java installation includes a soundbank. Most do, but you can load one manually in the code if needed. |
| **MIDI keyboard not detected** | The app connects to the first input‑capable device. If you have multiple, you may need to modify `startMIDIInput()` to select the correct one. |
| **White title bar on macOS** | Run with `-Dapple.awt.application.appearance=NSAppearanceNameDarkAqua` as shown above, or set your system to Dark Mode. |
| **Window doesn’t resize properly** | The aspect‑ratio enforcement adjusts the frame height automatically – if you encounter glitches, check the `adjustFrameSize()` method. |

---
🙏 Acknowledgements
Built with standard Java libraries (javax.sound.midi, java.util.prefs, javax.swing).
