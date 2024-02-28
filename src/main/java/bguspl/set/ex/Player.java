
package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    private final Dealer dealer;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    protected ArrayBlockingQueue<Integer> awaitDealer;
    private ArrayBlockingQueue<Integer> queue;

    public ArrayList<Integer> myTokens;

    public volatile boolean checked;

    public volatile int state;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.queue = new ArrayBlockingQueue<Integer>(3);
        this.awaitDealer = new ArrayBlockingQueue<Integer>(1);
        terminate = false;
        checked = false;
        state = 0;
        myTokens = new ArrayList<Integer>(env.config.featureSize);
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human)
            createArtificialIntelligence();

        while (!terminate) {
            Integer currentToken;
            try {
                System.out.println("Taking a token " + state + " " + queue.size());
                state = 0;
                currentToken = queue.take();
                System.out.println("Player" + id + "Token: " + currentToken);
                if (table.tokens[this.id][currentToken] == true) {
                    table.removeToken(id, currentToken);
                    myTokens.remove(currentToken);
                } else if (myTokens.size() < env.config.featureSize && table.slotToCard[currentToken] != null) {
                    table.placeToken(id, currentToken);
                    myTokens.add(currentToken);
                    if (myTokens.size() == env.config.featureSize)
                        checkDealer();
                }

            } catch (InterruptedException e) {
            }
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");

    }

    private void checkDealer() {

        try {
            dealer.waitingForCheck.put(this.id);
        } catch (InterruptedException e) {
            System.out.println("Got interrupted");
        }

        try {       //make the player to wait
            awaitDealer.take();
        } catch (InterruptedException e) {

        }
        if (state == 1)
            point();
        if (state == -1)
            penalty();
        checked = false;
        state = 0;
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                Random rand = new Random();
                int random = rand.nextInt(env.config.tableSize);
                keyPressed(random);
                try {
                    synchronized (this) {
                        aiThread.sleep(3);
                    }
                } catch (InterruptedException ignored) {
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        playerThread.interrupt();
        try{
            playerThread.join();
        } catch(InterruptedException e){}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * @throws InterruptedException
     */
    public void keyPressed(int slot) {
        if (table.tableIsReady && state == 0) {
            queue.offer(slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     *       remember to sleep the thread
     */
    public void point() {

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        myTokens.clear();
        env.ui.setScore(id, ++score);

        // int ignored = table.countCards(); // this part is just for demonstration in
        // the unit tests
        long sleepTime = env.config.pointFreezeMillis + System.currentTimeMillis();

        while (System.currentTimeMillis() < sleepTime) {
            env.ui.setFreeze(id, sleepTime - System.currentTimeMillis());

            if (sleepTime - System.currentTimeMillis() > 900) {
                try {
                    playerThread.sleep(900);
                } catch (InterruptedException e) {
                }
            }

        }
        env.ui.setFreeze(id, 0);
        state = 0;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        int ignored = table.countCards(); // this part is just for demonstration in
        // the unit tests
        long sleepTime = env.config.penaltyFreezeMillis + System.currentTimeMillis();

        while (System.currentTimeMillis() < sleepTime) {
            env.ui.setFreeze(id, sleepTime - System.currentTimeMillis());

            if (sleepTime - System.currentTimeMillis() > 900) {
                try {
                    playerThread.sleep(900);
                } catch (InterruptedException e) {
                }
            }

        }
        env.ui.setFreeze(id, 0);
        state = 0;

    }

    public int score() {
        return score;
    }

    public BlockingQueue<Integer> getQueue() {
        return this.queue;
    }

    public int getScore() {
        return score;
    }

    public boolean getTerminate() {
        return terminate;
    }

}
