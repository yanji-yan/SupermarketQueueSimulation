import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * SUPERMARKET QUEUE SIMULATION  -  CC104 Data Structures and Algorithms
 * Data structure: one Queue (FIFO) per cashier lane  -  offer() to add, poll() to serve.
 */
public class SupermarketQueueSimulation extends JFrame {

    // GROUP MEMBERS: the first 3 work the cashier lanes (Lane 1, 2, 3); the rest walk around the store.
    // Keep at least 3 names.
    static final String[] GROUP_MEMBERS = {
        "Ashley Duque",                 // Lane 1 cashier
        "Geoffrey Jheanne Dollentas",   // Lane 2 cashier
        "Rian Estrella",                // Lane 3 cashier
        "Tomas James Alonsagay",        // stocking shelves
        "John Louise Herrera",          // pushing a cart
        "Sheila Sophie Macavinta"       // mopping the floor
    };
    static final int NUM_LANES = 3;

    static String[] cashierNames() {
        return java.util.Arrays.copyOfRange(GROUP_MEMBERS, 0, NUM_LANES);
    }

    static String[] staffNames() {
        return java.util.Arrays.copyOfRange(GROUP_MEMBERS, NUM_LANES, GROUP_MEMBERS.length);
    }

    // Random customers: first names from the class list (our own group members are not included)
    private static final String[] RANDOM_NAMES = {
        "Robelyn", "Fredelyn", "Lloela Marie", "Ellesha Kelly", "Xandie", "Mariel",
        "Clouie", "Armiashane", "Matt Justin", "Pelliejoy", "John Michael", "Janellavin",
        "April May Ahira", "Justin", "Fritchie Kyla", "Kenzo", "John Emmanuel"
    };
    // EXTRA A: NAME VALIDATION (regex). \p{L} = any letter.
    // Valid = a letter first, then up to 19 letters, spaces, . ' or -  (1 to 20 characters).
    // Names like "R2D2" or "@@@" are rejected.
    private static final Pattern VALID_NAME = Pattern.compile("^\\p{L}[\\p{L} .'-]{0,19}$");

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final StorePanel store = new StorePanel(cashierNames(), staffNames());
    private final Random random = new Random();

    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel statsLabel = new JLabel();
    private final JTextArea logArea = new JTextArea();

    public SupermarketQueueSimulation() {
        super("Supermarket Queue Simulation");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        root.add(buildIntro(), "intro");
        root.add(buildMain(), "main");
        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);          // starts maximized; resize freely
        refreshStats();
        // the "serving" lines also change by themselves when a lane becomes empty
        new javax.swing.Timer(250, e -> refreshStats()).start();
    }

    // =====================================================================
    //  Opening screen
    // =====================================================================

    /** Last word of a full name, e.g. "Ashley Duque" -> "Duque". */
    private static String lastName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    private JPanel buildIntro() {
        final BufferedImage[] pics = new BufferedImage[GROUP_MEMBERS.length];
        final Color[] vests = {
            new Color(198, 40, 40), new Color(21, 101, 192), new Color(46, 125, 50),
            new Color(239, 108, 0), new Color(106, 27, 154), new Color(0, 131, 143)
        };
        for (int i = 0; i < pics.length; i++) {
            pics[i] = Avatars.createStaff(GROUP_MEMBERS[i].hashCode(), vests[i % vests.length]);
        }

        // The intro lists members A-Z by LAST NAME (like an introduction slide).
        // Only the display order changes; the cashier lanes still follow GROUP_MEMBERS.
        final Integer[] order = new Integer[GROUP_MEMBERS.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> lastName(GROUP_MEMBERS[a]).compareToIgnoreCase(lastName(GROUP_MEMBERS[b])));

        JPanel intro = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                double sc = getHeight() / 650.0;                 // draw on a 650-unit-tall canvas
                int w = (int) Math.round(getWidth() / sc);
                int h = 650;
                g.scale(sc, sc);

                g.setPaint(new GradientPaint(0, 0, new Color(27, 94, 32), 0, h, new Color(129, 199, 132)));
                g.fillRect(0, 0, w, h);

                // shelves in the background
                for (int i = 0; i < 6; i++) {
                    int sx = 60 + i * (w - 120) / 6;
                    g.setColor(new Color(255, 255, 255, 30));
                    g.fillRoundRect(sx, 40, 120, 14, 6, 6);
                    g.fillRoundRect(sx, 70, 120, 14, 6, 6);
                }

                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 46));
                center(g, "SUPERMARKET QUEUE SIMULATION", w / 2, 190, w - 80);
                g.setFont(new Font("SansSerif", Font.PLAIN, 20));
                center(g, "CC104 - Data Structures and Algorithms  |  FIFO Queue per Cashier Lane", w / 2, 230, w - 80);

                g.setFont(new Font("SansSerif", Font.BOLD, 18));
                center(g, "OUR GROUP", w / 2, 300, w);
                int n = GROUP_MEMBERS.length;
                for (int i = 0; i < n; i++) {
                    int cx = w * (2 * i + 1) / (2 * n);
                    int m = order[i];                            // i-th member in A-Z order by last name
                    g.drawImage(pics[m], cx - 36, 320, 72, 86, null);
                    g.setFont(new Font("SansSerif", Font.BOLD, 15));
                    center(g, GROUP_MEMBERS[m], cx, 434, w / n - 16);
                }
                g.dispose();
            }

            private void center(Graphics2D g, String s, int cx, int y, int maxW) {
                Font f = g.getFont();
                FontMetrics fm = g.getFontMetrics(f);
                int sw = fm.stringWidth(s);
                if (sw > maxW) {
                    g.setFont(f.deriveFont(f.getSize2D() * maxW / sw));
                    fm = g.getFontMetrics();
                    sw = fm.stringWidth(s);
                }
                g.drawString(s, cx - sw / 2, y);
                g.setFont(f);
            }
        };
        intro.setPreferredSize(new Dimension(StorePanel.W + 290, StorePanel.H));

        JButton start = styledButton("START SIMULATION", new Color(255, 193, 7), Color.BLACK);
        start.setFont(new Font("SansSerif", Font.BOLD, 20));
        start.setPreferredSize(new Dimension(300, 52));
        start.addActionListener(e -> cards.show(root, "main"));

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(0, 0, 60, 0));
        south.add(start);
        intro.add(south, BorderLayout.SOUTH);
        return intro;
    }

    // =====================================================================
    //  Main screen: store + control panel
    // =====================================================================

    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout());
        main.add(store, BorderLayout.CENTER);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(new Color(245, 247, 250));
        side.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        side.setPreferredSize(new Dimension(290, StorePanel.H));

        side.add(heading("CONTROL PANEL"));
        side.add(gap(6));

        side.add(addButton(styledButton("Add Customer", new Color(46, 125, 50), Color.WHITE), e -> onAddCustomer()));
        side.add(gap(5));
        side.add(addButton(styledButton("Add Random Customer", new Color(0, 121, 107), Color.WHITE), e -> onAddRandom()));
        side.add(gap(5));
        for (int i = 0; i < NUM_LANES; i++) {
            final int lane = i;
            side.add(addButton(styledButton("Serve Lane " + (i + 1), new Color(21, 101, 192), Color.WHITE), e -> onServe(lane)));
            side.add(gap(5));
        }
        side.add(addButton(styledButton("Reset", new Color(239, 108, 0), Color.WHITE), e -> onReset()));
        side.add(gap(5));
        side.add(addButton(styledButton("Exit", new Color(198, 40, 40), Color.WHITE), e -> onExit()));
        side.add(gap(6));

        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusLabel.setPreferredSize(new Dimension(260, 34));
        statusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        side.add(statusLabel);
        side.add(gap(6));

        side.add(heading("STATISTICS"));
        side.add(gap(4));
        statsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        side.add(statsLabel);
        side.add(gap(8));

        side.add(heading("QUEUE LOG"));
        side.add(gap(4));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(260, 120));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        side.add(scroll);

        main.add(side, BorderLayout.EAST);
        return main;
    }

    // =====================================================================
    //  Button actions
    // =====================================================================

    /** Ask for the name (validated), unique ID is assigned automatically, user picks the lane. */
    private void onAddCustomer() {
        String previousName = "";
        int previousLane = 0;
        while (true) {
            JTextField nameField = new JTextField(previousName, 16);
            JComboBox<String> laneBox = new JComboBox<>();
            for (Lane lane : store.getLanes()) {
                laneBox.addItem("Lane " + lane.getNumber() + "  (" + lane.getWaitingCount() + " waiting)");
            }
            laneBox.setSelectedIndex(previousLane);

            JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
            form.add(new JLabel("Customer name:"));
            form.add(nameField);
            form.add(new JLabel("Choose a lane:"));
            form.add(laneBox);

            int result = JOptionPane.showConfirmDialog(this, form, "Add Customer",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;                                   // cancelled
            }

            String name = nameField.getText().trim().replaceAll("\\s+", " ");
            previousName = name;
            previousLane = laneBox.getSelectedIndex();

            // EXTRA A: the name is checked BEFORE adding. If invalid, show a message and ask again (nothing is added).
            if (!VALID_NAME.matcher(name).matches()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a valid name.\nUse letters, spaces, . ' or - only (1 to 20 characters).",
                        "Invalid name", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            int laneIndex = laneBox.getSelectedIndex();
            // EXPLAIN 6 of 9: ADD CUSTOMER button
            // Valid name -> store.addCustomer() -> Lane.addCustomer() -> queue.offer() (joins the BACK).
            // It returns null when the lane is full.
            Customer c = store.addCustomer(name, laneIndex);
            if (c == null) {
                // EXTRA B: FULL LANE. A lane holds 8 customers (Lane.isFull()), so addCustomer() returned null.
                // Suggest the open lane with the fewest customers (the user answers Yes or No).
                // If every lane is full, tell the user to serve a customer first.
                int suggested = findShortestOpenLane();
                if (suggested < 0) {
                    JOptionPane.showMessageDialog(this,
                            "All lanes are full (" + Lane.MAX_CAPACITY + " customers each).\nServe a customer first.",
                            "All lanes full", JOptionPane.WARNING_MESSAGE);
                    setStatus("All lanes are full. Serve a customer first.", true);
                    return;
                }
                int waiting = store.getLanes().get(suggested).getWaitingCount();
                int answer = JOptionPane.showConfirmDialog(this,
                        "Lane " + (laneIndex + 1) + " is full (" + Lane.MAX_CAPACITY + " customers).\n"
                        + "Add " + name + " to Lane " + (suggested + 1) + " instead? ("
                        + waiting + " waiting)",
                        "Lane full", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) {
                    previousLane = laneIndex;
                    continue;                              // nothing added; back to the form to pick again
                }
                laneIndex = suggested;
                c = store.addCustomer(name, laneIndex);
                if (c == null) {                           // safety net, should not happen
                    continue;
                }
            }
            announceArrival(c, laneIndex);
            return;
        }
    }

    /** Returns the index of the non-full lane with the fewest waiting customers, or -1 if every lane is full. */
    private int findShortestOpenLane() {
        // EXTRA B (how it works): check each lane, skip full ones, keep the one with the fewest waiting.
        // best = -1 means "none found yet"; if it is still -1 at the end, all lanes are full.
        int best = -1;
        for (int i = 0; i < store.getLanes().size(); i++) {
            Lane lane = store.getLanes().get(i);
            if (!lane.isFull() && (best < 0 || lane.getWaitingCount() < store.getLanes().get(best).getWaitingCount())) {
                best = i;
            }
        }
        return best;
    }

    private void onAddRandom() {
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < store.getLanes().size(); i++) {
            if (!store.getLanes().get(i).isFull()) {
                open.add(i);
            }
        }
        if (open.isEmpty()) {
            setStatus("All lanes are full. Serve a customer first.", true);
            return;
        }
        // EXTRA C: ADD RANDOM CUSTOMER. Pick a random lane from 'open' (lanes that are not full) and a random name,
        // then add the customer the same way as the normal button (offer() at the back).
        int laneIndex = open.get(random.nextInt(open.size()));
        String name = RANDOM_NAMES[random.nextInt(RANDOM_NAMES.length)];
        Customer c = store.addCustomer(name, laneIndex);
        announceArrival(c, laneIndex);
    }

    private void announceArrival(Customer c, int laneIndex) {
        setStatus(c.getName() + " (" + c.getIdLabel() + ") joined Lane " + (laneIndex + 1)
                + " at position " + c.getPosition() + ".", false);
        // QUEUE LOG: every offer() and poll() is written in the log on the right.
        // Read it top to bottom: the customer who got offer() first also gets poll() first = FIFO.
        log("offer(): " + c + " -> Lane " + (laneIndex + 1) + ", position " + c.getPosition());
        refreshStats();
    }

    private void onServe(int laneIndex) {
        // EXPLAIN 7 of 9: SERVE LANE button -> store.serveLane() -> Lane.serve() -> queue.poll()
        // (the FRONT customer leaves first).
        Customer c = store.serveLane(laneIndex);
        // EMPTY LANE: poll() returned null, so we show a message instead of crashing.
        if (c == null) {
            setStatus("Lane " + (laneIndex + 1) + " is empty - nobody to serve.", true);
            log("poll(): Lane " + (laneIndex + 1) + " is empty (returned null)");
        } else {
            int left = store.getLanes().get(laneIndex).getWaitingCount();
            setStatus("Lane " + (laneIndex + 1) + " served " + c.getName() + " (" + c.getIdLabel() + ").", false);
            log("poll(): " + c + " served at Lane " + (laneIndex + 1) + " (" + left + " still waiting)");
        }
        refreshStats();
    }

    private void onReset() {
        int answer = JOptionPane.showConfirmDialog(this,
                "Remove all customers and reset the counters?", "Reset", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        store.reset();
        logArea.setText("");
        setStatus("Simulation reset.", false);
        refreshStats();
    }

    private void onExit() {
        int answer = JOptionPane.showConfirmDialog(this,
                "Close the simulation?", "Exit", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }

    // =====================================================================
    //  Statistics, status and log (all read from the real lane data)
    // =====================================================================

    private void refreshStats() {
        // STATISTICS: all numbers come from the real queues (getWaitingCount() = queue.size()).
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>Waiting customers:</b> ").append(store.getTotalWaiting()).append("<br>");
        sb.append("<b>Customers served:</b> ").append(store.getTotalServed()).append("<br><br>");
        for (Lane lane : store.getLanes()) {
            Customer served = lane.getCurrentlyServed();
            sb.append("<b>Lane ").append(lane.getNumber()).append(":</b> ")
              .append(lane.getWaitingCount()).append(" waiting, ")
              .append(lane.getServedCount()).append(" served<br>")
              .append("&nbsp;&nbsp;Serving: ")
              .append(served == null ? "-" : served.getName() + " (" + served.getIdLabel() + ")")
              .append("<br>");
        }
        sb.append("</html>");
        statsLabel.setText(sb.toString());
    }

    private void setStatus(String message, boolean warning) {
        statusLabel.setForeground(warning ? new Color(198, 40, 40) : new Color(27, 94, 32));
        statusLabel.setText("<html>" + message + "</html>");
    }

    private void log(String line) {
        logArea.append(line + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // =====================================================================
    //  Small UI helpers
    // =====================================================================

    private JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 14));
        l.setForeground(new Color(55, 71, 79));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private Component gap(int h) {
        return javax.swing.Box.createRigidArea(new Dimension(0, h));
    }

    private JButton styledButton(String text, Color bg, Color fg) {
        return new ColorButton(text, bg, fg);
    }

    /** A button that paints its own background and text, so it looks the same on every system. */
    private static class ColorButton extends JButton {
        private final Color bg;

        ColorButton(String text, Color bg, Color fg) {
            super(text);
            this.bg = bg;
            setForeground(fg);
            setFont(new Font("SansSerif", Font.BOLD, 13));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color c = bg;
            if (getModel().isPressed()) {
                c = bg.darker();
            } else if (getModel().isRollover()) {
                c = bg.brighter();
            }
            g.setColor(c);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g.setColor(getForeground());
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(getText())) / 2;
            int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(getText(), tx, ty);
            g.dispose();
        }
    }

    private JButton addButton(JButton b, ActionListener action) {
        b.addActionListener(action);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return b;
    }

    public static void main(String[] args) {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
        SwingUtilities.invokeLater(() -> new SupermarketQueueSimulation().setVisible(true));
    }
}
