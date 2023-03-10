/*******************************************************************************
 * Copyright (c) 2016 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *     Achim Kraus (Bosch Software Innovations GmbH) - set exception in onSendError
 *     Simon Bernard                                 - use specific exception for onSendError  
 *******************************************************************************/
package org.eclipse.leshan.client.demo.core.messageobserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.elements.exception.EndpointUnconnectedException;
import org.eclipse.californium.scandium.dtls.DtlsHandshakeTimeoutException;
import org.eclipse.leshan.core.request.exception.RequestCanceledException;
import org.eclipse.leshan.core.request.exception.RequestRejectedException;
import org.eclipse.leshan.core.request.exception.SendFailedException;
import org.eclipse.leshan.core.request.exception.UnconnectedPeerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Californium message observer for a CoAP request.
 * <p>
 * Results are available via synchronous {@link #waitForCoapResponse()} method.
 * <p>
 * This class also provides response timeout facility.
 * 
 * @see <a href="https://github.com/eclipse/leshan/wiki/Request-Timeout">Request Timeout Wiki page</a>
 */
public class CoapSyncRequestObserver extends AbstractRequestObserver {

    private static final Logger LOG = LoggerFactory.getLogger(CoapSyncRequestObserver.class);

    private CountDownLatch latch = new CountDownLatch(1);
    private AtomicReference<Response> ref = new AtomicReference<>(null);
    private AtomicBoolean coapTimeout = new AtomicBoolean(false);
    private AtomicReference<RuntimeException> exception = new AtomicReference<>();
    private long timeout;

    /**
     * @param coapRequest The CoAP request to observe.
     * @param timeoutInMs A response timeout(in millisecond) which is raised if neither a response or error happens (see
     *        https://github.com/eclipse/leshan/wiki/Request-Timeout).
     */
    public CoapSyncRequestObserver(Request coapRequest, long timeoutInMs) {
        super(coapRequest);
        this.timeout = timeoutInMs;
    }

    @Override
    public void onResponse(Response coapResponse) {
        LOG.error("??????---->> "+"?????????????????????????????????{}", coapResponse);
        try {
            ref.set(coapResponse);
        } catch (RuntimeException e) {
            exception.set(e);
        } finally {
            latch.countDown();
        }
    }

    @Override
    public void onTimeout() {
        coapTimeout.set(true);
        latch.countDown();
    }

    @Override
    public void onCancel() {
        LOG.debug(String.format("Synchronous request cancelled %s", coapRequest));
        if (!coapTimeout.get()) {
            exception.set(new RequestCanceledException("Request %s canceled", coapRequest.getURI()));
        }
        latch.countDown();
    }

    @Override
    public void onReject() {
        exception.set(new RequestRejectedException("Request %s rejected", coapRequest.getURI()));
        latch.countDown();
    }

    @Override
    public void onSendError(Throwable error) {
        if (error instanceof DtlsHandshakeTimeoutException) {
            coapTimeout.set(true);
        } else if (error instanceof EndpointUnconnectedException) {
            exception.set(new UnconnectedPeerException(error,
                    "Unable to send request %s : peer is not connected (no DTLS connection)", coapRequest.getURI()));
        } else {
            exception.set(new SendFailedException(error, "Request %s cannot be sent", coapRequest, error.getMessage()));
        }
        latch.countDown();
    }

    /**
     * Wait for the CoAP response.
     * 
     * @return the CoAP response. The response can be <code>null</code> if the timeout expires (see
     *         https://github.com/eclipse/leshan/wiki/Request-Timeout).
     * 
     * @throws InterruptedException if the thread was interrupted.
     * @throws RequestRejectedException if the request is rejected by foreign peer.
     * @throws RequestCanceledException if the request is cancelled.
     * @throws SendFailedException if the request can not be sent. E.g. error at CoAP or DTLS/UDP layer.
     */
    public Response waitForCoapResponse() throws InterruptedException {
        try {
            boolean timeElapsed = false;
            timeElapsed = !latch.await(timeout, TimeUnit.MILLISECONDS);
            if (timeElapsed || coapTimeout.get()) {
                coapTimeout.set(true);
                coapRequest.cancel();
            }
        } finally {
            coapRequest.removeMessageObserver(this);
        }

        if (exception.get() != null) {
            coapRequest.cancel();
            throw exception.get();
        }
        return ref.get();
    }
}