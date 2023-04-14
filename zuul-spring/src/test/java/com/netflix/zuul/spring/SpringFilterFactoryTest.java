package com.netflix.zuul.spring;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.netflix.zuul.Filter;
import com.netflix.zuul.filters.BaseFilter;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import com.netflix.zuul.message.ZuulMessage;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import rx.Observable;

/**
 * @author Justin Guerra
 * @since 4/14/23
 */
@SpringBootTest(classes = ZuulSpringAutoConfiguration.class)
class SpringFilterFactoryTest {

    @Autowired
    private SpringFilterFactory factory;

    @Test
    public void createFilter() {
        ZuulFilter<?, ?> zuulFilter1 = factory.newInstance(TestFilter.class);
        ZuulFilter<?, ?> zuulFilter2 = factory.newInstance(TestFilter.class);

        assertSame(zuulFilter1, zuulFilter2);

        assertTrue(zuulFilter1.shouldFilter(null));
        ZuulMessage first = zuulFilter1.applyAsync(null).toBlocking().first();
        assertNotNull(first);
    }

    @Filter(order = 1, type = FilterType.INBOUND)
    public static class TestFilter extends BaseFilter<HttpRequestMessage, HttpResponseMessage> {
        @Override
        public boolean shouldFilter(HttpRequestMessage msg) {
            return true;
        }

        @Override
        public Observable<HttpResponseMessage> applyAsync(HttpRequestMessage input) {
            return Observable.just(Mockito.mock(HttpResponseMessage.class));
        }
    }
}