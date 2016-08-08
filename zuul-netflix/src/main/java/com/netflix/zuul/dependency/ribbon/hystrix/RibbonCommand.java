package com.netflix.zuul.dependency.ribbon.hystrix;

import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.client.AbstractLoadBalancerAwareClient;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.context.NFRequestContext;
import com.netflix.zuul.dependency.httpclient.hystrix.HostCommand;

import javax.ws.rs.core.MultivaluedMap;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static com.netflix.client.http.HttpRequest.Verb;

/**
 * Hystrix wrapper around Eureka Ribbon command
 *
 * @author Mikey Cohen
 *         Date: 2/6/12
 *         Time: 2:54 PM
 */
public class RibbonCommand extends HystrixCommand<HttpResponse> {

    AbstractLoadBalancerAwareClient<HttpRequest, HttpResponse> restClient;
    Verb verb;
    URI uri;
    MultivaluedMap<String, String> headers;
    MultivaluedMap<String, String> params;
    InputStream requestEntity;


    public RibbonCommand(AbstractLoadBalancerAwareClient<HttpRequest, HttpResponse> restClient,
                         Verb verb,
                         String uri,
                         MultivaluedMap<String, String> headers,
                         MultivaluedMap<String, String> params,
                         InputStream requestEntity) throws URISyntaxException {
        this("default", restClient, verb, uri, headers, params, requestEntity);
    }


    public RibbonCommand(String commandKey,
                         AbstractLoadBalancerAwareClient<HttpRequest, HttpResponse> restClient,
                         Verb verb,
                         String uri,
                         MultivaluedMap<String, String> headers,
                         MultivaluedMap<String, String> params,
                         InputStream requestEntity) throws URISyntaxException {

        // Switch the command/group key to remain passive with the previous release which used the command key as the group key
        this(commandKey, RibbonCommand.class.getSimpleName(), restClient, verb, uri, headers, params, requestEntity);
    }
    
    public RibbonCommand(String groupKey, 
                    String commandKey,
                    AbstractLoadBalancerAwareClient<HttpRequest, HttpResponse> restClient,
                    Verb verb,
                    String uri,
                    MultivaluedMap<String, String> headers,
                    MultivaluedMap<String, String> params,
                    InputStream requestEntity) throws URISyntaxException {
        
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey)).andCommandPropertiesDefaults(
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
    protected HttpResponse run() throws Exception {
        try {
            return forward();
        } catch (Exception e) {
            throw e;
        }
    }

    HttpResponse forward() throws Exception {

        NFRequestContext context = NFRequestContext.getCurrentContext();


        HttpRequest.Builder builder = HttpRequest.newBuilder().
                verb(verb).
                uri(uri).
                entity(requestEntity);

        for (String name : headers.keySet()) {
            List<String> values = headers.get(name);
            for (String value : values) {
                builder.header(name, value);
            }
        }

        for (String name : params.keySet()) {
            List<String> values = params.get(name);
            for (String value : values) {
                builder.queryParams(name, value);
            }
        }

        HttpRequest httpClientRequest = builder.build();

        HttpResponse response = restClient.executeWithLoadBalancer(httpClientRequest);
        context.setZuulResponse(response);

        // Here we want to handle the case where this hystrix command timed-out before the
        // ribbon client received a response from origin. So in this situation we want to
        // cleanup this response (release connection) now, as we know that the zuul filter
        // chain has already continued without us and therefore won't cleanup itself.
        if (isResponseTimedOut()) {
            response.close();
        }

        return response;
    }


    
    public static class UnitTest {
        
        private static final String localhost = "http://localhost";
        
        @Test
        public void testConstruction() throws URISyntaxException {
            RibbonCommand rc = new RibbonCommand(null, null, localhost, null, null, null);
            Assert.assertEquals("default", rc.getCommandGroup().name());
            Assert.assertEquals(RibbonCommand.class.getSimpleName(), rc.getCommandKey().name());
        }
        
        @Test
        public void testConstructionWithCommandKey() throws URISyntaxException {
            RibbonCommand rc = new RibbonCommand("myCommand", null, null, localhost, null, null, null);
            Assert.assertEquals("myCommand", rc.getCommandGroup().name());
            Assert.assertEquals(RibbonCommand.class.getSimpleName(), rc.getCommandKey().name());
        }
        
        @Test
        public void testConstructionWithGroupKeyAndCommandKey() throws URISyntaxException {
            RibbonCommand rc = new RibbonCommand("myGroup", "myCommand", null, null, localhost, null, null, null);
            Assert.assertEquals("myGroup", rc.getCommandGroup().name());
            Assert.assertEquals("myCommand", rc.getCommandKey().name());
        }
    }
}
