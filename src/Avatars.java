import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Draws small cartoon characters with Java2D, so the project needs no image files.
 */
public final class Avatars {

    public static final int W = 40;
    public static final int H = 48;

    private static final Color[] SKIN = {
        new Color(255, 224, 189), new Color(241, 194, 125), new Color(224, 172, 105),
        new Color(198, 134, 66), new Color(141, 85, 36)
    };
    private static final Color[] HAIR = {
        new Color(40, 30, 20), new Color(90, 60, 30), new Color(20, 20, 20),
        new Color(160, 110, 50), new Color(190, 60, 40), new Color(120, 120, 120)
    };
    private static final Color[] SHIRT = {
        new Color(66, 133, 244), new Color(52, 168, 83), new Color(251, 188, 5),
        new Color(171, 71, 188), new Color(0, 172, 193), new Color(255, 112, 67),
        new Color(121, 134, 203), new Color(240, 98, 146)
    };

    private Avatars() { }

    /** A shopper with a random look. */
    public static BufferedImage createCustomer(long seed) {
        return draw(seed, false, null);
    }

    /** A cashier / staff member wearing a coloured vest and cap. */
    public static BufferedImage createStaff(long seed, Color vest) {
        return draw(seed, true, vest);
    }

    private static BufferedImage draw(long seed, boolean staff, Color vest) {
        Random r = new Random(seed);
        Color skin = SKIN[r.nextInt(SKIN.length)];
        Color hair = HAIR[r.nextInt(HAIR.length)];
        Color shirt = staff ? vest : SHIRT[r.nextInt(SHIRT.length)];
        int style = r.nextInt(3);

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // body
        g.setColor(shirt);
        g.fillRoundRect(6, 28, 28, 20, 12, 12);
        if (staff) {                                   // white collar + name badge
            g.setColor(Color.WHITE);
            g.fillRect(17, 28, 6, 8);
            g.fillRoundRect(23, 36, 8, 5, 2, 2);
        }

        // head
        g.setColor(skin);
        g.fillOval(10, 4, 20, 22);

        // hair
        g.setColor(hair);
        g.fillArc(9, 1, 22, 18, 0, 180);
        if (style == 1) {                              // long hair
            g.fillRect(9, 9, 4, 15);
            g.fillRect(27, 9, 4, 15);
        } else if (style == 2) {                       // side fringe
            g.fillOval(9, 6, 10, 8);
        }

        // cap for staff
        if (staff) {
            g.setColor(shirt.darker());
            g.fillArc(8, 0, 24, 18, 0, 180);
            g.fillRoundRect(8, 8, 26, 4, 4, 4);
        }

        // face
        g.setColor(Color.BLACK);
        g.fillOval(15, 14, 3, 3);
        g.fillOval(23, 14, 3, 3);
        g.setStroke(new BasicStroke(1.5f));
        g.drawArc(15, 15, 10, 7, 200, 140);

        g.dispose();
        return img;
    }
}
