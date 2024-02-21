package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UserInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;
    private ArrayList <Integer> tokensToRemove;
    Player claimer;
    private ArrayList <Integer> announced;
    protected ArrayList<ArrayList<Integer>> waitingForCheck;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        claimer = null;
        reshuffleTime= System.currentTimeMillis()+env.config.turnTimeoutMillis;
        tokensToRemove = new ArrayList<Integer>(3);

    }
    

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for(Player p : players){
            new Thread(p,"Player"+p.id).start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        for (Player player : players){
            player.terminate();
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || (env.util.findSets(deck, 1).size() == 0);
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        //if a player gets a set
        
            while(!tokensToRemove.isEmpty()){
                int slot = tokensToRemove.remove(0);

                for (Player p : players){
                        p.getQueue().remove(slot);
                        if (table.tokens[slot][p.id] == true)
                            p.myTokens.remove(slot);
                    }

                 table.removeCard(slot); 

                }
            
            cleanTokensToRemove();
            
        }


    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Collections.shuffle(deck);
        for(int i=0; i<table.slotToCard.length & !deck.isEmpty(); i++){
            if(table.slotToCard[i] == null){
                Integer card = deck.get(0);
                deck.remove(0);
                table.placeCard(card, i);       
            }     
        }
        
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {

        try {
            // Sleep for the fixed amount of time
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Thread was interrupted, handle interruption if needed
            System.out.println("Thread was interrupted.");
        }
        // env.ui.setElapsed(1000);
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(!reset){
            boolean warn = false;
            if((reshuffleTime-System.currentTimeMillis()) < env.config.turnTimeoutWarningMillis){
                warn = true;
            }
            env.ui.setCountdown((reshuffleTime-System.currentTimeMillis()), warn);
        }
        else{
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for(int i = 0 ; i<table.cardToSlot.length; i++){
            deck.add(table.slotToCard[i]);
            table.removeCard(i);
            }
        
            cleanTokensToRemove();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        ArrayList <Integer> winners = new ArrayList<>(players.length);
        int maxScore = 0;
        for(int i=0; i<players.length; i++){
            if(players[i].score() <= maxScore){
                maxScore = players[i].getScore();
                winners.add(players[i].id);
            }
        }
        System.out.println("Winners are: ");
        for(int i = 0; i < winners.size(); i++){
            System.out.println(winners.get(i));
        }
    }

    public boolean checkMySet(int claimerId, boolean[] playerTokens){

        ArrayList<Integer> firstSet;
        boolean setExist;

        synchronized(waitingForCheck){
            firstSet = waitingForCheck.remove(0);
            Player claimer = players[claimerId];
            setExist = isSet(claimerId, firstSet);
            if(setExist){
                tokensToRemove = firstSet;
                claimer.point(); 
                removeCardsFromTable();
                placeCardsOnTable();
            }
            else{
                claimer.penalty();
            }
            claimer.checked = true;
            // claimer.notifyAll();
            }
        return setExist;
    }


    public boolean isSet(int claimerId, ArrayList<Integer> mySet){
        int first[] = extractFeatures(table.slotToCard[mySet.get(0)]);
        int second[] = extractFeatures(table.slotToCard[mySet.get(1)]);
        int third[] = extractFeatures(table.slotToCard[mySet.get(2)]);
        for(Player player : players){
            if(claimerId == player.id){
                claimer = player;
            }
        }
        boolean compareFeatures = compareFeatures(first, second, third);
        return compareFeatures;
    }

    private static int[] extractFeatures(Integer card) {
        int[] features = new int[4];
        for (int i = 0; i < 4; i++) {
            features[i] = (card / (int) Math.pow(3, i)) % 3;
        }
        return features;
    }

    public static boolean compareFeatures(int[] card1, int[] card2, int[] card3) {
        for (int i = 0; i < 4; i++) {
            int feature1 = card1[i];
            int feature2 = card2[i];
            int feature3 = card3[i];

            // Check if values are either all the same or all different
            if (!((feature1 == feature2 && feature2 == feature3) || (feature1 != feature2 && feature2 != feature3 && feature1 != feature3))) {
                return false; // Not a set
            }
        }
        return true; // Form a set
    }

    public ArrayList<Integer> getAnnounced ()
    {return announced;}

    public void cleanTokensToRemove(){
        while(!tokensToRemove.isEmpty()){
            tokensToRemove.remove(0);
        }

    }

    }
 


