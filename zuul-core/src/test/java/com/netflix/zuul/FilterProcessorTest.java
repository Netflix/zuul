/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.server.HttpResponseHeaders;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class FilterProcessorTest {
    @Mock FilterStore<Void, Void> mockFilterStore;
    @Mock FiltersForRoute<Void, Void> mockFilters;
    @Mock HttpServerResponse<ByteBuf> mockRxNettyResp;
    @Mock HttpResponseHeaders mockRespHeaders;
    private FilterProcessor<Void, Void> processor;

    private final HttpRequest nettyReq = new HttpRequest() {
        @Override
        public HttpMethod getMethod() {
            return HttpMethod.GET;
        }

        @Override
        public HttpRequest setMethod(HttpMethod method) {
            return this;
        }

        @Override
        public String getUri() {
            return "/foo/test?qp=1";
        }

        @Override
        public HttpRequest setUri(String uri) {
            return this;
        }

        @Override
        public HttpRequest setProtocolVersion(HttpVersion version) {
            return this;
        }

        @Override
        public HttpVersion getProtocolVersion() {
            return HttpVersion.HTTP_1_1;
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.EMPTY_HEADERS;
        }

        @Override
        public DecoderResult getDecoderResult() {
            return DecoderResult.SUCCESS;
        }

        @Override
        public void setDecoderResult(DecoderResult result) {

        }
    };
    private final HttpServerRequest<ByteBuf> rxNettyReq = new HttpServerRequest<>(nettyReq, PublishSubject.create());
    private final IngressRequest ingressReq = IngressRequest.from(rxNettyReq);

    private final HttpResponse nettyResp = new HttpResponse() {
        @Override
        public HttpResponseStatus getStatus() {
            return HttpResponseStatus.OK;
        }

        @Override
        public HttpResponse setStatus(HttpResponseStatus status) {
            return this;
        }

        @Override
        public HttpResponse setProtocolVersion(HttpVersion version) {
            return this;
        }

        @Override
        public HttpVersion getProtocolVersion() {
            return HttpVersion.HTTP_1_1;
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.EMPTY_HEADERS;
        }

        @Override
        public DecoderResult getDecoderResult() {
            return DecoderResult.SUCCESS;
        }

        @Override
        public void setDecoderResult(DecoderResult result) {

        }
    };
    private final HttpClientResponse<ByteBuf> rxNettyResp = new HttpClientResponse<>(nettyResp, PublishSubject.create());
    private final IngressResponse ingressResp = IngressResponse.from(rxNettyResp);

    private final HttpResponse nettyErrorResp = new HttpResponse() {
        @Override
        public HttpResponseStatus getStatus() {
            return HttpResponseStatus.INTERNAL_SERVER_ERROR;
        }

        @Override
        public HttpResponse setStatus(HttpResponseStatus status) {
            return this;
        }

        @Override
        public HttpResponse setProtocolVersion(HttpVersion version) {
            return this;
        }

        @Override
        public HttpVersion getProtocolVersion() {
            return HttpVersion.HTTP_1_1;
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.EMPTY_HEADERS;
        }

        @Override
        public DecoderResult getDecoderResult() {
            return DecoderResult.SUCCESS;
        }

        @Override
        public void setDecoderResult(DecoderResult result) {

        }
    };
    private final HttpClientResponse<ByteBuf> rxNettyErrorResp = new HttpClientResponse<>(nettyErrorResp, PublishSubject.create());
    private final IngressResponse ingressErrorResp = IngressResponse.from(rxNettyErrorResp);

    private final PreFilter<Void> successPreFilter = createPreFilter(1, true, egressReq -> {
        egressReq.addHeader("PRE", "DONE");
        return egressReq;
    });

    private final PreFilter<Void> shouldNotPreFilter = createPreFilter(2, false, egressReq -> {
        egressReq.addHeader("PRE", "SHOULDNOT");
        return egressReq;
    });

    private final PreFilter<Void> emptyPreFilter = createIoPreFilter(3, true, egressReq -> Observable.empty());

    private final PreFilter<Void> doublePreFilter = createIoPreFilter(4, true, egressReq -> {
        egressReq.addHeader("PRE", "DOUBLE");
        return Observable.from(egressReq, egressReq);
    });

    private final PreFilter<Void> errorPreFilter = createIoPreFilter(5, true, egressReq -> {
        egressReq.addHeader("PRE", "ERROR");
        return Observable.error(new RuntimeException("pre unit test"));
    });

    private final RouteFilter<Void> successRouteFilter = createRouteFilter(egressReq -> Observable.just(ingressResp));

    private final RouteFilter<Void> emptyRouteFilter = createRouteFilter(egressReq -> Observable.empty());

    private final RouteFilter<Void> doubleRouteFilter = createRouteFilter(egressReq -> Observable.from(ingressResp, ingressResp));

    private final RouteFilter<Void> errorRouteFilter = createRouteFilter(egressReq -> Observable.error(new RuntimeException("route unit test")));

    private final PostFilter<Void> successPostFilter = createPostFilter(1, true, egressResp -> {
        egressResp.addHeader("POST", "DONE");
        return egressResp;
    });

    private final PostFilter<Void> shouldNotPostFilter = createPostFilter(2, false, egressResp -> {
        egressResp.addHeader("POST", "SHOULDNOT");
        return egressResp;
    });

    private final PostFilter<Void> emptyPostFilter = createIoPostFilter(3, true, egressResp -> Observable.empty());

    private final PostFilter<Void> doublePostFilter = createIoPostFilter(4, true, egressResp -> {
        egressResp.addHeader("POST", "DOUBLE");
        return Observable.from(egressResp, egressResp);
    });

    private final PostFilter<Void> errorPostFilter = createIoPostFilter(5, true, egressResp -> {
        egressResp.addHeader("POST", "ERROR");
        return Observable.error(new RuntimeException("post unit test"));
    });

    private final ErrorFilter<Void> errorFilter = new ErrorFilter<Void>() {
        @Override
        public Observable<EgressResponse<Void>> handleError(Throwable ex) {
            EgressResponse<Void> egressResp = EgressResponse.from(ingressErrorResp, mockRxNettyResp, null);
            egressResp.addHeader("ERROR", "TRUE");
            return Observable.just(egressResp);
        }

        @Override
        public int getOrder() {
            return 1;
        }
    };

    private final FilterStateFactory<Void> voidStateFactory = () -> null;

    @Before
    public void init() throws IOException {
        MockitoAnnotations.initMocks(this);
        processor = new FilterProcessor<Void, Void>(mockFilterStore, voidStateFactory, voidStateFactory);

        when(mockFilterStore.getFilters(ingressReq)).thenReturn(mockFilters);
        when(mockRxNettyResp.getHeaders()).thenReturn(mockRespHeaders);
    }

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicBoolean alreadyProcessedOnNext = new AtomicBoolean(false);

    private Action1<EgressResponse<Void>> onNextAssert(HttpResponseStatus status) {
        return (egressResp) -> {
            System.out.println("onNext : " + egressResp);
            if (alreadyProcessedOnNext.compareAndSet(false, true)) {
                verify(mockRxNettyResp, times(1)).setStatus(status);
            } else {
                fail("Only expecting 1 onNext");
            }
        };
    }

    private Action1<EgressResponse<Void>> onNextAssertOk() {
        return onNextAssert(HttpResponseStatus.OK);
    }

    private Action1<EgressResponse<Void>> onNextAssertError() {
        return onNextAssert(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private final Action1<Throwable> onErrorFail = (ex) -> { System.out.println("onError : " + ex); ex.printStackTrace(); fail(ex.getMessage());};
    private final Action0 onCompletedUnlatch = () -> { System.out.println("onCompleted"); latch.countDown();};

    private PreFilter<Void> createPreFilter(final int order, final boolean shouldFilter, Func1<EgressRequest<Void>, EgressRequest<Void>> behavior) {
        return new ComputationPreFilter<Void>() {
            @Override
            public EgressRequest<Void> apply(EgressRequest<Void> egressReq) {
                System.out.println("Executing preFilter : " + this);
                return behavior.call(egressReq);
            }

            @Override
            public int getOrder() {
                return order;
            }

            @Override
            public Boolean shouldFilter(EgressRequest<Void> egressReq) {
                return shouldFilter;
            }
        };
    }

    private PreFilter<Void> createIoPreFilter(final int order, final boolean shouldFilter, Func1<EgressRequest<Void>, Observable<EgressRequest<Void>>> behavior) {
        return new IoPreFilter<Void>() {
            @Override
            public Observable<Boolean> shouldFilter(EgressRequest<Void> input) {
                return Observable.just(shouldFilter);
            }

            @Override
            public Observable<EgressRequest<Void>> apply(EgressRequest<Void> input) {
                System.out.println("Executing preFilter: " + this);
                return behavior.call(input);
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    private RouteFilter<Void> createRouteFilter(Func1<EgressRequest<Void>, Observable<IngressResponse>> behavior) {
        return new IoRouteFilter<Void>() {
            @Override
            public Observable<IngressResponse> routeToIngress(EgressRequest<Void> egressReq) {
                System.out.println("Executing routeFilter : " + this);
                return behavior.call(egressReq);
            }

            @Override
            public int getOrder() {
                return 1;
            }
        };
    }

    private PostFilter<Void> createPostFilter(final int order, final boolean shouldFilter, Func1<EgressResponse<Void>, EgressResponse<Void>> behavior) {
        return new ComputationPostFilter<Void>() {
            @Override
            public EgressResponse<Void> apply(EgressResponse<Void> egressResp) {
                System.out.println("Executing postFilter : " + this);
                return behavior.call(egressResp);
            }

            @Override
            public int getOrder() {
                return order;
            }

            @Override
            public Boolean shouldFilter(EgressResponse<Void> egressReq) {
                return shouldFilter;
            }
        };
    }

    private PostFilter<Void> createIoPostFilter(final int order, final boolean shouldFilter, Func1<EgressResponse<Void>, Observable<EgressResponse<Void>>> behavior) {
        return new IoPostFilter<Void>() {
            @Override
            public Observable<Boolean> shouldFilter(EgressResponse<Void> input) {
                return Observable.just(shouldFilter);
            }

            @Override
            public Observable<EgressResponse<Void>> apply(EgressResponse<Void> input) {
                System.out.println("Executing postFilter : " + this);
                return behavior.call(input);
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    @Test(timeout=1000)
    public void testApplyOneRoute() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(new ArrayList<>());
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(new ArrayList<>());
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertOk(), onErrorFail, onCompletedUnlatch);

        latch.await();
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testApplyOnePreOneRouteOnePost() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(successPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(successPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertOk(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(1)).addHeader("POST", "DONE");
        verify(mockRespHeaders, times(0)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testApplyTwoPreOneRouteTwoPost() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(successPreFilter, successPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(successPostFilter, successPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertOk(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(2)).addHeader("POST", "DONE");
        verify(mockRespHeaders, times(0)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testShouldFilterWorks() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(successPreFilter, shouldNotPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(successPostFilter, shouldNotPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertOk(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(1)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(0)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testPreFilterIsEmpty() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(emptyPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(successPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertError(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(0)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(1)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testRouteFilterIsEmpty() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(successPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(emptyRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(successPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertError(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(0)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(1)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testPostFilterIsEmpty() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(successPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(emptyPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertError(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(0)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(1)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    //silently ignores all emissions after first
    @Test(timeout=1000)
    public void testPreFilterEmitsTwice() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(doublePreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(successPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertOk(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(1)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(0)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    //silently ignores all emissions after first
    @Test(timeout=1000)
    public void testRouteFilterEmitsTwice() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(successPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(doubleRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(successPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertOk(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(1)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(0)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    //silently ignores all emissions after first
    @Test(timeout=1000)
    public void testPostFilterEmitsTwice() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(successPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(doublePostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertOk(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(1)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(0)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testErrorInPreFilter() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(errorPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(successPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertError(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(0)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(1)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testErrorInPreAndPostFilters() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(errorPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(errorPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertError(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(0)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(1)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testErrorInRouteFilter() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(successPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(errorRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(successPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertError(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(0)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(1)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testErrorInRouteAndPostFilters() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(successPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(errorRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(errorPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertError(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(0)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(1)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }

    @Test(timeout=1000)
    public void testErrorInPostFilter() throws InterruptedException {
        when(mockFilters.getPreFilters()).thenReturn(Arrays.asList(successPreFilter));
        when(mockFilters.getRouteFilter()).thenReturn(successRouteFilter);
        when(mockFilters.getPostFilters()).thenReturn(Arrays.asList(errorPostFilter));
        when(mockFilters.getErrorFilter()).thenReturn(errorFilter);

        Observable<EgressResponse<Void>> result = processor.applyAllFilters(ingressReq, mockRxNettyResp);
        result.subscribe(onNextAssertError(), onErrorFail, onCompletedUnlatch);

        latch.await();
        verify(mockRespHeaders, times(1)).addHeader(eq("POST"), anyString());
        verify(mockRespHeaders, times(1)).addHeader(eq("ERROR"), anyString());
        assertTrue(alreadyProcessedOnNext.get());
    }
}
