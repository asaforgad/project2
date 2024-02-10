package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
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

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }
    

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
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
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        //if a player gets a set




        // TODO implement
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
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
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for(int i = 0 ; i<table.cardToSlot.length; i++){
            deck.add(table.slotToCard[i]);
            table.removeCard(i);
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

    private boolean isSet(ArrayList<Integer> tokensList){
        int first = tokensList.get(0);
        int second = tokensList.get(1);
        int third = tokensList.get(2);

        





        }

    public boolean comparColors(int first, int second, int third){

        if(first.)

    }  

    }
}
