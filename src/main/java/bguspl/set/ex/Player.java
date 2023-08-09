package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    public int sleepTime;
    private final int tableSize;
    private final int numOfToken;

    /**
     * Actions Queue
     */
    public BlockingQueue<Integer> actions;

    /**
     * tokens, -1 if not placed, else slot
     */
    public BlockingQueue<Integer> myTokens;

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


    public boolean waitForCards;
    public boolean isInSleep;
    /**
     * True iff game should be terminated due to an external event.
     */
    public volatile boolean terminate;

    /**
     * The current score of the player.
     */
    public int score;
    private final Dealer dealer;
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
        actions = new LinkedBlockingQueue<>();
        myTokens = new LinkedBlockingQueue<>();
        waitForCards = false;
        isInSleep = false;
        this.tableSize = env.config.tableSize;
        this.numOfToken = env.config.featureSize;
    }


    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            //if actions not null act
            if(actions.size()>0)
                doAction();
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * perform the action
     * check if remove or place
     * act accordingly
     */
    private void doAction() {
        boolean peek = actions.peek() != null;
        if (peek) {
            //TODO:System.out.println("player" + this.id + ":  doing action ");
            // Retrieve an element from the queue
            int tokenToCheck =-1;
            synchronized (actions) {
                if (actions.peek() != null)
                    tokenToCheck = actions.poll();
            }
            if (tokenToCheck != -1 && table.slotToCard[tokenToCheck] != null) {
                //remove if alredy there
                boolean flag = false;
                for (int i = 0; i < numOfToken; i++) {
                    if (myTokens.contains(tokenToCheck)) {
                        if (table.removeToken(this.id, tokenToCheck)) {
                            //TODO: System.out.println("player" + this.id + ":   i delete " + tokenToCheck);
                            myTokens.remove(tokenToCheck);
                            flag = true;
                        }
                    }
                }
                boolean set = true;
                boolean found = false;
                if (!flag) {
                    for (int i = 0; i < numOfToken & !found; i++) {
                        if (myTokens.size() < numOfToken) {
                            table.placeToken(this, tokenToCheck);
                            myTokens.add(tokenToCheck);
                            //TODO:System.out.println("player" + this.id + ":   i choose " + tokenToCheck);

                            found = true;
                            if (myTokens.size() != numOfToken)
                                set = false;
                        }
                    }
                    if (set) {
                        actions.clear();
                        isInSleep = true;
                        dealer.addToOrder(this);
                        synchronized (this) {
                            try {
                                this.wait();

                            } catch (InterruptedException e) {

                            }
                            try {
                                while (sleepTime>0){
                                    env.ui.setFreeze(id, sleepTime);
                                    Thread.sleep(this.sleepTime);
                                    sleepTime-=1000;
                                }
                                env.ui.setFreeze(id, 0);
                            } catch (Exception e) {
                                //TODO:System.out.println("not sleep");
                            }
                            this.sleepTime = 0;
                            this.isInSleep =false;
                            actions.clear();
                        }
                    }
                }
            }
        }
    }




    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                // TODO implement player key press simulator
                //tryFindSet();
                beIdiotAi();
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    private void beIdiotAi() {
        //TODO:System.out.println("player"+this.id+":  doing action ");

        Random random = new Random();
        int pressed;
//        for(int i=0; i<numOfToken; i++){

            pressed = (int)(Math.random()*tableSize); /*random.nextInt(env.config.tableSize);*/
            keyPressed(pressed);


        }



    private void tryFindSet() {

        // try {
        //     synchronized (this) {
        //         wait(500);
        //     }
        // } catch (InterruptedException ignored) {
        // }
        List<Integer> poss = table.Aihints();
        //TODO:if(poss.size()==0) System.out.println("\033[34mNo More Sets\033[0m");

        for(int i=0; i<numOfToken; i++){
            if(poss.size()>0) {
                int pressed = poss.get(i);
                keyPressed(pressed);
            }
            // try {
            //     synchronized (this) {
            //         wait(500);
            //     }
            // } catch (InterruptedException ignored) {
            // }
        }

    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
       if ((!waitForCards|| !isInSleep) && (!(myTokens.size() ==3)|myTokens.contains(slot))){
        if(actions.size()<numOfToken) {
            try {
                actions.put(slot);
            }catch (Exception e) {
            }
        }
        
      }
//       if (waitForCards| isInSleep){
//            actions.clear();
//      }

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        score = score+1;
        env.ui.setScore(this.id, score);
        this.myTokens.clear();
        sleepTime =(int) env.config.pointFreezeMillis;

    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        sleepTime = (int) env.config.penaltyFreezeMillis;
        //actions.clear();


    }

    public int getScore() {
        return score;
    }

    public int score() {
        return score;
    }
}
