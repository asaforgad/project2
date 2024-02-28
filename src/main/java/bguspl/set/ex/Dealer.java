package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UserInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
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
    private ArrayList<Integer> tokensToRemove;
    protected volatile ArrayBlockingQueue<Integer> waitingForCheck;
    long lastReset;
    private Integer claimerId;
    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        this.tokensToRemove = new ArrayList<Integer>(env.config.featureSize);
        this.waitingForCheck = new ArrayBlockingQueue<>(players.length);
        lastReset = System.currentTimeMillis();

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player p : players) {
            new Thread(p, "Player" + p.id).start();
        }
        updateTimerDisplay(true);
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        terminate();
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            updateTimerDisplay(false);
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            checkSets();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        for (int i = players.length-1; i >= 0; i--) {
            players[i].terminate();
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return (terminate || (env.util.findSets(deck, 1).size() == 0));
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // if a player gets a set

        synchronized (table) {
            table.tableIsReady(false);

            while (!tokensToRemove.isEmpty()) {
                Integer slot = tokensToRemove.remove(0);

                for (Player p : players) {
                    p.getQueue().remove(slot);

                    if (table.tokens[p.id][slot] == true)
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

        synchronized (table) {
            Collections.shuffle(deck);
            for (int i = 0; i < table.slotToCard.length & !deck.isEmpty(); i++) {
                if (table.slotToCard[i] == null) {
                    Integer card = deck.get(0);
                    deck.remove(0);
                    table.placeCard(card, i);
                }

            }

            table.tableIsReady(true);

        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */

    private void sleepUntilWokenOrTimeout() {

        synchronized (this) {
            try {
                if (System.currentTimeMillis() / 1000 == lastReset / 1000){
                    claimerId = waitingForCheck.poll(Math.abs(900 - lastReset % 1000), TimeUnit.MILLISECONDS);
                } else {
                    claimerId = waitingForCheck.poll();
                }
            } catch (InterruptedException e) {
                System.out.println("Thread was interrupted.");
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {

        if (env.config.turnTimeoutMillis < 0)
            return;
        else if (env.config.turnTimeoutMillis == 0) {
            long elapsedTime = System.currentTimeMillis() - lastReset;
            env.ui.setElapsed(elapsedTime);
        } else {
            if (!reset) {
                // long elapsedTime = System.currentTimeMillis() - lastReset;
                boolean warn = false;
                if (reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis) {
                    warn = true;
                }
                env.ui.setCountdown((reshuffleTime - System.currentTimeMillis()), warn);
            } else {
                lastReset = System.currentTimeMillis();
                reshuffleTime = lastReset + env.config.turnTimeoutMillis;
                env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized (table) {
            table.tableIsReady(false);
            for (int i = 0; i < env.config.tableSize && table.slotToCard[i] != null; i++) {
                deck.add(table.slotToCard[i]);
                table.removeCard(i);
            }
            //clean the players requests and wake them
            tokensToRemove.clear();
            for(Integer id: waitingForCheck){
                try {
                    players[id].awaitDealer.put(0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            waitingForCheck.clear();


            for (Player p : players) {
                p.myTokens.clear();
                p.checked = true;
                p.state = 0;
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = 0;
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() >= maxScore) {
                maxScore = players[i].getScore();
            }
        }
        int counter = 0;
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == maxScore) {
                counter++;
            }
        }
        int[] winners = new int[counter];
        counter = 0;
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == maxScore) {
                winners[counter] = i;
                counter++;
            }
        }
        env.ui.announceWinner(winners);
        
    }

    public void checkSets() {
        synchronized (this) {
            if (claimerId != null) {
                Player claimer = players[claimerId];
                ArrayList<Integer> firstSet = new ArrayList<Integer>(claimer.myTokens);

                if(firstSet.size() < env.config.featureSize){  //check set'svalidility
                    claimer.state =0;
                    claimer.checked = true;
                    try{
                        claimer.awaitDealer.put(0);
                    } catch(InterruptedException ignored){}
                    return;
                }
                if (isSet(firstSet)) {  //if legal set
                    tokensToRemove = firstSet;
                    removeSetsContainSameValue(firstSet);
                    claimer.state = 1;
                    removeCardsFromTable();
                    placeCardsOnTable();
                    updateTimerDisplay(true);
                } else {
                    claimer.state = -1;
                }
                claimer.checked = true;
                    try {           //notify the player
                        claimer.awaitDealer.put(0);
                    } catch (InterruptedException e) {
                    }
            }
        }

    }

    public void removeSetsContainSameValue(ArrayList<Integer> firstSet) {
        int third = firstSet.get(0);
        int second = firstSet.get(1);
        int first = firstSet.get(2);

        for (Integer Id : waitingForCheck) {
            if (players[Id].myTokens.contains(third) ||
                    players[Id].myTokens.contains(second) ||
                    players[Id].myTokens.contains(first)) {

                waitingForCheck.remove(Id);
                players[Id].checked = true;
                try {
                    players[Id].awaitDealer.put(0);
                } catch (InterruptedException e) {}
                ;
                continue;
            }
        }
    }

    public boolean isSet(ArrayList<Integer> mySet) {
        synchronized (table) {
            int[] cardToCheck = new int[3];
            boolean allGood = true;

            if (table.slotToCard[mySet.get(0)] == null ||
                    table.slotToCard[mySet.get(1)] == null ||
                    table.slotToCard[mySet.get(2)] == null) {
                allGood = false;
            }

            if (allGood) {
                cardToCheck[0] = table.slotToCard[mySet.get(0)];
                cardToCheck[1] = table.slotToCard[mySet.get(1)];
                cardToCheck[2] = table.slotToCard[mySet.get(2)];

                return env.util.testSet(cardToCheck);
            }
        }
        return false;
    }



}
