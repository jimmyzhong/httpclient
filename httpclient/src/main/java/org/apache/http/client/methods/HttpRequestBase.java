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

package org.apache.http.client.methods;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.annotation.NotThreadSafe;

import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.utils.CloneUtils;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ConnectionReleaseTrigger;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.params.HttpProtocolParams;

/**
 * Basic implementation of an HTTP request that can be modified. Methods of the
 * {@link AbortableHttpRequest} interface implemented by this class are thread safe.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public abstract class HttpRequestBase extends AbstractHttpMessage
    implements HttpUriRequest, HttpExecutionAware, AbortableHttpRequest, Cloneable {

    private Lock abortLock;
    private volatile boolean aborted;

    private ProtocolVersion version;
    private URI uri;
    private Cancellable cancellable;

    public HttpRequestBase() {
        super();
        this.abortLock = new ReentrantLock();
    }

    public abstract String getMethod();

    /**
     * @since 4.3
     */
    public void setProtocolVersion(final ProtocolVersion version) {
        this.version = version;
    }

    public ProtocolVersion getProtocolVersion() {
        return version != null ? version : HttpProtocolParams.getVersion(getParams());
    }

    /**
     * Returns the original request URI.
     * <p>
     * Please note URI remains unchanged in the course of request execution and
     * is not updated if the request is redirected to another location.
     */
    public URI getURI() {
        return this.uri;
    }

    public RequestLine getRequestLine() {
        String method = getMethod();
        ProtocolVersion ver = getProtocolVersion();
        URI uri = getURI();
        String uritext = null;
        if (uri != null) {
            uritext = uri.toASCIIString();
        }
        if (uritext == null || uritext.length() == 0) {
            uritext = "/";
        }
        return new BasicRequestLine(method, uritext, ver);
    }

    public void setURI(final URI uri) {
        this.uri = uri;
    }

    @Deprecated
    public void setConnectionRequest(final ClientConnectionRequest connRequest) {
        if (this.aborted) {
            return;
        }
        this.abortLock.lock();
        try {
            this.cancellable = new Cancellable() {

                public boolean cancel() {
                    connRequest.abortRequest();
                    return true;
                }

            };
        } finally {
            this.abortLock.unlock();
        }
    }

    @Deprecated
    public void setReleaseTrigger(final ConnectionReleaseTrigger releaseTrigger) {
        if (this.aborted) {
            return;
        }
        this.abortLock.lock();
        try {
            this.cancellable = new Cancellable() {

                public boolean cancel() {
                    try {
                        releaseTrigger.abortConnection();
                        return true;
                    } catch (IOException ex) {
                        return false;
                    }
                }

            };
        } finally {
            this.abortLock.unlock();
        }
    }

    private void cancelExecution() {
        if (this.cancellable != null) {
            this.cancellable.cancel();
            this.cancellable = null;
        }
    }

    public void abort() {
        if (this.aborted) {
            return;
        }
        this.abortLock.lock();
        try {
            this.aborted = true;
            cancelExecution();
        } finally {
            this.abortLock.unlock();
        }
    }

    public boolean isAborted() {
        return this.aborted;
    }

    /**
     * @since 4.2
     */
    public void started() {
    }

    /**
     * @since 4.2
     */
    public void setCancellable(final Cancellable cancellable) {
        if (this.aborted) {
            return;
        }
        this.abortLock.lock();
        try {
            this.cancellable = cancellable;
        } finally {
            this.abortLock.unlock();
        }
    }

    /**
     * @since 4.2
     */
    public void completed() {
        this.abortLock.lock();
        try {
            this.cancellable = null;
        } finally {
            this.abortLock.unlock();
        }
    }

    /**
     * Resets internal state of the request making it reusable.
     *
     * @since 4.2
     */
    public void reset() {
        this.abortLock.lock();
        try {
            cancelExecution();
            this.aborted = false;
        } finally {
            this.abortLock.unlock();
        }
    }

    /**
     * A convenience method to simplify migration from HttpClient 3.1 API. This method is
     * equivalent to {@link #reset()}.
     *
     * @since 4.2
     */
    public void releaseConnection() {
        reset();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        HttpRequestBase clone = (HttpRequestBase) super.clone();
        clone.abortLock = new ReentrantLock();
        clone.aborted = false;
        clone.cancellable = null;
        clone.headergroup = CloneUtils.cloneObject(this.headergroup);
        clone.params = CloneUtils.cloneObject(this.params);
        return clone;
    }

    @Override
    public String toString() {
        return getMethod() + " " + getURI() + " " + getProtocolVersion();
    }

}
