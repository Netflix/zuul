package com.netflix.zuul.dependency;


import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.niws.client.http.HttpClientRequest;
import com.netflix.niws.client.http.HttpClientResponse;
import com.netflix.zuul.context.NFRequestContext;
import com.netflix.niws.client.http.RestClient;

import javax.ws.rs.core.MultivaluedMap;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by IntelliJ IDEA.
 * User: mcohen
 * Date: 2/6/12
 * Time: 2:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class NIWSCommand extends HystrixCommand<HttpClientResponse> {

    RestClient restClient;
    HttpClientRequest.Verb verb;
    URI uri;
    MultivaluedMap<String, String> headers;
    MultivaluedMap<String, String> params;
    InputStream requestEntity;



    public NIWSCommand(RestClient restClient,
                        HttpClientRequest.Verb verb,
                        String uri,
                        MultivaluedMap<String, String> headers,
                        MultivaluedMap<String, String> params,
                        InputStream requestEntity) throws URISyntaxException {
        this("default", restClient, verb, uri, headers, params, requestEntity);
    }


        public NIWSCommand( String commandKey,
            RestClient restClient,
                       HttpClientRequest.Verb verb,
                       String uri,
                       MultivaluedMap<String, String> headers,
                       MultivaluedMap<String, String> params,
                       InputStream requestEntity) throws URISyntaxException {

        super(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey(commandKey))
                .andCommandPropertiesDefaults(
                        // we want to default to semaphore-isolation since this wraps
                        // 2 others commands that are already thread isolated
                        HystrixCommandProperties.Setter()
                                .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                                .withExecutionIsolationSemaphoreMaxConcurrentRequests(DynamicPropertyFactory.getInstance().
                                        getIntProperty("zuul.eureka."+commandKey +".semaphore.maxSemaphores", 100).get())));

        this.restClient = restClient;
        this.verb = verb;
        this.uri = new URI(uri);
        this.headers = headers;
        this.params = params;
        this.requestEntity = requestEntity;
    }

    @Override
    protected HttpClientResponse run() throws Exception {
        try {
            return proxy();
        } catch (Exception e) {
            throw e;
        }
    }

    HttpClientResponse proxy() throws Exception {

        NFRequestContext context = NFRequestContext.getCurrentContext();
        HttpClientRequest httpClientRequest = HttpClientRequest.newBuilder().
                setVerb(verb).
                setUri(uri).
                setHeaders(headers).
                setEntity(requestEntity).
                setQueryParams(params).build();

        HttpClientResponse response = restClient.executeWithLoadBalancer(httpClientRequest);
        context.setProxyResponse(response);
        return response;
    }


}
