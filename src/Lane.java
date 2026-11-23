import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * One cashier lane. THE QUEUE LIVES IN THIS CLASS.
 * FIFO = First In, First Out: the first customer who joins is the first one served.
 *
 *     FRONT (served first)                      BACK (newest customer)
 *     poll() removes here                       offer() adds here
 *           |                                          |
 *           v                                          v
 *        [ Ana ]  <-  [ Ben ]  <-  [ Cy ]  <-  [ Dee ]
 *       position 1   position 2   position 3   position 4
 *
 * 3 lanes = 3 separate queues. Search "EXPLAIN" to find the important parts in presentation order.
 */
public class Lane {

    /** Maximum customers a lane can hold (so everyone still fits on screen). */
    public static final int MAX_CAPACITY = 8;

    private final int number;                 // 1, 2, 3 ...
    private final String cashierName;
    private final BufferedImage cashierPicture;

    // EXPLAIN 1 of 9: THE QUEUE (data structure)
    // Queue<Customer> = a line of Customer objects. LinkedList is the class that stores them
    // and follows the Queue rules (FIFO). Every Lane has its own queue.
    private final Queue<Customer> queue = new LinkedList<>();

    private int servedCount;
    private Customer currentlyServed;         // last customer removed with poll()

    public Lane(int number, String cashierName, BufferedImage cashierPicture) {
        this.number = number;
        this.cashierName = cashierName;
        this.cashierPicture = cashierPicture;
    }

    /** Adds a customer to the end of the queue using offer(). */
    public boolean addCustomer(Customer c) {
        // A full lane (8 customers) refuses new customers: nobody is added and we return false.
        if (isFull()) {
            return false;
        }
        c.laneNumber = number;
        // EXPLAIN 2 of 9: ENQUEUE - offer() adds the customer at the BACK of the line.
        // Nobody can cut in front. Then refreshPositions() renumbers everyone (EXPLAIN 4).
        boolean added = queue.offer(c);
        refreshPositions();
        return added;
    }

    /** Serves the first customer using poll(). An empty lane never throws an error. */
    public Customer serve() {
        // EXPLAIN 3 of 9: DEQUEUE - poll() removes and returns the FRONT customer (the one who waited longest).
        // On an empty queue poll() returns null instead of crashing, so we check for null.
        Customer c = queue.poll();
        if (c == null) {
            return null;
        }
        c.setPosition(0);
        currentlyServed = c;
        servedCount++;
        refreshPositions();
        return c;
    }

    /** Renumbers everybody still waiting: front = 1, next = 2, ... */
    private void refreshPositions() {
        // EXPLAIN 4 of 9: QUEUE POSITIONS
        // A for-each loop over a Queue goes from FRONT to BACK, so the front customer gets 1, the next 2, ...
        // This runs after every offer() and poll(): when the front customer leaves, everyone behind moves up one.
        int pos = 1;
        for (Customer c : queue) {
            c.setPosition(pos++);
        }
    }

    /** Called when the last customer has left the counter and nobody else is waiting. */
    public void clearCurrentlyServed() {
        currentlyServed = null;
    }

    public void reset() {
        queue.clear();
        servedCount = 0;
        currentlyServed = null;
    }

    // EXPLAIN 5 of 9: CHECKING THE QUEUE
    // isEmpty() = nobody waiting?   size() = how many are waiting?   isFull() = 8 waiting.
    // The statistics and the "lane is full" message use these.
    public boolean isEmpty() { return queue.isEmpty(); }

    public boolean isFull() { return queue.size() >= MAX_CAPACITY; }

    public int getWaitingCount() { return queue.size(); }

    public int getServedCount() { return servedCount; }

    public Customer getCurrentlyServed() { return currentlyServed; }

    /** A copy of the queue contents in FIFO order (front first). */
    public List<Customer> getCustomers() { return new ArrayList<>(queue); }

    public int getNumber() { return number; }

    public String getCashierName() { return cashierName; }

    public BufferedImage getCashierPicture() { return cashierPicture; }
}
