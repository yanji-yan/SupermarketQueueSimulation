import java.awt.image.BufferedImage;

/**
 * A customer waiting in (or leaving) a cashier lane.
 * Required fields from the guidelines: customer ID, name, picture, queue position.
 */
public class Customer {

    private final int id;
    private final String name;
    private final BufferedImage picture;
    // Set by Lane.refreshPositions() (EXPLAIN 4), not by the customer. 1 = front of the lane.
    private int position;

    // Animation state (screen coordinates) - used only by StorePanel.
    double x, y;               // where the customer is drawn right now
    double targetX, targetY;   // where the customer is walking to
    int laneNumber;            // 1-based lane number (used for colours)
    boolean leaving;           // true once the customer was removed with poll()
    int leavePhase;            // 0 = walking to the counter, 1 = walking away

    public Customer(int id, String name, BufferedImage picture) {
        this.id = id;
        this.name = name;
        this.picture = picture;
    }

    public int getId() { return id; }

    public String getIdLabel() { return "C" + id; }

    public String getName() { return name; }

    public BufferedImage getPicture() { return picture; }

    public int getPosition() { return position; }

    public void setPosition(int position) { this.position = position; }

    @Override
    public String toString() {
        return getIdLabel() + " " + name;
    }
}
