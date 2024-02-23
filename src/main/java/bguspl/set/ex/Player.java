package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
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

    private ArrayBlockingQueue <Integer> queue;

    public ArrayBlockingQueue <Integer> myTokens; 

    public volatile boolean checked;


    

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
        this.queue =  new ArrayBlockingQueue<Integer>(env.config.featureSize);
        terminate = false;
        checked = false;
        myTokens = new ArrayBlockingQueue<Integer>(3);
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
    playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        try { while (!terminate) {
            while (!queue.isEmpty()){
                // System.out.println("not empty");
                int currentToken =queue.take();
                // System.out.println("removed from queue");

                if(table.tokens[this.id][currentToken]==true){
                    table.removeToken(id,currentToken); 
                    myTokens.remove(currentToken);
                    // System.out.println("removed");
                }
                else{
                    table.placeToken(id, currentToken); 
                    myTokens.add(currentToken);
                    if (myTokens.size()==3)
                        checkDealer();
                    // System.out.println("put on table");
                }

            }
        }
            
        } catch (InterruptedException e) {}

        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    
        }
    


    private void checkDealer() throws InterruptedException{ 

        synchronized(dealer){
            addIdToFirstSpot();
            dealer.waitingForCheck.offer(myTokens);
            dealer.notifyAll();
            dealer.checkSets();
        }

        synchronized(this){
            while(!checked){
                playerThread.wait();
            }
            checked = false;
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

                try {
                    keyPressed(random);
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
    public synchronized void terminate() {
        terminate = true;
        playerThread.interrupt();
    }
    

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * @throws InterruptedException 
     */
    public void keyPressed(int slot) {
            
            if(queue.size()<=3 && table.tableIsReady){
                System.out.println("the number "+ slot+ " slot was pressed");
                    queue.offer(slot);          
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
        myTokens.clear();
        env.ui.setScore(id, ++score);
        long time = this.env.config.pointFreezeMillis;
        int loops = (int) this.env.config.pointFreezeMillis / 1000;
        int div = loops;
        while (loops >= 0) {
            this.env.ui.setFreeze(id, time);//turn the color of the player to red
            if (loops > 0) {
                try {
                
                    Thread.sleep(this.env.config.pointFreezeMillis / div);//put the player thread to sleep for 1 sec
                } catch (InterruptedException e) {}
            }
            time = time - 1000;
            loops--;
        }

    }



    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests

            long time = this.env.config.penaltyFreezeMillis;
      
            int loops = (int) this.env.config.penaltyFreezeMillis / 1000;
            int div = loops;
            while (loops >= 0) {
                this.env.ui.setFreeze(id, time);//turn the color of the player to red
                if (loops > 0) {
                    try {
                        Thread.sleep(this.env.config.penaltyFreezeMillis / div);//put the player thread to sleep for 1 sec
                    } catch (InterruptedException e) {}
                }
                time = time - 1000;
                loops--;
              }
    }
    

    public int score() {
        return score;
    }
    void addIdToFirstSpot(){
        Integer f = myTokens.poll();
        Integer s = myTokens.poll();
        Integer t = myTokens.poll();
        myTokens.offer(id);
        myTokens.offer(f);
        myTokens.offer(s);
        myTokens.offer(t);
    }


    public BlockingQueue <Integer> getQueue(){
        return this.queue;
    }

    public int getScore(){
        return score;
    }
    
}
