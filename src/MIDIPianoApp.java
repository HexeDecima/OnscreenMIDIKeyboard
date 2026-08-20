import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;
import java.util.ArrayList;

public class MIDIPianoApp extends JFrame {
    private static final String PREFS_NODE = "midipiano";
    private final Preferences prefs = Preferences.userRoot().node(PREFS_NODE);

    private PianoPanel pianoPanel;
    private JCheckBoxMenuItem showLabelsItem;

    public MIDIPianoApp() {
        setTitle("On-Screen MIDI Keyboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Restore window position and size from preferences
        int x = prefs.getInt("window.x", 100);
        int y = prefs.getInt("window.y", 100);
        int w = prefs.getInt("window.width", 1200);
        int h = prefs.getInt("window.height", 300);
        setBounds(x, y, w, h);

        // Main piano panel
        pianoPanel = new PianoPanel();
        add(pianoPanel, BorderLayout.CENTER);

        // Menu bar with controls
        JMenuBar menuBar = new JMenuBar();
        JMenu viewMenu = new JMenu("View");
        showLabelsItem = new JCheckBoxMenuItem("Show Note Labels");
        showLabelsItem.addActionListener(e -> {
            pianoPanel.setShowLabels(showLabelsItem.isSelected());
        });
        viewMenu.add(showLabelsItem);
        menuBar.add(viewMenu);

        // Transpose (optional)
        JMenu transposeMenu = new JMenu("Transpose");
        for (int i = -12; i <= 12; i++) {
            JMenuItem item = new JMenuItem((i >= 0 ? "+" : "") + i);
            int semitones = i;
            item.addActionListener(e -> pianoPanel.setTranspose(semitones));
            transposeMenu.add(item);
        }
        menuBar.add(transposeMenu);
        setJMenuBar(menuBar);

        // Save preferences on window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                savePreferences();
            }
        });

        // Start MIDI input
        pianoPanel.startMIDI();

        setVisible(true);
    }

    private void savePreferences() {
        Rectangle bounds = getBounds();
        prefs.putInt("window.x", bounds.x);
        prefs.putInt("window.y", bounds.y);
        prefs.putInt("window.width", bounds.width);
        prefs.putInt("window.height", bounds.height);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MIDIPianoApp::new);
    }
}

/**
 * JPanel that draws the piano keyboard, handles MIDI input, and supports resizing.
 */
class PianoPanel extends JPanel implements Receiver, ComponentListener {
    private static final int TOTAL_KEYS = 88;                 // A0 (note 21) to C8 (note 108)
    private static final int FIRST_NOTE = 21;
    private static final int LAST_NOTE = 108;

    // Key state: true = pressed
    private final boolean[] pressed = new boolean[TOTAL_KEYS];

    // Geometry of each key (computed on resize)
    private final Rectangle[] whiteKeyRects = new Rectangle[TOTAL_KEYS];
    private final Rectangle[] blackKeyRects = new Rectangle[TOTAL_KEYS];
    private final boolean[] isBlack = new boolean[TOTAL_KEYS];

    private final Color pressedColor = new Color(0x4A90D9); // user‑selectable blue

    private int transpose = 0;
    private boolean showLabels = false;

    // MIDI device and transmitter
    private MidiDevice midiDevice;
    private Transmitter transmitter;

    public PianoPanel() {
        setBackground(Color.WHITE);
        setFocusable(true);
        addComponentListener(this);

        // Map which notes are black (standard piano pattern)
        // Note numbers: 21=A0, 22=A#0, 23=B0, 24=C1, ...
        // Black keys: 1,3,6,8,10 (relative to C)
        for (int i = 0; i < TOTAL_KEYS; i++) {
            int note = FIRST_NOTE + i;
            int mod = note % 12;
            isBlack[i] = (mod == 1 || mod == 3 || mod == 6 || mod == 8 || mod == 10);
        }
    }

    /**
     * Compute key rectangles based on current panel size.
     * Maintains a fixed aspect ratio (width/height) for the keyboard.
     */
    private void computeKeyRects() {
        Dimension size = getSize();
        if (size.width <= 0 || size.height <= 0) return;

        // Desired aspect ratio: for 88 keys, width is about 7.5 times height
        // We'll compute the largest rectangle that fits inside the panel with a fixed ratio.
        double targetAspect = 7.5; // empirically chosen
        int panelW = size.width;
        int panelH = size.height;

        int keybW, keybH;
        if ((double) panelW / panelH > targetAspect) {
            keybH = panelH;
            keybW = (int) (keybH * targetAspect);
        } else {
            keybW = panelW;
            keybH = (int) (keybW / targetAspect);
        }
        // Center the keyboard within the panel
        int offsetX = (panelW - keybW) / 2;
        int offsetY = (panelH - keybH) / 2;

        // White key width and height
        int whiteCount = 0;
        for (int i = 0; i < TOTAL_KEYS; i++) {
            if (!isBlack[i]) whiteCount++;
        }
        double whiteWidth = (double) keybW / whiteCount;
        int whiteHeight = keybH;

        // Black key dimensions: 60% width of white, 60% height of white, positioned at top
        double blackWidthFactor = 0.6;
        double blackHeightFactor = 0.6;
        int blackWidth = (int) (whiteWidth * blackWidthFactor);
        int blackHeight = (int) (whiteHeight * blackHeightFactor);
        int blackY = offsetY; // top of keyboard

        // Build white keys first
        int whiteIndex = 0;
        double x = offsetX;
        for (int i = 0; i < TOTAL_KEYS; i++) {
            if (!isBlack[i]) {
                int left = (int) Math.round(x);
                int w = (int) Math.round(x + whiteWidth) - left; // handle rounding
                whiteKeyRects[i] = new Rectangle(left, offsetY, w, whiteHeight);
                x += whiteWidth;
                whiteIndex++;
            } else {
                whiteKeyRects[i] = null;
            }
        }

        // Build black keys: placed between white keys that have a black key.
        // For each note, if it's black, find the two surrounding white keys.
        for (int i = 0; i < TOTAL_KEYS; i++) {
            if (isBlack[i]) {
                // Find previous and next white keys
                int prevWhite = -1, nextWhite = -1;
                for (int j = i - 1; j >= 0; j--) {
                    if (!isBlack[j]) { prevWhite = j; break; }
                }
                for (int j = i + 1; j < TOTAL_KEYS; j++) {
                    if (!isBlack[j]) { nextWhite = j; break; }
                }
                if (prevWhite >= 0 && nextWhite >= 0) {
                    Rectangle rPrev = whiteKeyRects[prevWhite];
                    Rectangle rNext = whiteKeyRects[nextWhite];
                    if (rPrev != null && rNext != null) {
                        // Black key centered over the gap between the two white keys
                        int left = rPrev.x + rPrev.width - blackWidth / 2;
                        // Clamp to avoid overlapping next white? Actually black key overlaps both.
                        // We'll center it between the two whites.
                        int centerX = (rPrev.x + rPrev.width + rNext.x) / 2;
                        left = centerX - blackWidth / 2;
                        // Ensure it doesn't exceed the right edge of prev white
                        left = Math.max(left, rPrev.x);
                        // Ensure it stays left of next white
                        left = Math.min(left, rNext.x + rNext.width - blackWidth);
                        blackKeyRects[i] = new Rectangle(left, blackY, blackWidth, blackHeight);
                    }
                }
            } else {
                blackKeyRects[i] = null;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        computeKeyRects();

        // Draw white keys first
        for (int i = 0; i < TOTAL_KEYS; i++) {
            if (!isBlack[i] && whiteKeyRects[i] != null) {
                drawKey(g2, whiteKeyRects[i], pressed[i], false, i);
            }
        }
        // Draw black keys on top
        for (int i = 0; i < TOTAL_KEYS; i++) {
            if (isBlack[i] && blackKeyRects[i] != null) {
                drawKey(g2, blackKeyRects[i], pressed[i], true, i);
            }
        }
        g2.dispose();
    }

    /**
     * Draws a single key with 3D effect (normal or pressed).
     */
    private void drawKey(Graphics2D g2, Rectangle rect, boolean isPressed, boolean isBlackKey, int noteIndex) {
        int x = rect.x;
        int y = rect.y;
        int w = rect.width;
        int h = rect.height;

        // Background color
        Color baseColor;
        if (isBlackKey) {
            baseColor = Color.BLACK;
        } else {
            baseColor = Color.WHITE;
        }

        if (isPressed) {
            // Pressed effect: key appears lowered, with inset shadow and pressed color overlay
            // Draw dark base
            g2.setColor(baseColor.darker());
            g2.fillRect(x, y, w, h);

            // Overlay with pressed color (semi‑transparent)
            g2.setColor(new Color(pressedColor.getRed(), pressedColor.getGreen(), pressedColor.getBlue(), 160));
            g2.fillRect(x, y, w, h);

            // Inset shadow: draw a dark border inside
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(x + 1, y + 1, w - 3, h - 3);
            // Add a subtle highlight at bottom to simulate depression
            g2.setColor(new Color(0, 0, 0, 50));
            g2.fillRect(x + 2, y + 2, w - 4, 2);
            g2.fillRect(x + 2, y + h - 4, w - 4, 2);
            g2.fillRect(x + 2, y + 2, 2, h - 4);
            g2.fillRect(x + w - 4, y + 2, 2, h - 4);
        } else {
            // Normal 3D raised look
            g2.setColor(baseColor);
            g2.fillRect(x, y, w, h);

            if (isBlackKey) {
                // Black key: subtle gradient
                GradientPaint gp = new GradientPaint(x, y, Color.DARK_GRAY, x, y + h, Color.BLACK);
                g2.setPaint(gp);
                g2.fillRect(x, y, w, h);
                // Border
                g2.setColor(Color.BLACK);
                g2.drawRect(x, y, w - 1, h - 1);
                // Highlight on top
                g2.setColor(new Color(80, 80, 80));
                g2.drawLine(x + 2, y + 1, x + w - 3, y + 1);
            } else {
                // White key: border and slight gradient
                GradientPaint gp = new GradientPaint(x, y, new Color(240, 240, 240), x, y + h, Color.WHITE);
                g2.setPaint(gp);
                g2.fillRect(x, y, w, h);
                g2.setColor(Color.LIGHT_GRAY);
                g2.drawRect(x, y, w - 1, h - 1);
                // Highlight on top
                g2.setColor(Color.WHITE);
                g2.drawLine(x + 2, y + 1, x + w - 3, y + 1);
            }
        }

        // Optionally draw note label
        if (showLabels) {
            String label = getNoteName(FIRST_NOTE + noteIndex);
            g2.setColor(isBlackKey ? Color.WHITE : Color.BLACK);
            Font font = new Font("SansSerif", Font.PLAIN, Math.min(w / 4, 12));
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int labelW = fm.stringWidth(label);
            int labelH = fm.getAscent();
            int labelX = x + (w - labelW) / 2;
            int labelY = y + h - 5; // bottom
            if (isBlackKey) labelY = y + h - 4;
            g2.drawString(label, labelX, labelY);
        }
    }

    /**
     * Returns the note name (e.g., "C", "C#") for a given MIDI note number.
     */
    private String getNoteName(int note) {
        int mod = note % 12;
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        return names[mod];
    }

    // ---- MIDI handling ----

    /**
     * Starts MIDI input: enumerates devices and attaches a receiver.
     */
    public void startMIDI() {
        try {
            MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
            for (MidiDevice.Info info : infos) {
                MidiDevice device = MidiSystem.getMidiDevice(info);
                if (device.getMaxTransmitters() != 0) {
                    // This device can send MIDI messages
                    try {
                        device.open();
                        Transmitter trans = device.getTransmitter();
                        trans.setReceiver(this);
                        // Keep reference to prevent GC
                        this.midiDevice = device;
                        this.transmitter = trans;
                        System.out.println("Connected to MIDI input: " + info.getName());
                        return;
                    } catch (MidiUnavailableException e) {
                        // Try next device
                    }
                }
            }
            System.out.println("No MIDI input device found.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (message instanceof ShortMessage) {
            ShortMessage sm = (ShortMessage) message;
            int command = sm.getCommand();
            int channel = sm.getChannel();
            int note = sm.getData1();
            int velocity = sm.getData2();

            // Apply transpose (only if we want to remap notes; here we simply transpose display)
            // We'll keep the note mapping as is, but we could shift which key lights up.
            // For simplicity, we ignore transpose for MIDI input (it's only for output if we add that).
            // But we could implement transpose by adjusting the note index.
            int index = note - FIRST_NOTE;
            if (index >= 0 && index < TOTAL_KEYS) {
                if (command == ShortMessage.NOTE_ON && velocity > 0) {
                    pressed[index] = true;
                } else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && velocity == 0)) {
                    pressed[index] = false;
                }
                repaint();
            }
        }
    }

    @Override
    public void close() {
        // Clean up MIDI resources
        if (transmitter != null) {
            transmitter.close();
            transmitter = null;
        }
        if (midiDevice != null && midiDevice.isOpen()) {
            midiDevice.close();
            midiDevice = null;
        }
    }

    // ---- ComponentListener for resize ----

    @Override
    public void componentResized(ComponentEvent e) {
        repaint();
    }

    @Override public void componentMoved(ComponentEvent e) {}
    @Override public void componentShown(ComponentEvent e) {}
    @Override public void componentHidden(ComponentEvent e) {}

    // ---- Setters for controls ----

    public void setShowLabels(boolean show) {
        this.showLabels = show;
        repaint();
    }

    public void setTranspose(int semitones) {
        this.transpose = semitones;
        // For future MIDI output, we would shift notes.
        // For visual only, we could highlight different keys, but not implemented here.
        // Could also change note labels.
        repaint();
    }
}