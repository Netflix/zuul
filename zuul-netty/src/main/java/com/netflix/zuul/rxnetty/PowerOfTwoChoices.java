package com.netflix.zuul.rxnetty;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.client.ConnectionProvider;
import io.reactivex.netty.client.Host;
import io.reactivex.netty.client.HostConnector;
import io.reactivex.netty.client.loadbalancer.HostHolder;
import io.reactivex.netty.client.loadbalancer.LoadBalancingStrategy;
import io.reactivex.netty.client.loadbalancer.NoHostsAvailableException;
import io.reactivex.netty.events.EventPublisher;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import rx.Observable;
import rx.observers.TestSubscriber;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static java.lang.Integer.*;

class PowerOfTwoChoices implements LoadBalancingStrategy<ByteBuf, ByteBuf> {

    private final HttpClientMetrics clientMetrics;

    PowerOfTwoChoices(HttpClientMetrics clientMetrics) {
        this.clientMetrics = clientMetrics;
    }

    @Override
    public ConnectionProvider<ByteBuf, ByteBuf> newStrategy(final List<HostHolder<ByteBuf, ByteBuf>> hosts) {
        clientMetrics.setHostsInPool(hosts.size());

        return new ConnectionProvider<ByteBuf, ByteBuf>() {

            private final Random rand = new Random();

            @Override
            public Observable<Connection<ByteBuf, ByteBuf>> newConnectionRequest() {
                HostHolder<ByteBuf, ByteBuf> selected;
                if (hosts.isEmpty()) {
                    clientMetrics.noUsableHostsFound();
                    return Observable.error(NoHostsAvailableException.EMPTY_INSTANCE);
                }
                else if (hosts.size() == 1) {
                    HostHolder<ByteBuf, ByteBuf> holder = hosts.get(0);
                    HttpClientListenerImpl eventListener = (HttpClientListenerImpl) holder.getEventListener();
                    if (eventListener.isUnusable(eventListener.getWeight())) {
                        clientMetrics.noUsableHostsFound();
                        return Observable.error(new NoHostsAvailableException("No usable hosts found."));
                    }
                    selected = holder;
                }
                else {
                    selected = selectNext();
                    if (null == selected) {
                        clientMetrics.noUsableHostsFound();
                        return Observable.error(new NoHostsAvailableException("No usable hosts found after 3 tries."));
                    }
                }

                return selected.getConnector().getConnectionProvider().newConnectionRequest();
            }

            private HostHolder<ByteBuf, ByteBuf> selectNext() {
                for (int i = 0; i < 5; i++) {
                    int pos  = rand.nextInt(hosts.size());
                    HostHolder<ByteBuf, ByteBuf> first  = hosts.get(pos);
                    int pos2 = (rand.nextInt(hosts.size() - 1) + pos + 1) % hosts.size();
                    HostHolder<ByteBuf, ByteBuf> second = hosts.get(pos2);

                    int w1 = ((HttpClientListenerImpl) first.getEventListener()).getWeight();
                    int w2 = ((HttpClientListenerImpl) second.getEventListener()).getWeight();

                    if (w1 > w2) {
                        return first;
                    } else if (w1 < w2) {
                        return second;
                    } else if (((HttpClientListenerImpl) first.getEventListener()).isUnusable(w1)) {
                        clientMetrics.foundTwoUnusableHosts();
                    } else {
                        return first;
                    }
                }
                return null;
            }
        };
    }

    @Override
    public HostHolder<ByteBuf, ByteBuf> toHolder(HostConnector<ByteBuf, ByteBuf> connector) {
        return new HostHolder<>(connector, new HttpClientListenerImpl());
    }

    public static class UnitTest {

        @Rule
        public final StrategyRule rule = new StrategyRule();

        @Test
        public void testNoHosts() {
            ConnectionProvider<ByteBuf, ByteBuf> cp = rule.strategy.newStrategy(Collections.emptyList());
            TestSubscriber<Connection<ByteBuf, ByteBuf>> sub = new TestSubscriber<>();
            cp.newConnectionRequest().subscribe(sub);

            sub.awaitTerminalEvent();
            sub.assertError(NoHostsAvailableException.class);
            Assert.assertEquals("Unexpected number of hosts in the pool.", 0, rule.clientMetrics.getHostsInPool().get());
            Assert.assertEquals("Unexpected number of no usable hosts count.", 1,
                                rule.clientMetrics.getNoUsableHosts().count());
        }

        @Test
        public void testSingleUnusableHost() {
            ConnectionProvider<ByteBuf, ByteBuf> cp = rule.strategy.newStrategy(rule.newHostStream(MIN_VALUE));
            TestSubscriber<Connection<ByteBuf, ByteBuf>> sub = new TestSubscriber<>();
            cp.newConnectionRequest().subscribe(sub);

            sub.awaitTerminalEvent();
            sub.assertError(NoHostsAvailableException.class);

            Assert.assertEquals("Unexpected number of hosts in the pool.", 1, rule.clientMetrics.getHostsInPool().get());
            Assert.assertEquals("Unexpected number of Unusable hosts found count.", 0,
                                rule.clientMetrics.getFoundUnusableHosts().count());
            Assert.assertEquals("Unexpected number of no usable hosts count.", 1,
                                rule.clientMetrics.getNoUsableHosts().count());
        }

        @Test
        public void testMultipleUnusableHost() {
            ConnectionProvider<ByteBuf, ByteBuf> cp = rule.strategy.newStrategy(rule.newHostStream(MIN_VALUE,
                                                                                                   MIN_VALUE));
            TestSubscriber<Connection<ByteBuf, ByteBuf>> sub = new TestSubscriber<>();
            cp.newConnectionRequest().subscribe(sub);

            sub.awaitTerminalEvent();
            sub.assertError(NoHostsAvailableException.class);

            Assert.assertEquals("Unexpected number of hosts in the pool.", 2, rule.clientMetrics.getHostsInPool().get());
            Assert.assertEquals("Unexpected number of Unusable hosts found count.", 5,
                                rule.clientMetrics.getFoundUnusableHosts().count());
            Assert.assertEquals("Unexpected number of no usable hosts count.", 1,
                                rule.clientMetrics.getNoUsableHosts().count());
        }

        @Test
        public void testUsableAndUnusable() {
            ConnectionProvider<ByteBuf, ByteBuf> cp = rule.strategy.newStrategy(rule.newHostStream(10,
                                                                                                   MIN_VALUE));
            TestSubscriber<Connection<ByteBuf, ByteBuf>> sub = new TestSubscriber<>();
            cp.newConnectionRequest().subscribe(sub);

            sub.awaitTerminalEvent();
            sub.assertNoErrors();

            Assert.assertEquals("Unexpected number of hosts in the pool.", 2, rule.clientMetrics.getHostsInPool().get());
            Assert.assertEquals("Unexpected number of Unusable hosts found count.", 0,
                                rule.clientMetrics.getFoundUnusableHosts().count());
            Assert.assertEquals("Unexpected number of no usable hosts count.", 0,
                                rule.clientMetrics.getNoUsableHosts().count());
        }

        public static class StrategyRule extends ExternalResource {

            private PowerOfTwoChoices strategy;
            private HttpClientMetrics clientMetrics;

            @Override
            public Statement apply(final Statement base, Description description) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        clientMetrics = new HttpClientMetrics(StrategyRule.this.toString());
                        strategy = new PowerOfTwoChoices(clientMetrics);
                        base.evaluate();
                    }
                };
            }

            public List<HostHolder<ByteBuf, ByteBuf>> newHostStream(int... weights) {
                List<HostHolder<ByteBuf, ByteBuf>> toReturn = new ArrayList<>();
                for (int weight : weights) {
                    ConnectionProvider<ByteBuf, ByteBuf> dummy = Observable::empty;
                    Host h = new Host(new InetSocketAddress(0));
                    EventPublisher publisher = () -> false;
                    HostConnector<ByteBuf, ByteBuf> connector = new HostConnector<>(h, dummy, null, publisher, null);
                    toReturn.add(new HostHolder<>(connector, new HttpClientListenerImpl() {
                        @Override
                        public int getWeight() {
                            return weight;
                        }
                    }));
                }
                return toReturn;
            }
        }

    }
}
