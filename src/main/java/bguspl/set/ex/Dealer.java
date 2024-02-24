package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UserInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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
    protected volatile ArrayBlockingQueue <Integer> waitingForCheck;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.reshuffleTime= System.currentTimeMillis()+env.config.turnTimeoutMillis;
        this.tokensToRemove = new ArrayList <Integer>(3);
        this.waitingForCheck = new ArrayBlockingQueue <>(players.length);

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

        synchronized(table){
            table.tableIsReady(false);
        
            while(!tokensToRemove.isEmpty()){
                int slot = tokensToRemove.remove(0);

                for (Player p : players){
                        p.getQueue().remove(slot);

                        if (table.tokens[slot][p.id] == true)
                            p.myTokens.remove(slot);
                    }

                 table.removeCard(slot); 

                }
            
        }
    }


    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {

        synchronized(table){
            Collections.shuffle(deck);
            for(int i=0; i<table.slotToCard.length & !deck.isEmpty(); i++){
                if(table.slotToCard[i] == null){
                    Integer card = deck.get(0);
                    deck.remove(0);
                    table.placeCard(card, i);       
                }     
            }
            table.tableIsReady(true);

        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */

     
    private void sleepUntilWokenOrTimeout() {

        synchronized (this){
        if (waitingForCheck.isEmpty()){
        try {
            this.wait(700);
        } catch (InterruptedException e) {
            System.out.println("Thread was interrupted.");
        }}}
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
        synchronized(table){
        checkSets();
        table.tableIsReady(false);
        for(int i = 0 ; i< env.config.tableSize; i++){
            deck.add(table.slotToCard[i]);
            table.removeCard(i);
            }
            tokensToRemove.clear();

            for(Player p :players){
                p.myTokens.clear();
                p.getQueue().clear();
            }
        }
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

    public Player findPlayer (int i){

        return players[i];
    }
    public boolean checkSets(){

        boolean setExist;

        synchronized(this){

            int claimerId = waitingForCheck.poll();
            ArrayList<Integer> firstSet = players[claimerId].myTokens;

            setExist = isSet(firstSet);
            Player claimer = findPlayer(claimerId);

            if(setExist){
                printSet(firstSet);
                System.out.println("this is a set");
                tokensToRemove = firstSet;
                removeSetsContainSameValue(firstSet);
                claimer.point(); 
                updateTimerDisplay(true);
                removeCardsFromTable();
                placeCardsOnTable();
                
            }
            else{
                claimer.penalty();                
            }
            
            claimer.checked = true;
            this.notifyAll();
            }
        return setExist;
    } 

    public void removeSetsContainSameValue(ArrayList<Integer> firstSet){
            int third = firstSet.get(0);
            int second = firstSet.get(1);
            int first = firstSet.get(2);
        
        for (Integer Id : waitingForCheck){
            if (players[Id].myTokens.contains(third)||
            players[Id].myTokens.contains(second)||
            players[Id].myTokens.contains(first)){
                printSet(players[Id].myTokens);
                waitingForCheck.remove(Id);
                System.out.println("removed");
                continue;
            }
        }
    }


    public boolean isSet(ArrayList<Integer> mySet){

        int [] cardToCheck= new int[3];
        cardToCheck[0] = mySet.remove(0);
        cardToCheck[1] = mySet.remove(0);
        cardToCheck[2] = mySet.remove(0);

        return env.util.testSet(cardToCheck);
    }



    public void printSet(ArrayList<Integer> mySet){

        System.out.println("your set:");
        System.out.println(mySet.remove(0));
        System.out.println(mySet.remove(0));
        System.out.println(mySet.remove(0));
        System.out.println("that was your set!");

    }

    }
 


