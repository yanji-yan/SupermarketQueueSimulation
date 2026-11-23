import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The supermarket scene. It owns the lanes (one Queue each) and animates customers.
 * Every customer's walking target is computed from the REAL queue position, so the
 * animation always matches the queue data.
 *
 * The scene is drawn on a "logical" canvas that is H (650) units tall. It is scaled to
 * the window height, and the logical width (viewW) follows the window width, so the
 * store fills the window.
 */
public class StorePanel extends JPanel {

    public static final int W = 960;     // design / preferred width
    public static final int H = 650;     // logical height (always)

    private static final int DOOR_X = 55;
    private static final int DOOR_Y = 620;
    private static final int SERVE_Y = 274;      // where a customer stands while being served
    private static final int FIRST_SLOT_Y = 318;
    private static final int SLOT_SPACING = 42;

    private static final int WAIT_LEFT = 50;     // waiting area: from cx - 50 ...
    private static final int WAIT_RIGHT = 130;   // ... to cx + 130
    private static final int WALK_MIN_Y = 330;   // staff walkway limits
    private static final int WALK_MAX_Y = 590;

    private static final Color[] LANE_COLORS = {
        new Color(66, 133, 244), new Color(255, 152, 0), new Color(156, 39, 176),
        new Color(0, 150, 136), new Color(233, 30, 99)
    };
    // One colour for every customer line (waiting box + position badges). Change it here to recolour all lanes.
    private static final Color QUEUE_COLOR = new Color(156, 39, 176);
    // Vest colour of each group member, by position in GROUP_MEMBERS.
    private static final Color[] STAFF_COLORS = {
        new Color(198, 40, 40), new Color(21, 101, 192), new Color(46, 125, 50),
        new Color(239, 108, 0), new Color(106, 27, 154), new Color(0, 131, 143)
    };
    private static final Color[] PRODUCT_COLORS = {
        new Color(229, 57, 53), new Color(251, 192, 45), new Color(67, 160, 71), new Color(30, 136, 229),
        new Color(251, 140, 0), new Color(142, 36, 170), new Color(0, 172, 193), new Color(244, 143, 177)
    };
    private static final String[] AISLE_NAMES = {"Snacks", "Drinks", "Fresh", "Frozen", "Bakery"};
    private static final String[] STAFF_JOBS = {"Stocking shelves", "Pushing a cart", "Mopping the floor"};

    private final List<Lane> lanes = new ArrayList<>();
    private final List<Walker> walkers = new ArrayList<>();      // staff members wandering the aisles
    private final List<Customer> leaving = new ArrayList<>();    // drawing only (already polled)
    private final Random random = new Random();
    private long[] serveAt;                 // when each cashier last served (for the "Next, please!" animation)
    // UNIQUE CUSTOMER IDs: each new customer gets nextId, then it goes up by 1 (101, 102, 103 ...).
    // reset() sets it back to 101.
    private int nextId = 101;

    private double scale = 1.0;             // window height / H
    private int viewW = W;                  // logical width (follows the window width)

    /**
     * @param cashierNames group members who work the cashier lanes (one per lane)
     * @param staffNames   the other group members (they walk around the store)
     */
    public StorePanel(String[] cashierNames, String[] staffNames) {
        setPreferredSize(new Dimension(W, H));

        // same name + same vest colour as on the opening screen
        for (int i = 0; i < cashierNames.length; i++) {
            BufferedImage pic = Avatars.createStaff(cashierNames[i].hashCode(), STAFF_COLORS[i % STAFF_COLORS.length]);
            lanes.add(new Lane(i + 1, cashierNames[i], pic));
        }
        serveAt = new long[cashierNames.length];
        java.util.Arrays.fill(serveAt, -10000L);

        for (int j = 0; j < staffNames.length; j++) {
            int colourIndex = cashierNames.length + j;
            BufferedImage pic = Avatars.createStaff(staffNames[j].hashCode(), STAFF_COLORS[colourIndex % STAFF_COLORS.length]);
            walkers.add(new Walker(staffNames[j], pic, j % STAFF_JOBS.length, j));
        }

        Timer timer = new Timer(20, e -> tick());   // ~50 frames per second
        timer.start();
    }

    // =====================================================================
    //  Operations used by the buttons
    // =====================================================================

    /** Creates a customer with a unique ID and offer()s them to the end of the lane. */
    public Customer addCustomer(String name, int laneIndex) {
        Lane lane = lanes.get(laneIndex);
        if (lane.isFull()) {
            return null;
        }
        // EXPLAIN 6 of 9: ADD CUSTOMER button -> offer()
        // nextId++ gives the customer a unique ID. They start at the entrance door.
        Customer c = new Customer(nextId++, name, Avatars.createCustomer(random.nextLong()));
        c.x = DOOR_X;
        c.y = DOOR_Y;
        // Button -> StorePanel.addCustomer() -> Lane.addCustomer() -> queue.offer(c): joins the BACK of the lane.
        lane.addCustomer(c);
        syncTargets();
        return c;
    }

    /** poll()s the first customer of the lane. Returns null if the lane is empty. */
    public Customer serveLane(int laneIndex) {
        Lane lane = lanes.get(laneIndex);
        // EXPLAIN 7 of 9: SERVE LANE button -> poll()
        // Button -> StorePanel.serveLane() -> Lane.serve() -> queue.poll(): only the FRONT customer is removed (FIFO).
        // c == null means the lane was empty. Otherwise syncTargets() (EXPLAIN 8) moves everyone behind forward.
        Customer c = lane.serve();
        if (c == null) {
            return null;
        }
        serveAt[laneIndex] = System.currentTimeMillis();
        c.leaving = true;
        c.leavePhase = 0;
        c.targetX = laneX(laneIndex);
        c.targetY = SERVE_Y;
        leaving.add(c);
        syncTargets();                      // everybody else moves forward
        return c;
    }

    public void reset() {
        for (Lane lane : lanes) {
            lane.reset();
        }
        leaving.clear();
        java.util.Arrays.fill(serveAt, -10000L);
        nextId = 101;
        repaint();
    }

    /** Points every waiting customer at the screen slot that matches their REAL queue position. */
    private void syncTargets() {
        for (int i = 0; i < lanes.size(); i++) {
            for (Customer c : lanes.get(i).getCustomers()) {
                c.targetX = laneX(i);
                // EXPLAIN 8 of 9: THE PICTURE FOLLOWS THE QUEUE
                // This line connects the queue DATA to the screen: position 1 -> slot nearest the cashier,
                // position 2 -> next slot, ... After poll() the positions shrink, so customers walk forward
                // by themselves. The drawing has no order of its own - it copies the queue.
                c.targetY = slotY(c.getPosition());
            }
        }
    }

    // =====================================================================
    //  Data for the statistics panel
    // =====================================================================

    public List<Lane> getLanes() { return lanes; }

    public int getTotalWaiting() {
        int n = 0;
        for (Lane l : lanes) n += l.getWaitingCount();
        return n;
    }

    public int getTotalServed() {
        int n = 0;
        for (Lane l : lanes) n += l.getServedCount();
        return n;
    }

    // =====================================================================
    //  Layout (follows the window size)
    // =====================================================================

    private void updateView() {
        if (getHeight() <= 0 || getWidth() <= 0) {
            return;
        }
        scale = getHeight() / (double) H;
        int w = Math.max(700, (int) Math.round(getWidth() / scale));
        if (w != viewW) {
            viewW = w;
            syncTargets();                  // waiting customers re-line up in the new lane positions
            for (Walker walker : walkers) {
                walker.relocate();
            }
        }
    }

    private int laneX(int laneIndex) {
        return viewW * (2 * laneIndex + 1) / (2 * lanes.size());
    }

    private int slotY(int position) {
        return FIRST_SLOT_Y + (position - 1) * SLOT_SPACING;
    }

    /** The free walkway between lane i and lane i+1: {left, right}. Staff only walk here. */
    private int[] corridor(int i) {
        if (lanes.size() < 2) {
            return new int[]{60, viewW - 60};
        }
        return new int[]{laneX(i) + WAIT_RIGHT + 8, laneX(i + 1) - WAIT_LEFT - 8};
    }

    private int corridorCount() {
        return Math.max(1, lanes.size() - 1);
    }

    // =====================================================================
    //  Staff walking around the store
    // =====================================================================

    /** A group member who walks along an aisle (never through a waiting line) doing a job. */
    private class Walker {
        final String name;
        final BufferedImage pic;
        final int job;
        final int aisle;                 // which corridor this person works in
        double x, y, tx, ty;
        int dir = 1;                     // 1 = facing right, -1 = facing left
        long pauseUntil;
        final double speed;

        Walker(String name, BufferedImage pic, int job, int index) {
            this.name = name;
            this.pic = pic;
            this.job = job;
            this.aisle = index % corridorCount();
            this.speed = 0.9 + random.nextDouble() * 0.6;
            int[] c = corridor(aisle);
            this.x = (c[0] + c[1]) / 2.0;
            this.y = WALK_MIN_Y + random.nextInt(WALK_MAX_Y - WALK_MIN_Y + 1);
            pickTarget();
        }

        void pickTarget() {
            int[] c = corridor(aisle);
            int lo = c[0] + 52;
            int hi = c[1] - 52;
            if (hi < lo) {
                lo = (c[0] + c[1]) / 2;
                hi = lo;
            }
            tx = lo + random.nextInt(hi - lo + 1);
            ty = WALK_MIN_Y + random.nextInt(WALK_MAX_Y - WALK_MIN_Y + 1);
        }

        void relocate() {                // the window size changed
            int[] c = corridor(aisle);
            x = (c[0] + c[1]) / 2.0;
            y = Math.max(WALK_MIN_Y, Math.min(WALK_MAX_Y, y));
            pickTarget();
        }

        void step(long now) {
            if (now < pauseUntil) {
                return;                  // standing still for a moment
            }
            double dx = tx - x;
            double dy = ty - y;
            double dist = Math.hypot(dx, dy);
            if (dist <= speed) {
                x = tx;
                y = ty;
                pauseUntil = now + 500 + random.nextInt(1200);
                pickTarget();
                return;
            }
            x += dx / dist * speed;
            y += dy / dist * speed;
            if (Math.abs(dx) > 1) {
                dir = dx > 0 ? 1 : -1;
            }
        }
    }

    // =====================================================================
    //  Animation
    // =====================================================================

    private void tick() {
        updateView();
        long now = System.currentTimeMillis();
        for (Walker w : walkers) {
            w.step(now);
        }
        for (Lane lane : lanes) {
            for (Customer c : lane.getCustomers()) {
                c.x = ease(c.x, c.targetX);
                c.y = ease(c.y, c.targetY);
            }
        }
        // EXPLAIN 9 of 9: THE SERVED CUSTOMER LEAVES
        // poll() already removed the customer from the queue, so they stay in the 'leaving' list only for
        // the animation: phase 0 = walk to the cashier, phase 1 = walk off the screen.
        // The Timer calls tick() every 20 ms and each call moves everyone a little (ease()).
        Iterator<Customer> it = leaving.iterator();
        while (it.hasNext()) {
            Customer c = it.next();
            c.x = ease(c.x, c.targetX);
            c.y = ease(c.y, c.targetY);
            if (c.leavePhase == 0 && Math.abs(c.x - c.targetX) < 1 && Math.abs(c.y - c.targetY) < 1) {
                c.leavePhase = 1;                       // reached the counter -> walk away
                c.targetX = viewW + 90;
            } else if (c.leavePhase == 1 && c.x > viewW + 40) {
                it.remove();                            // left the screen
                Lane lane = lanes.get(c.laneNumber - 1);
                if (lane.isEmpty() && lane.getCurrentlyServed() == c) {
                    lane.clearCurrentlyServed();        // nobody left -> "Now serving: -"
                }
            }
        }
        repaint();
    }

    private double ease(double current, double target) {
        double d = target - current;
        if (Math.abs(d) < 0.6) {
            return target;
        }
        double step = d * 0.12;
        if (Math.abs(step) < 1.5) {
            step = Math.signum(d) * Math.min(1.5, Math.abs(d));
        }
        return current + step;
    }

    // =====================================================================
    //  Drawing
    // =====================================================================

    // The floor, shelves, sign and aisles never change, so they are drawn ONCE into an image
    // and only copied each frame (this is what keeps the animation smooth).
    private BufferedImage bgCache;
    private int bgCacheW, bgCacheH, bgCacheViewW;

    private static final java.util.Map<Integer, Font> FONTS = new java.util.HashMap<>();

    private static Font font(int style, int size) {
        return FONTS.computeIfAbsent(style * 1000 + size, k -> new Font("SansSerif", style, size));
    }

    private void setHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        updateView();
        int pw = getWidth();
        int ph = getHeight();
        if (pw <= 0 || ph <= 0) {
            return;
        }
        if (bgCache == null || bgCacheW != pw || bgCacheH != ph || bgCacheViewW != viewW) {
            bgCache = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
            Graphics2D bg = bgCache.createGraphics();
            setHints(bg);
            bg.scale(scale, scale);
            paintBackground(bg);
            paintAisles(bg);
            bg.dispose();
            bgCacheW = pw;
            bgCacheH = ph;
            bgCacheViewW = viewW;
        }
        g0.drawImage(bgCache, 0, 0, null);

        Graphics2D g = (Graphics2D) g0.create();
        setHints(g);
        g.scale(scale, scale);

        for (int i = 0; i < lanes.size(); i++) {
            paintLane(g, i);
        }
        paintDoor(g);
        paintWalkers(g);                       // staff walk in the aisles, never through a line
        for (int i = 0; i < lanes.size(); i++) {
            for (Customer c : lanes.get(i).getCustomers()) {
                paintCustomer(g, c, QUEUE_COLOR, true);
            }
        }
        for (Customer c : leaving) {
            paintCustomer(g, c, QUEUE_COLOR, false);
        }
        g.dispose();
    }

    private void paintBackground(Graphics2D g) {
        // tiled floor
        for (int y = 0; y < H; y += 40) {
            for (int x = 0; x < viewW + 40; x += 40) {
                boolean alt = ((x / 40) + (y / 40)) % 2 == 0;
                g.setColor(alt ? new Color(238, 241, 243) : new Color(226, 230, 233));
                g.fillRect(x, y, 40, 40);
            }
        }

        // back wall
        g.setPaint(new GradientPaint(0, 0, new Color(250, 244, 232), 0, 100, new Color(232, 222, 204)));
        g.fillRect(0, 0, viewW, 100);

        // two rows of shelves full of products (same look every frame)
        Random r = new Random(42);
        int[] boardY = {42, 84};
        for (int row = 0; row < boardY.length; row++) {
            int by = boardY[row];
            int x = 4;
            while (x < viewW - 10) {
                int pw = 12 + r.nextInt(12);
                int ph = 16 + r.nextInt(16);
                Color c = PRODUCT_COLORS[r.nextInt(PRODUCT_COLORS.length)];
                g.setColor(c);
                g.fillRoundRect(x, by - ph, pw, ph, 4, 4);
                g.setColor(new Color(255, 255, 255, 90));
                g.fillRect(x + 2, by - ph + 3, Math.max(2, pw / 3), ph - 6);
                g.setColor(new Color(255, 255, 255, 170));
                g.fillRect(x + 1, by - ph + ph / 2 - 3, pw - 2, 6);
                x += pw + 2;
            }
            g.setColor(new Color(141, 110, 99));              // shelf board
            g.fillRect(0, by, viewW, 5);
            g.setColor(new Color(0, 0, 0, 35));
            g.fillRect(0, by + 5, viewW, 3);
        }
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(0, 96, viewW, 5);

        // store sign
        int sw = 440;
        int sx = viewW / 2 - sw / 2;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(sx + 4, 24, sw, 52, 18, 18);
        g.setColor(new Color(27, 94, 32));
        g.fillRoundRect(sx, 20, sw, 52, 18, 18);
        g.setColor(new Color(255, 213, 79));
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(sx + 4, 24, sw - 8, 44, 14, 14);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Color.WHITE);
        g.setFont(font(Font.BOLD, 34));
        drawFit(g, "SUPERMARKET", viewW / 2, 59, sw - 40, true);
    }

    /** Free walkways between the lanes (where staff walk), with hanging aisle signs. */
    private void paintAisles(Graphics2D g) {
        if (lanes.size() < 2) {
            return;
        }
        for (int i = 0; i < lanes.size() - 1; i++) {
            int[] c = corridor(i);
            int mid = (c[0] + c[1]) / 2;

            g.setColor(new Color(255, 253, 231, 150));
            g.fillRect(c[0], 108, c[1] - c[0], H - 118);
            g.setColor(new Color(255, 193, 7, 200));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{10f, 8f}, 0f));
            g.drawLine(c[0], 108, c[0], H - 10);
            g.drawLine(c[1], 108, c[1], H - 10);
            g.setStroke(new BasicStroke(1f));

            // hanging sign
            g.setColor(new Color(120, 120, 120));
            g.drawLine(mid - 30, 101, mid - 30, 114);
            g.drawLine(mid + 30, 101, mid + 30, 114);
            g.setColor(new Color(21, 101, 192));
            g.fillRoundRect(mid - 46, 114, 92, 34, 8, 8);
            g.setColor(Color.WHITE);
            g.setFont(font(Font.BOLD, 12));
            drawFit(g, "AISLE " + (i + 1), mid, 128, 84, true);
            g.setFont(font(Font.PLAIN, 11));
            drawFit(g, AISLE_NAMES[i % AISLE_NAMES.length], mid, 142, 84, true);
        }
    }

    private void paintWalkers(Graphics2D g) {
        long now = System.currentTimeMillis();
        for (Walker w : walkers) {
            boolean moving = now >= w.pauseUntil;
            int bob = moving ? (int) Math.round(Math.sin(now / 130.0 + w.x / 20.0) * 2) : 0;
            int cx = (int) Math.round(w.x);
            int cy = (int) Math.round(w.y);
            int top = cy - 29 + bob;

            g.setColor(new Color(0, 0, 0, 40));                          // shadow
            g.fillOval(cx - 18, cy + 27, 36, 10);

            if (w.dir > 0) {
                g.drawImage(w.pic, cx - 24, top, 48, 58, null);
            } else {                                                     // mirrored when walking left
                g.drawImage(w.pic, cx + 24, top, cx - 24, top + 58, 0, 0, Avatars.W, Avatars.H, null);
            }
            drawProp(g, w.job, cx + w.dir * 36, top, w.dir, now);

            g.setFont(font(Font.BOLD, 11));             // name above the head
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(w.name);
            g.setColor(new Color(255, 255, 255, 225));
            g.fillRoundRect(cx - tw / 2 - 5, top - 20, tw + 10, 16, 8, 8);
            g.setColor(new Color(30, 30, 30));
            g.drawString(w.name, cx - tw / 2, top - 8);

            g.setFont(font(Font.PLAIN, 10));            // job below the feet
            g.setColor(new Color(70, 70, 70));
            drawFit(g, STAFF_JOBS[w.job], cx, cy + 44, 140, true);
        }
    }

    private void drawProp(Graphics2D g, int job, int px, int top, int dir, long now) {
        if (job == 0) {                                  // carrying a crate
            g.setColor(new Color(161, 110, 60));
            g.fillRect(px - 11, top + 30, 22, 18);
            g.setColor(new Color(110, 70, 35));
            g.drawRect(px - 11, top + 30, 22, 18);
            g.drawLine(px - 11, top + 39, px + 11, top + 39);
        } else if (job == 1) {                           // pushing a shopping cart
            g.setColor(new Color(200, 200, 205));
            g.fillRect(px - 14, top + 28, 28, 14);
            g.setColor(new Color(90, 90, 100));
            g.drawRect(px - 14, top + 28, 28, 14);
            for (int k = -8; k <= 8; k += 8) {
                g.drawLine(px + k, top + 28, px + k, top + 42);
            }
            g.drawLine(px - dir * 14, top + 28, px - dir * 20, top + 20);   // handle
            g.fillOval(px - 10, top + 44, 7, 7);
            g.fillOval(px + 3, top + 44, 7, 7);
        } else {                                         // mopping the floor
            int swing = (int) Math.round(Math.sin(now / 150.0) * 8);
            g.setColor(new Color(121, 85, 72));
            g.setStroke(new BasicStroke(2f));
            g.drawLine(px, top + 18, px + swing, top + 54);
            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(100, 181, 246));
            g.fillRoundRect(px + swing - 10, top + 52, 20, 7, 4, 4);
        }
    }

    private void paintLane(Graphics2D g, int i) {
        Lane lane = lanes.get(i);
        Color color = LANE_COLORS[i % LANE_COLORS.length];
        int cx = laneX(i);
        int areaX = cx - WAIT_LEFT;
        int areaW = WAIT_LEFT + WAIT_RIGHT;
        int mid = areaX + areaW / 2;          // centre of the lane: counter, cashier and labels line up here

        // waiting area (customer line): same colour in every lane
        g.setColor(new Color(QUEUE_COLOR.getRed(), QUEUE_COLOR.getGreen(), QUEUE_COLOR.getBlue(), 35));
        g.fillRoundRect(areaX, 252, areaW, H - 262, 16, 16);
        g.setColor(new Color(QUEUE_COLOR.getRed(), QUEUE_COLOR.getGreen(), QUEUE_COLOR.getBlue(), 120));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 5f}, 0f));
        g.drawRoundRect(areaX, 252, areaW, H - 262, 16, 16);
        g.setStroke(new BasicStroke(1f));

        // lane label
        g.setColor(color);
        g.fillRoundRect(mid - 38, 112, 76, 20, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(font(Font.BOLD, 12));
        drawFit(g, "LANE " + lane.getNumber(), mid, 127, 70, true);

        // cashier character (idles, hops when it serves) + name
        long now = System.currentTimeMillis();
        long elapsed = now - serveAt[i];
        int yoff = (int) Math.round(Math.sin(now / 300.0 + i * 2) * 1.5);       // idle bobbing
        if (elapsed >= 0 && elapsed < 900) {                                       // just served: hop + shout
            yoff = -(int) Math.abs(10 * Math.sin(elapsed / 900.0 * Math.PI * 2));
            g.setColor(Color.WHITE);
            g.fillRoundRect(mid - 112, 142, 80, 22, 10, 10);
            g.setColor(new Color(60, 60, 60));
            g.drawRoundRect(mid - 112, 142, 80, 22, 10, 10);
            g.setFont(font(Font.BOLD, 11));
            drawFit(g, "Next, please!", mid - 72, 157, 74, true);
        }
        g.drawImage(lane.getCashierPicture(), mid - 20, 138 + yoff, null);
        g.setColor(new Color(30, 30, 30));
        g.setFont(font(Font.BOLD, 12));
        drawFit(g, lane.getCashierName(), mid, 198, 190, true);

        // counter + register
        g.setColor(new Color(121, 85, 72));
        g.fillRoundRect(mid - 70, 204, 140, 24, 8, 8);
        g.setColor(new Color(161, 136, 127));
        g.fillRoundRect(mid - 70, 204, 140, 7, 8, 8);
        g.setColor(new Color(55, 71, 79));                       // register sits ON the counter
        g.fillRoundRect(mid + 36, 208, 28, 16, 4, 4);
        g.setColor(new Color(129, 199, 132));
        g.fillRect(mid + 39, 211, 22, 8);

        // "now serving" line (comes from the lane's real data)
        Customer served = lane.getCurrentlyServed();
        g.setColor(new Color(40, 40, 40));
        g.setFont(font(Font.BOLD, 12));
        String text = served == null ? "Now serving: -" : "Now serving: " + served.getName() + " (" + served.getIdLabel() + ")";
        drawFit(g, text, mid, 244, 200, true);

        if (lane.isEmpty()) {
            g.setColor(new Color(120, 120, 120));
            g.setFont(font(Font.PLAIN, 11));
            drawFit(g, "(empty lane)", mid, FIRST_SLOT_Y + 4, 110, true);
        }
    }

    private void paintDoor(Graphics2D g) {
        g.setColor(new Color(93, 64, 55));
        g.fillRoundRect(8, 588, 94, 58, 8, 8);
        g.setColor(new Color(179, 229, 252));
        g.fillRect(14, 594, 38, 46);
        g.fillRect(58, 594, 38, 46);
        g.setColor(new Color(60, 60, 60));
        g.setFont(font(Font.BOLD, 10));
        drawFit(g, "ENTRANCE", 55, 582, 90, true);
    }

    private void paintCustomer(Graphics2D g, Customer c, Color laneColor, boolean showBadge) {
        int x = (int) Math.round(c.x);
        int y = (int) Math.round(c.y);

        g.drawImage(c.getPicture(), x - 18, y - 21, 36, 42, null);

        if (showBadge) {                         // queue position badge
            g.setColor(laneColor);
            g.fillOval(x - 42, y - 10, 20, 20);
            g.setColor(Color.WHITE);
            g.setFont(font(Font.BOLD, 11));
            drawFit(g, String.valueOf(c.getPosition()), x - 32, y + 4, 16, true);
        }

        g.setColor(new Color(30, 30, 30));
        g.setFont(font(Font.BOLD, 12));
        drawFit(g, c.getName(), x + 24, y - 2, 102, false);
        g.setColor(new Color(90, 90, 90));
        g.setFont(font(Font.PLAIN, 11));
        g.drawString("ID: " + c.getIdLabel(), x + 24, y + 12);
    }

    /** Draws text, shrinking the font if it would be wider than maxWidth. */
    private void drawFit(Graphics2D g, String s, int x, int y, int maxWidth, boolean center) {
        Font original = g.getFont();
        FontMetrics fm = g.getFontMetrics(original);
        int w = fm.stringWidth(s);
        if (w > maxWidth) {
            float size = original.getSize2D() * maxWidth / w;
            g.setFont(original.deriveFont(Math.max(8f, size)));
            fm = g.getFontMetrics();
            w = fm.stringWidth(s);
        }
        g.drawString(s, center ? x - w / 2 : x, y);
        g.setFont(original);
    }
}
