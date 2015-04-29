package com.netflix.zuul.dependency.ribbon.hystrix;

import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.niws.client.http.RestClient;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.context.Headers;
import com.netflix.zuul.context.HttpQueryParams;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import static com.netflix.client.http.HttpRequest.Verb;

/**
 * Hystrix wrapper around Eureka Ribbon command
 *
 * @author Mikey Cohen
 *         Date: 2/6/12
 *         Time: 2:54 PM
 */
public class RibbonCommand extends HystrixCommand<HttpResponse> {

    RestClient restClient;
    Verb verb;
    URI uri;
    Headers headers;
    HttpQueryParams params;
    InputStream requestEntity;


    public RibbonCommand(RestClient restClient,
                         Verb verb,
                         String uri,
                         Headers headers,
                         HttpQueryParams params,
                         InputStream requestEntity) throws URISyntaxException {
        this("default", restClient, verb, uri, headers, params, requestEntity);
    }


    public RibbonCommand(String commandKey,
                         RestClient restClient,
                         Verb verb,
                         String uri,
                         Headers headers,
                         HttpQueryParams params,
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
    protected HttpResponse run() throws Exception {
        try {
            return forward();
        } catch (Exception e) {
            throw e;
        }
    }

    HttpResponse forward() throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder().
                verb(verb).
                uri(uri).
                entity(requestEntity);

        for (Map.Entry<String, String> entry : headers.entries()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, String> entry : params.getEntries()) {
            builder.queryParams(entry.getKey(), entry.getValue());
        }

        HttpRequest httpClientRequest = builder.build();

        return restClient.executeWithLoadBalancer(httpClientRequest);
    }


}
