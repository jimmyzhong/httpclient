/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.conn;

import java.util.concurrent.TimeUnit;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * {@link PoolingHttpClientConnectionManager} tests.
 */
public class TestPoolingHttpClientConnectionManager {

    private static HttpClientConnection getConnection(
            final HttpClientConnectionManager mgr,
            final HttpRoute route,
            long timeout,
            TimeUnit unit) throws ConnectionPoolTimeoutException, InterruptedException {
        ConnectionRequest connRequest = mgr.requestConnection(route, null);
        return connRequest.get(timeout, unit);
    }

    private static HttpClientConnection getConnection(
            final HttpClientConnectionManager mgr,
            final HttpRoute route) throws ConnectionPoolTimeoutException, InterruptedException {
        ConnectionRequest connRequest = mgr.requestConnection(route, null);
        return connRequest.get(0, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalConstructor() {
        new PoolingHttpClientConnectionManager(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetConnection()
            throws InterruptedException, ConnectionPoolTimeoutException {
        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        HttpClientConnection conn = getConnection(mgr, route);
        Assert.assertNotNull(conn);
        Assert.assertFalse(conn.isOpen());

        mgr.releaseConnection(conn, null, -1, null);

        try {
            getConnection(mgr, null);
        } finally {
            mgr.shutdown();
        }
    }

    // testTimeout in 3.x TestHttpConnectionManager is redundant
    // several other tests here rely on timeout behavior
    @Test
    public void testMaxConnTotal()
            throws InterruptedException, ConnectionPoolTimeoutException {

        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();
        mgr.setMaxTotal(2);
        mgr.setDefaultMaxPerRoute(1);

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);

        HttpClientConnection conn1 = getConnection(mgr, route1);
        Assert.assertNotNull(conn1);
        HttpClientConnection conn2 = getConnection(mgr, route2);
        Assert.assertNotNull(conn2);

        try {
            // this should fail quickly, connection has not been released
            getConnection(mgr, route2, 100L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // release one of the connections
        mgr.releaseConnection(conn2, null, -1, null);
        conn2 = null;

        // there should be a connection available now
        try {
            getConnection(mgr, route2, 100L, TimeUnit.MILLISECONDS);
        } catch (ConnectionPoolTimeoutException cptx) {
            Assert.fail("connection should have been available: " + cptx);
        }

        mgr.shutdown();
    }

    @Test
    public void testMaxConnPerHost() throws Exception {

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);
        HttpHost target3 = new HttpHost("www.test3.invalid", 80, "http");
        HttpRoute route3 = new HttpRoute(target3, null, false);

        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();
        mgr.setMaxTotal(100);
        mgr.setDefaultMaxPerRoute(1);
        mgr.setMaxPerRoute(route2, 2);
        mgr.setMaxPerRoute(route3, 3);

        // route 3, limit 3
        HttpClientConnection conn1 =
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);
        HttpClientConnection conn2 =
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn2);
        HttpClientConnection conn3 =
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn3);
        try {
            // should fail quickly, connection has not been released
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // route 2, limit 2
        conn1 = getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
        conn2 = getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
        try {
            // should fail quickly, connection has not been released
            getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // route 1, should use default limit of 1
        conn1 = getConnection(mgr, route1, 10L, TimeUnit.MILLISECONDS);
        try {
            // should fail quickly, connection has not been released
            getConnection(mgr, route1, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        // check releaseConnection with invalid arguments
        try {
            mgr.releaseConnection(null, null, -1, null);
            Assert.fail("null connection adapter not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }
        try {
            HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
            mgr.releaseConnection(conn, null, -1, null);
            Assert.fail("foreign connection adapter not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        mgr.shutdown();
    }

    @Test
    public void testReleaseConnection() throws Exception {

        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();
        mgr.setMaxTotal(3);
        mgr.setDefaultMaxPerRoute(1);

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);
        HttpHost target3 = new HttpHost("www.test3.invalid", 80, "http");
        HttpRoute route3 = new HttpRoute(target3, null, false);

        // the first three allocations should pass
        HttpClientConnection conn1 =
            getConnection(mgr, route1, 10L, TimeUnit.MILLISECONDS);
        HttpClientConnection conn2 =
            getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
        HttpClientConnection conn3 =
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn1);
        Assert.assertNotNull(conn2);
        Assert.assertNotNull(conn3);

        // obtaining another connection for either of the three should fail
        // this is somehow redundant with testMaxConnPerHost
        try {
            getConnection(mgr, route1, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        try {
            getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        try {
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        // now release one and check that exactly that one can be obtained then
        mgr.releaseConnection(conn2, null, -1, null);
        conn2 = null;
        try {
            getConnection(mgr, route1, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }
        // this one succeeds
        conn2 = getConnection(mgr, route2, 10L, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(conn2);
        try {
            getConnection(mgr, route3, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        mgr.shutdown();
    }

    @Test
    public void testDeleteClosedConnections() throws Exception {
        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        HttpClientConnection conn = getConnection(mgr, route);

        Assert.assertEquals(1, mgr.getTotalStats().getLeased());
        Assert.assertEquals(1, mgr.getStats(route).getLeased());
        mgr.releaseConnection(conn, null, -1, null);

        Assert.assertEquals(0, mgr.getTotalStats().getAvailable());
        Assert.assertEquals(0, mgr.getStats(route).getAvailable());

        mgr.shutdown();
    }

    @Test
    public void testShutdown() throws Exception {
        // 3.x: TestHttpConnectionManager.testShutdown

        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();
        mgr.setMaxTotal(1);
        mgr.setDefaultMaxPerRoute(1);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        // get the only connection, then start an extra thread
        // on shutdown, the extra thread should get an exception

        HttpClientConnection conn =
            getConnection(mgr, route, 1L, TimeUnit.MILLISECONDS);
        GetConnThread gct = new GetConnThread(mgr, route, 0L); // no timeout
        gct.start();
        Thread.sleep(100); // give extra thread time to block


        mgr.shutdown();

        // First release the connection. If the manager keeps working
        // despite the shutdown, this will deblock the extra thread.
        // The release itself should turn into a no-op, without exception.
        mgr.releaseConnection(conn, null, -1, null);


        gct.join(10000);
        Assert.assertNull("thread should not have obtained connection",
                   gct.getConnection());
        Assert.assertNotNull("thread should have gotten an exception",
                      gct.getException());
        Assert.assertSame("thread got wrong exception",
                InterruptedException.class, gct.getException().getClass());

        // the manager is down, we should not be able to get a connection
        try {
            getConnection(mgr, route, 1L, TimeUnit.MILLISECONDS);
            Assert.fail("shut-down manager does not raise exception");
        } catch (IllegalStateException isx) {
            // expected
        }
    }

    @Test
    public void testInterruptThread() throws Exception {
        // 3.x: TestHttpConnectionManager.testWaitingThreadInterrupted

        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();
        mgr.setMaxTotal(1);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        // get the only connection, then start an extra thread
        HttpClientConnection conn =
            getConnection(mgr, route, 1L, TimeUnit.MILLISECONDS);
        GetConnThread gct = new GetConnThread(mgr, route, 0L); // no timeout
        gct.start();
        Thread.sleep(100); // give extra thread time to block


        // interrupt the thread, it should cancel waiting with an exception
        gct.interrupt();


        gct.join(10000);
        Assert.assertNotNull("thread should have gotten an exception",
                      gct.getException());
        Assert.assertSame("thread got wrong exception",
                   InterruptedException.class,
                   gct.getException().getClass());

        // make sure the manager is still working
        try {
            getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("should have gotten a timeout");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        mgr.releaseConnection(conn, null, -1, null);
        // this time: no exception
        conn = getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("should have gotten a connection", conn);

        mgr.shutdown();
    }

    @Test
    public void testReusePreference() throws Exception {
        // 3.x: TestHttpConnectionManager.testHostReusePreference

        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();
        mgr.setMaxTotal(1);

        HttpHost target1 = new HttpHost("www.test1.invalid", 80, "http");
        HttpRoute route1 = new HttpRoute(target1, null, false);
        HttpHost target2 = new HttpHost("www.test2.invalid", 80, "http");
        HttpRoute route2 = new HttpRoute(target2, null, false);

        // get the only connection, then start two extra threads
        HttpClientConnection conn =
            getConnection(mgr, route1, 1L, TimeUnit.MILLISECONDS);
        GetConnThread gct1 = new GetConnThread(mgr, route1, 1000L);
        GetConnThread gct2 = new GetConnThread(mgr, route2, 1000L);

        // the second thread is started first, to distinguish the
        // route-based reuse preference from first-come, first-served
        gct2.start();
        Thread.sleep(100); // give the thread time to block
        gct1.start();
        Thread.sleep(100); // give the thread time to block


        // releasing the connection for route1 should deblock thread1
        // the other thread gets a timeout
        mgr.releaseConnection(conn, null, -1, null);

        gct1.join(10000);
        gct2.join(10000);

        Assert.assertNotNull("thread 1 should have gotten a connection",
                      gct1.getConnection());
        Assert.assertNull   ("thread 2 should NOT have gotten a connection",
                      gct2.getConnection());

        mgr.shutdown();
    }

    @Test
    public void testAbortAfterRequestStarts() throws Exception {
        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();
        mgr.setMaxTotal(1);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        // get the only connection, then start an extra thread
        HttpClientConnection conn = getConnection(mgr, route, 1L, TimeUnit.MILLISECONDS);
        ConnectionRequest request = mgr.requestConnection(route, null);
        GetConnThread gct = new GetConnThread(request, route, 0L); // no timeout
        gct.start();
        Thread.sleep(100); // give extra thread time to block

        request.cancel();

        gct.join(10000);
        Assert.assertNotNull("thread should have gotten an exception",
                      gct.getException());
        Assert.assertSame("thread got wrong exception",
                   InterruptedException.class,
                   gct.getException().getClass());

        // make sure the manager is still working
        try {
            getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("should have gotten a timeout");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        mgr.releaseConnection(conn, null, -1, null);
        // this time: no exception
        conn = getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("should have gotten a connection", conn);

        mgr.shutdown();
    }

    @Test
    public void testAbortBeforeRequestStarts() throws Exception {
        PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();
        mgr.setMaxTotal(1);

        HttpHost target = new HttpHost("www.test.invalid", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        // get the only connection, then start an extra thread
        HttpClientConnection conn = getConnection(mgr, route, 1L, TimeUnit.MILLISECONDS);
        ConnectionRequest request = mgr.requestConnection(route, null);
        request.cancel();

        GetConnThread gct = new GetConnThread(request, route, 0L); // no timeout
        gct.start();
        Thread.sleep(100); // give extra thread time to block

        gct.join(10000);
        Assert.assertNotNull("thread should have gotten an exception",
                      gct.getException());
        Assert.assertSame("thread got wrong exception",
                   InterruptedException.class,
                   gct.getException().getClass());

        // make sure the manager is still working
        try {
            getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
            Assert.fail("should have gotten a timeout");
        } catch (ConnectionPoolTimeoutException e) {
            // expected
        }

        mgr.releaseConnection(conn, null, -1, null);
        // this time: no exception
        conn = getConnection(mgr, route, 10L, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("should have gotten a connection", conn);

        mgr.shutdown();
    }

    public class GetConnThread extends Thread {

        private final ConnectionRequest connRequest;
        private final long timeout;

        private volatile HttpClientConnection connection;
        private volatile Exception exception;

        public GetConnThread(
                final HttpClientConnectionManager mgr,
                final HttpRoute route, long timeout) {
            this(mgr.requestConnection(route, null), route, timeout);
        }

        public GetConnThread(
                final ConnectionRequest connRequest,
                final HttpRoute route, long timeout) {
            super();
            this.connRequest = connRequest;
            this.timeout = timeout;
        }

        @Override
        public void run() {
            try {
                connection = connRequest.get(timeout, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                exception = ex;
            }
        }

        public Throwable getException() {
            return exception;
        }

        public HttpClientConnection getConnection() {
            return connection;
        }

    }

}
