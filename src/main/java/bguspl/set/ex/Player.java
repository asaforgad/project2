package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;

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
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
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

    private ArrayList <Integer> queue;

    private int howManyTokens; 

    

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.queue = new ArrayList<Integer>(3);
        howManyTokens=0;
        terminate = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
    playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // queue.add(1);
            while (!queue.isEmpty()){
                System.out.println("not empty");

            // synchronized (table.getLock(queue.get(0))){
                int slot =queue.remove(0);
                System.out.println("removed from queue");

                if(table.tokens[this.id][slot]==true){
                    table.removeToken(id,slot); 
                    decreaseHowMany();
                    System.out.println("removed");
                }
                else{
                    System.out.println("hello");
                    table.placeToken(id, slot); 
                    increaseHowMany();
                    System.out.println("put  on table");
                // }
                // notifyAll();
            }
        }
        

            while (howManyTokens==3){
            
                dealer.getAnnounced().add(id);
                
                while (!(dealer.getAnnounced().get(0)==id)){
                    try {
                        synchronized (this) { wait(); }
                    } catch (InterruptedException ignored) {}
            }

            notifyAll();
            synchronized(dealer){
                dealer.checkMySet(id, table.tokens[id]);
            
             }
        
    }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    
        }
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                int random = (int) (Math.random() * env.config.tableSize);
                keyPressed(random);
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
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
    }
    

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
            
            if(queue.size()<=3 & this.table.slotToCard[slot] != null){
                System.out.println(slot);
                    queue.add(slot);          
    }
}

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     * remember to sleep the thread
     */
    public void point() {
        
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        
            decreaseHowMany();decreaseHowMany();      
            decreaseHowMany();      
        
        env.ui.setScore(id, ++score);

        env.ui.setFreeze(id,env.config.pointFreezeMillis);
        try {
            // Sleep for the fixed amount of time
            Thread.sleep(env.config.pointFreezeMillis);
        } catch (InterruptedException e) {
            // Thread was interrupted, handle interruption if needed
            System.out.println("Thread was interrupted.");
        }
   
        
    }


    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        try {
            // Sleep for the fixed amount of time
            Thread.sleep(env.config.pointFreezeMillis);
        } catch (InterruptedException e) {
            // Thread was interrupted, handle interruption if needed
            System.out.println("Thread was interrupted.");
        }
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
    }
    

    public int score() {
        return score;
    }

    public ArrayList <Integer> getQueue(){
        return this.queue;
    }

    public int getScore(){
        return score;
    }
    public void decreaseHowMany(){
        howManyTokens--;
    }
    public void increaseHowMany(){
        howManyTokens++;
    }
    
}
