/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.turn.ttorrent.client;

import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.common.TorrentCreator;
import com.turn.ttorrent.test.TorrentTestUtils;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author shevek
 */
public class ReplicationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ReplicationTest.class);
    private Tracker tracker;
    private Torrent torrent;
    private TrackedTorrent trackedTorrent;
    private Client seed;
    private final List<Client> leechers = new ArrayList<Client>();

    @Before
    public void setUp() throws Exception {
        tracker = new Tracker(new InetSocketAddress("localhost", 5674));
        tracker.start();

        File dir = TorrentTestUtils.newTorrentDir("ReplicationTest.seed");

        TorrentCreator creator = TorrentTestUtils.newTorrentCreator(dir, 1234567, true);
        creator.setAnnounce(tracker.getAnnounceUrl().toURI());
        torrent = creator.create();

        trackedTorrent = tracker.announce(torrent);
        trackedTorrent.setAnnounceInterval(4, TimeUnit.SECONDS);

        seed = new Client(torrent, dir);
    }

    @After
    public void tearDown() throws Exception {
        for (Client leecher : leechers)
            leecher.stop();
        seed.stop();
        tracker.stop();
    }

    private void testReplication(int seed_delay, int nclients) throws Exception {
        if (seed_delay <= 0) {
            seed.start();
            Thread.sleep(-seed_delay);
        }

        CountDownLatch latch = new CountDownLatch(nclients);

        for (int i = 0; i < nclients; i++) {
            File d = TorrentTestUtils.newTorrentDir("ReplicationTest.client" + i);
            Client c = new Client(torrent, d);
            c.addClientListener(new ReplicationCompletionListener(latch, TorrentHandler.State.SEEDING));
            leechers.add(c);
            c.start();
        }

        if (seed_delay > 0) {
            Thread.sleep(seed_delay);
            seed.start();
        }

        latch.await();
    }

    @Test
    public void testReplicationMultipleLate() throws Exception {
        trackedTorrent.setAnnounceInterval(100, TimeUnit.SECONDS);
        testReplication(-500, 3);
    }
}