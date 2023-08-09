package bguspl.set.ex;

import bguspl.set.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
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
    private final int numOfToken;
    private final int tableSize;
    /**
     * Game entities.
     */
    private final Table table;
    public final Player[] players;

    private final Thread[] playerThreads;


    public BlockingQueue<Player> order;
    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;
    public boolean busy;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        order = new LinkedBlockingQueue<>();
        playerThreads = new Thread[players.length];
        terminate = false;
        this.tableSize = env.config.tableSize;
        this.numOfToken = env.config.featureSize;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {

        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        //create players threads
        for (int i =0; i<players.length; i++){
            Thread playerThread = new Thread(players[i], "player"+i);
            playerThread.start();
            playerThreads[i] = playerThread;
        }
        order = new LinkedBlockingQueue<>();
        while (!shouldFinish()) {
            Collections.shuffle(deck);
            placeCardsOnTable();
            reshuffleTime = System.currentTimeMillis()+ env.config.turnTimeoutMillis;
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
            

        }
        if (terminate)
            removeAllCardsFromTableFinel();
        announceWinners();
        terminate();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());

    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            checkIfSet();
            updateTimerDisplay(false);
            placeCardsOnTable();
            if(env.config.hints)table.hints();

        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        // System.out.println("dealer is killing");
        for(int i=players.length-1; i>=0; i--){
            players[i].terminate();
            try {
                playerThreads[i].interrupt();
                playerThreads[i].join();
            }
            catch (InterruptedException e){}
        }
        terminate = true;

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
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable() {
        // TODO implement

    }

    public void addToOrder(Player player){


            if (player.myTokens.size()==numOfToken) {
                //TODO:System.out.println("player" +player.id+": going into order que"+Arrays.toString(player.myTokens.toArray()));
                synchronized (order) {
                    order.add(player);
                }

            }


    }

/*    public synchronized void removeFromOrder(Player player){
        order.add(player);
        checkIfSet();
    }*/
    /**player asks dealer to check**/
    public void checkIfSet() {
        if(order.size()!=0) {
            Player player = order.remove();
            //TODO:System.out.println("player " + player.id + ": check my set" + "  " + Arrays.toString(player.myTokens.toArray()));
            player.waitForCards =false;

            if (player.myTokens.size() == numOfToken) {
                Integer[] cards = new Integer[numOfToken];
                int[] cardsInt = new int[numOfToken];

                player.myTokens.toArray(cards);
                for (int i = 0; i < numOfToken; i++) {
                    int cardID = table.slotToCard[cards[i]];
                    cardsInt[i] = cardID;
                }
                if (env.util.testSet(cardsInt)) {//sync
                    //TODO:System.out.println("player " + player.id + ": my set is correct" + "  " + Arrays.toString(player.myTokens.toArray()));
                    foundSet(player);
                } else {
                    if (player.myTokens.size() == numOfToken) {
                        //TODO:System.out.println("\033[31mplayer " + player.id + ": I get a penalty on" + "  " + Arrays.toString(player.myTokens.toArray()) + "\033[0m");
                        player.penalty();
                    }
                }
            }
            synchronized(player) {
                player.notify();
            }
        }
        

    }





    /**
     * once a set is found
     * cards should be removed
     * a point shold be granted
     * and tokens also should be off the set
     * @param player
     */
    private void foundSet(Player player){

        Integer[] to = new Integer[numOfToken];
        int[] toInt = new int[numOfToken];

        player.myTokens.toArray(to);
        for (int i = 0; i < numOfToken; i++) {
            toInt[i] = to[i];
        }

        for (int j =0; j<players.length; j++){
            for (int i =0; i<numOfToken; i++) {
                    if (players[j].myTokens.contains(toInt[i]) && !players[j].myTokens.isEmpty()&& !player.equals(players[j])) {
                        table.removeToken(players[j].id, toInt[i]);
                        players[j].myTokens.remove(toInt[i]);
                        //TODO:System.out.println("player " + players[j].id + ": one of my tokens removed,  " + players[j].myTokens.size() + " tokens left");
                    }
            }
        }
        removeSetFromTable(toInt, player);
        //TODO:System.out.println("player "+ player.id +": i get a point:)");
        env.ui.setFreeze(player.id, env.config.pointFreezeMillis);

        player.point();
    }
    private boolean removeSetFromTable (int [] playersToken,Player player){
        player.waitForCards=true;
        synchronized (table) {

            if(player.myTokens.size()==numOfToken) {
                //TODO:System.out.println("player "+ player.id +": my set is being removed");
                for (int i = 0; i < numOfToken; i++) {
                    table.removeCard(playersToken[i]);
                }
                for (int i = 0; i < player.myTokens.size(); i++) {
                    env.ui.removeTokens(playersToken[i]);
                }
                placeCardsOnTable();
                updateTimerDisplay(true);
                //checkSetOnTable();
            }
            player.waitForCards=false;

        }
        //TODO:if(player.myTokens.size()!=numOfToken)
            //TODO:System.out.println("\033[31mplayer "+ player.id +": my set is -not- being removed\033[0m");

        return player.myTokens.size()==numOfToken;
    }
     private void checkSetOnTable(){

         LinkedList<Integer> cardsOnTable = new LinkedList<>();
         for (int i = 0; i < table.slotToCard.length; i++) {
             if (table.slotToCard[i] != null)
                 cardsOnTable.add(table.slotToCard[i]);

         }

         if ((env.util.findSets(cardsOnTable, 1)).size() ==0) {
             //removeAllCardsFromTable();
             if (shouldFinish()) {
                 terminate();
             } else {
                 removeAllCardsFromTable();///no set so replace
                 placeCardsOnTable();
             }
             //removeAllCardsFromTable();///no set so replace
         }

     }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement

        synchronized (table) {
            for (int i = 0; i <tableSize; i++) {//added deck size
                if (table.slotToCard[i] == null && deck.size()>=1)
                    table.placeCard(this.deck.remove(0), i);
            }
            //checkSetOnTable();
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    public void sleepUntilWokenOrTimeout() {
        // TODO implement
/*
        synchronized (this) {
*/
            try {
                Thread.sleep(10);

            } catch (InterruptedException e) {
                //TODO:System.out.println("\033[31mnot sleeping\033[0m");

            }
        //}

    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset){
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            reshuffleTime = System.currentTimeMillis() +env.config.turnTimeoutMillis;
        }
        else if (reshuffleTime - System.currentTimeMillis()< 10000){
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
        }
        else
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {//sleep
        // TODO implement
        synchronized (table) {
            //table.removeAllTokens();
            for (int i =0; i<players.length; i++){
                players[i].waitForCards = true;
                players[i].actions.clear();
            }
            for (int i =0; i<players.length; i++){
              
                while(!players[i].myTokens.isEmpty()){
                    //TODO:System.out.println("playe"+players[i].id+": token "+players[i].myTokens+" is being removed for table clean");
                    //TODO:System.out.println("playe"+players[i].id+": token "+players[i].myTokens.peek()+" is being removed for table clean");
                    table.removeToken(players[i].id, players[i].myTokens.remove());
                    
                }
                if (players[i].myTokens.size()!=0)
                    players[i].myTokens.clear();
                if (players[i].actions.size()!=0)
                    players[i].actions.clear();

                
                    
            }
            for (int i = 0; i < tableSize; i++) {
                if (table.slotToCard[i] != null) {
                    //return to deck
                    int cardToReturn = table.slotToCard[i];
                    table.removeCard(i);
                    deck.add(cardToReturn);
                }
            }


            placeCardsOnTable();
            for (int i =0; i<players.length; i++){
            if (players[i].myTokens.size()!=0)
                players[i].myTokens.clear();
            if (players[i].actions.size()!=0)
                players[i].actions.clear();

                players[i].waitForCards = false;

            }
            
        }
    }
    private void removeAllCardsFromTableFinel() {//sleep
        // TODO implement
        synchronized (table) {
            for (int i =0; i<players.length; i++){
                while(!players[i].myTokens.isEmpty())
                    table.removeToken(players[i].id, players[i].myTokens.remove());
                if (players[i].actions.size()!=0)
                    players[i].actions.clear();
                
            }

            for (int i = 0; i < tableSize; i++) {
                if (table.slotToCard[i] != null) {
                    System.out.println("removing card from table");
                    //return to deck
                    int cardToReturn = table.slotToCard[i];
                    table.removeCard(i);
                    deck.add(cardToReturn);
                }
            }
            
        }
    }


    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int maxScore=0;
        int countWinners=0;

        for(Player p: players){
            if(p.getScore()>maxScore){
                maxScore= p.getScore();
            }
        }

        for(Player p: players){
            if(p.getScore()==maxScore){
                countWinners++;
            }
        }
        int j =0;
        int[] winners  = new int[countWinners];
        for(int i=0; i<players.length;i++){
            if(players[i].getScore()==maxScore){
                winners[j] = players[i].id;
                j++;
            }
        }
        env.ui.announceWinner(winners);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    public int[] announceWinnersTest() {
        // TODO implement
        int maxScore=0;
        int countWinners=0;

        for(Player p: players){
            if(p.getScore()>maxScore){
                maxScore= p.getScore();
            }
        }

        for(Player p: players){
            if(p.getScore()==maxScore){
                countWinners++;
            }
        }
        int j =0;
        int[] winners  = new int[countWinners];
        for(int i=0; i<players.length;i++){
            if(players[i].getScore()==maxScore){
                winners[j] = players[i].id;
                j++;
            }
        }
        //env.ui.announceWinner(winners);
        return winners;
    }
}
