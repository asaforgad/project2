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

    public ArrayList <Integer> myTokens; 

    public volatile boolean checked;

    public volatile int state;


    

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
        this.queue =  new ArrayBlockingQueue<Integer>(3);
        terminate = false;
        checked = false;
        state = 0;
        myTokens = new ArrayList<Integer>(3);
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
                System.out.println("not empty");
                Integer currentToken =queue.take();
                System.out.println("queue take");

                if(table.tokens[this.id][currentToken]==true){
                    table.removeToken(id,currentToken); 
                    myTokens.remove(currentToken);
                    System.out.println("My token.removed");
                    System.out.println("my tokens size: " +myTokens.size() );
                }
                else if (myTokens.size() < 3){   
                    table.placeToken(id, currentToken); 
                    myTokens.add(currentToken);
                    System.out.println("my tokens size: " +myTokens.size() );
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
            
            dealer.waitingForCheck.add(this.id);
            
        // dealer.notifyAll();
            dealer.checkSets();
        }

        synchronized(this){
            while(!checked){
                playerThread.wait();
            }
            if (state == 1)
                point();
            if (state == -1)
               penalty();
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
    public void terminate() {
        terminate = true;
        playerThread.interrupt();
    }
    

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * @throws InterruptedException 
     */
    public synchronized void keyPressed(int slot) {
            
            if(queue.size()<=3 && table.tableIsReady && state==0){
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
    public synchronized void point() {

        System.out.println("point");
        
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests  
        myToken`s.clear();
        env.ui.setScore(id, ++score);

        long sleepTime = env.config.pointFreezeMillis;
        while(sleepTime > 1000){
            env.ui.setFreeze(id, sleepTime);
        try{playerThread.sleep(1000);} catch(InterruptedException e){}
        sleepTime = sleepTime -1000;}
        env.ui.setFreeze(id, 0);
        state = 0;

        // long timeLeft= env.config.pointFreezeMillis;
        // int loops = (int)timeLeft/1000;
        // int constLoops = loops;

        // while(loops>0){
        //     env.ui.setFreeze(id, timeLeft);
        //     if(loops!=0){
        // try {
        //     // Sleep for the fixed amount of time
        //     Thread.sleep(env.config.pointFreezeMillis/constLoops);
        // } catch (InterruptedException e) {
        //  }
    //   }
    // timeLeft= timeLeft-1000;
    // loops--;
    // }
    }


    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized void penalty() {
        System.out.println("penalty");
        // int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        long sleepTime = env.config.penaltyFreezeMillis;
        while(sleepTime > 1000){
            env.ui.setFreeze(id, sleepTime);
        try{playerThread.sleep(1000);} catch(InterruptedException e){}
        sleepTime = sleepTime -1000;}
        env.ui.setFreeze(id, 0);
        state = 0;
        

    //     long timeLeft= env.config.penaltyFreezeMillis;
    //     int loops = (int)timeLeft/1000;
    //     int constLoops = loops;

    //     while(loops>0){
    //         env.ui.setFreeze(id, timeLeft);
    //         if(loops!=0){
    //         try {
    //             // Sleep for the fixed amount of time
    //             Thread.sleep(env.config.penaltyFreezeMillis/constLoops);
    //         } catch (InterruptedException e) {
    //         }
    // }
    // timeLeft= timeLeft-1000;
    // loops--;
    // }

    }
    

    public int score() {
        return score;
    }

    public BlockingQueue <Integer> getQueue(){
        return this.queue;
    }

    public int getScore(){
        return score;
    }
    
}
