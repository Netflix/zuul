package com.netflix.zuul.dependency.ribbon.hystrix;

import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.niws.client.http.HttpClientRequest;
import com.netflix.niws.client.http.HttpClientResponse;
import com.netflix.niws.client.http.RestClient;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.context.NFRequestContext;

import javax.ws.rs.core.MultivaluedMap;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Hystrix wrapper around Eureka Ribbon command
 *
 * @author Mikey Cohen
 *         Date: 2/6/12
 *         Time: 2:54 PM
 */
public class RibbonCommand extends HystrixCommand<HttpClientResponse> {

    RestClient restClient;
    HttpClientRequest.Verb verb;
    URI uri;
    MultivaluedMap<String, String> headers;
    MultivaluedMap<String, String> params;
    InputStream requestEntity;


    public RibbonCommand(RestClient restClient,
                         HttpClientRequest.Verb verb,
                         String uri,
                         MultivaluedMap<String, String> headers,
                         MultivaluedMap<String, String> params,
                         InputStream requestEntity) throws URISyntaxException {
        this("default", restClient, verb, uri, headers, params, requestEntity);
    }


    public RibbonCommand(String commandKey,
                         RestClient restClient,
                         HttpClientRequest.Verb verb,
                         String uri,
                         MultivaluedMap<String, String> headers,
                         MultivaluedMap<String, String> params,
                         InputStream requestEntity) throws URISyntaxException {

        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(commandKey)).andCommandPropertiesDefaults(
                // we want to default to semaphore-isolation since this wraps
                // 2 others commands that are already thread isolated
                HystrixCommandProperties.Setter().withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                        .withExecutionIsolationSemaphoreMaxConcurrentRequests(DynamicPropertyFactory.getInstance().
                                getIntProperty(ZuulConstants.ZUUL_EUREKA + commandKey + ".semaphore.maxSemaphores", 100).get())));

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
            return forward();
        } catch (Exception e) {
            throw e;
        }
    }

    HttpClientResponse forward() throws Exception {

        NFRequestContext context = NFRequestContext.getCurrentContext();
        HttpClientRequest httpClientRequest = HttpClientRequest.newBuilder().
                setVerb(verb).
                setUri(uri).
                setHeaders(headers).
                setEntity(requestEntity).
                setQueryParams(params).build();

        HttpClientResponse response = restClient.executeWithLoadBalancer(httpClientRequest);
        context.setZuulResponse(response);
        return response;
    }


}
