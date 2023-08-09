package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Player player;

    Player player2;

    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.score() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        player = new Player(env, dealer, table, 0, false);
        player2 = new Player(env, dealer, table, 1, false);

        Player[] players = new Player[2];
        dealer = new Dealer(env, table, players);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }



    @Test
    void addToOrder() {

        int expectedOrderSize = dealer.order.size();

        dealer.addToOrder(player);

        assertEquals(expectedOrderSize, dealer.order.size());

        player.myTokens.add(-1);
        player.myTokens.add(-1);
        player.myTokens.add(-1);

        expectedOrderSize = dealer.order.size()+1;

        dealer.addToOrder(player);
        assertEquals(expectedOrderSize, dealer.order.size());

    }

    @Test
    void announceWinners() {
        dealer.players[0] = player;
        dealer.players[1] = player2;
        int[] expectedWinners = new int[1];
        expectedWinners[0] = player.id;

        player.score = 4;
        player2.score = 1;

        for(int i=0; i<dealer.announceWinnersTest().length; i++) {
            assertEquals(expectedWinners[i], dealer.announceWinnersTest()[i]);
        }



        //expectedOrderSize = dealer.order.size()+1;

        //assertEquals(expectedOrderSize, dealer.order.size());

    }
}