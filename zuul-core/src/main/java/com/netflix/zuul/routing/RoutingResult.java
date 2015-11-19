package com.netflix.zuul.routing;


import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.netflix.util.Pair;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Created by chrisp on 5/18/15.
 */
public class RoutingResult {

    private final ImmutableList.Builder<Pair<String, Route>> assignedRoutes = new ImmutableList.Builder<>();

    private Route fallback = null;
    private Route assigned = null;

    public RoutingResult() {
    }

    public void assign(Route route, String assignedBy) {
        this.assignedRoutes.add(new Pair(assignedBy, route));
        if (!route.isDryRun()) {
            if (route.shouldFallback()) {
                this.fallback = assigned;
            }
            this.assigned = route;
        }
    }

    public Route getAssigned() {
        return assigned;
    }

    public List<Pair<String, Route>> getTrace() {
        return assignedRoutes.build();
    }

    public Optional<Route> getFallback() {
        return this.assigned.shouldFallback() ? Optional.fromNullable(fallback) : Optional.absent();
    }

    @Override
    public String toString() {
        return "RoutingResult{" +
                "assignedRoutes=" + assignedRoutes.build() +
                ", fallback=" + fallback +
                ", assigned=" + assigned +
                '}';
    }

    public static class TestUnit {

        private RoutingResult routingResult;

        @Before
        public void setup() {
            routingResult = new RoutingResult();
        }


        @Test
        public void singleRoute() {
            Route r = new Route("test", true);
            routingResult.assign(r, "test");

            assertThat(routingResult.getAssigned(), is(r));
            assertThat(routingResult.getFallback().isPresent(), is(false));
        }

        @Test
        public void singleRouteNoFallback() {
            Route r = new Route("test", false);
            routingResult.assign(r, "test");
            assertThat(routingResult.getAssigned(), is(r));
            assertThat(routingResult.getFallback().isPresent(), is(false));
        }

        @Test
        public void multipleShouldNotFallback() {
            Route r1 = new Route("test", true);
            Route r2 = new Route("test2", false);
            routingResult.assign(r1, "test");
            routingResult.assign(r2, "test");

            assertThat(routingResult.getAssigned(), is(r2));
            assertThat(routingResult.getFallback(), is(Optional.absent()));
        }

        @Test
        public void multipleShouldFallback() {
            Route r1 = new Route("test", true);
            Route r2 = new Route("test2", true);
            routingResult.assign(r1, "test");
            routingResult.assign(r2, "test");

            assertThat(routingResult.getAssigned(), is(r2));
            assertThat(routingResult.getFallback().isPresent(), is(true));
            assertThat(routingResult.getFallback().get(), is(r1));
        }

        @Test
        public void noFallbackStreak() {
            Route r1 = new Route("test", false);
            Route r2 = new Route("test2", false);
            Route r3 = new Route("test3", false);
            Route r4 = new Route("test4", true);
            routingResult.assign(r1, "test");
            routingResult.assign(r2, "test");
            routingResult.assign(r3, "test");
            routingResult.assign(r4, "test");

            assertThat(routingResult.getAssigned(), is(r4));
            assertThat(routingResult.getFallback().isPresent(), is(true));
            assertThat(routingResult.getFallback().get(), is(r3));
        }

        @Test
        public void dryRun() {
            Route r1 = new Route("test", true);
            Route r2 = new Route("test2", true);
            Route r3 = new Route("test3", true, true);
            routingResult.assign(r1, "test");
            routingResult.assign(r2, "test");
            routingResult.assign(r3, "test");

            assertThat(routingResult.getAssigned(), is(r2));
            assertThat(routingResult.getFallback().isPresent(), is(true));
            assertThat(routingResult.getFallback().get(), is(r1));
        }

        @Test
        public void dryRunInBetween() {
            Route r1 = new Route("test", true);
            Route r2 = new Route("test2", false, true);
            Route r3 = new Route("test3", true);
            routingResult.assign(r1, "test");
            routingResult.assign(r2, "test");
            routingResult.assign(r3, "test");

            assertThat(routingResult.getAssigned(), is(r3));
            assertThat(routingResult.getFallback().isPresent(), is(true));
            assertThat(routingResult.getFallback().get(), is(r1));
        }

    }



}
