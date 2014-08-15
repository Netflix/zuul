package com.netflix.zuul;

import java.util.HashMap;

public class ZuulServer {

    public static void start(int port, FilterStore<HashMap<String, Object>, HashMap<String, Object>> filterStore) {
        start(port, filterStore, DEFAULT_STATE_FACTORY);
    }

    public static <T> void start(int port, FilterStore<T, T> filterStore, FilterStateFactory<T> typeFactory) {
        start(port, filterStore, typeFactory, typeFactory);
    }

    public static <Request, Response> void start(int port, FilterStore<Request, Response> filterStore, FilterStateFactory<Request> request, FilterStateFactory<Response> response) {
        FilterProcessor<Request, Response> filterProcessor = new FilterProcessor<>(filterStore, request, response);
        NettyHttpServer<Request, Response> server = new NettyHttpServer<>(port, filterProcessor);

        server.createServer().startAndWait();
    }

    private static final DefaultStateFactory DEFAULT_STATE_FACTORY = new DefaultStateFactory();

    private static class DefaultStateFactory implements FilterStateFactory<HashMap<String, Object>> {

        @Override
        public HashMap<String, Object> create() {
            return new HashMap<String, Object>();
        }

    }
}
