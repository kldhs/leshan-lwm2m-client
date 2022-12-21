/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
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
 *     Zebra Technologies - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.client.demo.clientcf.request;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cn.hutool.json.JSONUtil;
import org.eclipse.californium.core.coap.MessageObserver;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.leshan.client.demo.clientcf.CaliforniumEndpointsManager;
import org.eclipse.leshan.client.demo.clientcore.servers.ServerIdentity;
import org.eclipse.leshan.client.demo.core.messageobserver.AsyncRequestObserver;
import org.eclipse.leshan.client.demo.core.messageobserver.SyncRequestObserver;
import org.eclipse.leshan.core.request.UplinkRequest;
import org.eclipse.leshan.core.response.ErrorCallback;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ResponseCallback;
import org.eclipse.leshan.core.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link LwM2mRequestSender} based on Californium(CoAP implementation).
 */
public class CaliforniumLwM2mRequestSender implements LwM2mRequestSender {

    private static final Logger LOG = LoggerFactory.getLogger(CaliforniumLwM2mRequestSender.class);

    private final ScheduledExecutorService executor;
    private final boolean attached;
    private final CaliforniumEndpointsManager endpointsManager;

    public CaliforniumLwM2mRequestSender(CaliforniumEndpointsManager endpointsManager,
            ScheduledExecutorService sharedExecutor) {
        this.endpointsManager = endpointsManager;
        if (sharedExecutor == null) {
            this.executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("Leshan Async Request timeout"));
            this.attached = false;
        } else {
            this.executor = sharedExecutor;
            this.attached = false;
        }
    }

    /**
     * 同步方式发送请求
     * @param server The destination.
     * @param request The request to send to the client.
     * @param timeout
     * @param <T>
     * @return
     * @throws InterruptedException
     */
    @Override
    public <T extends LwM2mResponse> T send(ServerIdentity server, final UplinkRequest<T> request, long timeout)
            throws InterruptedException {
        // Create the CoAP request from LwM2m request
        CoapRequestBuilder coapClientRequestBuilder = new CoapRequestBuilder(server.getIdentity());
        request.accept(coapClientRequestBuilder);
        Request coapRequest = coapClientRequestBuilder.getRequest();

        // Send CoAP request synchronously
        SyncRequestObserver<T> syncMessageObserver = new SyncRequestObserver<T>(coapRequest, timeout) {
            @Override
            public T buildResponse(Response coapResponse) {
                // Build LwM2m response
                LwM2mClientResponseBuilder<T> lwm2mResponseBuilder = new LwM2mClientResponseBuilder<>(coapResponse);
                request.accept(lwm2mResponseBuilder);
                return lwm2mResponseBuilder.getResponse();
            }
        };
        coapRequest.addMessageObserver(syncMessageObserver);

        // Send CoAP request asynchronously
        //String s = JSONObject.toJSONString(coapRequest);
        //System.err.println(s);
        LOG.error("发送---->> "+"Endpoint发送请求，coapRequest : {}", JSONUtil.toJsonStr(coapRequest));
        endpointsManager.getEndpoint(server).sendRequest(coapRequest);

        // Wait for response, then return it
        return syncMessageObserver.waitForResponse();
    }

    /**
     * 异步方式发送请求
     * @param server The destination.
     * @param request The request to send to the client.
     * @param timeout
     * @param responseCallback a callback called when a response is received (successful or error response). This
     *        callback MUST NOT be null.
     * @param errorCallback a callback called when an error or exception occurred when response is received. It can be :
     * @param <T>
     */
    @Override
    public <T extends LwM2mResponse> void send(ServerIdentity server, final UplinkRequest<T> request, long timeout,
            ResponseCallback<T> responseCallback, ErrorCallback errorCallback) {
        // Create the CoAP request from LwM2m request
        CoapRequestBuilder coapClientRequestBuilder = new CoapRequestBuilder(server.getIdentity());
        request.accept(coapClientRequestBuilder);
        Request coapRequest = coapClientRequestBuilder.getRequest();

        // Add CoAP request callback
        MessageObserver obs = new AsyncRequestObserver<T>(coapRequest, responseCallback, errorCallback, timeout,
                executor) {
            @Override
            public T buildResponse(Response coapResponse) {
                // Build LwM2m response
                LwM2mClientResponseBuilder<T> lwm2mResponseBuilder = new LwM2mClientResponseBuilder<>(coapResponse);
                request.accept(lwm2mResponseBuilder);
                return lwm2mResponseBuilder.getResponse();
            }
        };
        coapRequest.addMessageObserver(obs);

        // Send CoAP request asynchronously
        LOG.error("发送请求 ->异步 CaliforniumLwM2mRequestSender " );
        endpointsManager.getEndpoint(server).sendRequest(coapRequest);
    }

    @Override
    public void destroy() {
        if (attached) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.warn("Destroying RequestSender was interrupted.", e);
            }
        }
    }
}
