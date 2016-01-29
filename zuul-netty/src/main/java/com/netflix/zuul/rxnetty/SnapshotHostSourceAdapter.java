package com.netflix.zuul.rxnetty;

import io.reactivex.netty.client.Host;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func0;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SnapshotHostSourceAdapter {

    public static Observable<Host> toHostStream(Func0<List<SocketAddress>> sourceFactory,
                                                Observable<Long> pollingSource) {
        return pollingSource.onBackpressureDrop()
                            .scan(new HashMap<SocketAddress, Host>(), (lifecycles, tick) -> lifecycles)
                            .skip(1) /*Scan sends the initial value first, which isn't required for us*/
                            .flatMap(lifecycles -> {
                                List<SocketAddress> hosts = sourceFactory.call();

                                final Set<SocketAddress> hostsToRemove = new HashSet<>(lifecycles.keySet());
                                final Set<Host> hostsAdded = new HashSet<>();

                                for (SocketAddress host : hosts) {
                                    hostsToRemove.remove(host);

                                    Host lastSeen = lifecycles.get(host);
                                    if (null == lastSeen) {
                                        Host h = new Host(host, PublishSubject.create());
                                        hostsAdded.add(h);
                                        lifecycles.put(host, h);
                                    }
                                }

                                for (SocketAddress socketAddress : hostsToRemove) {
                                    Host removed = lifecycles.remove(socketAddress);
                                    ((PublishSubject) removed.getCloseNotifier()).onCompleted();
                                }

                                return Observable.from(hostsAdded);

                            }, 1 /*Have at most one pending calculation*/);
    }

    public static Observable<Host> toHostStream(Func0<List<SocketAddress>> sourceFactory) {
        return toHostStream(sourceFactory, Observable.interval(30, TimeUnit.SECONDS));
    }

    public static class UnitTest {

        @Rule
        public AdapterRule rule = new AdapterRule();

        @Test(timeout = 60000)
        public void testAdd() {
            rule.setNextHostsToEmit(Arrays.asList(1000, 1001));
            rule.subscribeToStream();

            rule.nextTick();

            List<Host> hosts = rule.getHosts(2);
            rule.assertNoHostsCompleted(hosts);
        }

        @Test(timeout = 60000)
        public void testRemove() {
            rule.setNextHostsToEmit(Collections.singletonList(1000));
            rule.subscribeToStream();

            rule.nextTick();

            List<Host> hosts = rule.getHosts(1);
            rule.assertNoHostsCompleted(hosts);

            rule.setNextHostsToEmit(Collections.emptyList());
            rule.nextTick();

            rule.assertAllHostsCompleted(hosts);
        }

        @Test(timeout = 60000)
        public void testMultiSnapshotsAllInstancesChanged() {
            rule.setNextHostsToEmit(Arrays.asList(1000, 1001));
            rule.subscribeToStream();

            rule.nextTick();

            List<Host> hosts = rule.getHosts(2);
            rule.assertNoHostsCompleted(hosts);

            rule.setNextHostsToEmit(Arrays.asList(1002, 1003));
            rule.nextTick();

            rule.assertAllHostsCompleted(hosts);
            List<Host> hosts2 = rule.getHosts(4);
            hosts2.removeAll(hosts);

            rule.assertNoHostsCompleted(hosts2);
        }

        @Test(timeout = 60000)
        public void testMultiSnapshotsSomeInstancesChanged() {
            rule.setNextHostsToEmit(Arrays.asList(1000, 1001));
            rule.subscribeToStream();

            rule.nextTick();

            List<Host> hosts = rule.getHosts(2);
            rule.assertNoHostsCompleted(hosts);

            rule.setNextHostsToEmit(Arrays.asList(1001, 1003));
            rule.nextTick();

            List<Host> removed = rule.assertHostsCompleted(hosts, 1000);
            List<Host> hosts2 = rule.getHosts(3);
            hosts2.removeAll(removed);

            rule.assertNoHostsCompleted(hosts2);
        }


        public static class AdapterRule extends ExternalResource {

            private TestSubscriber<Host> subscriber;
            private TestScheduler scheduler;
            private List<SocketAddress> nextHostsToEmit;

            @Override
            public Statement apply(final Statement base, Description description) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        subscriber = new TestSubscriber<>();
                        scheduler = Schedulers.test();
                        nextHostsToEmit = new ArrayList<>();
                        base.evaluate();
                    }
                };
            }

            public void nextTick() {
                scheduler.advanceTimeBy(1, TimeUnit.DAYS);
            }

            public void setNextHostsToEmit(List<Integer> ports) {
                List<SocketAddress> newVal = ports.stream().map(InetSocketAddress::new).collect(Collectors.toList());
                nextHostsToEmit.clear();
                nextHostsToEmit.addAll(newVal);
            }

            public void subscribeToStream() {
                SnapshotHostSourceAdapter.toHostStream(() -> nextHostsToEmit,
                                                       Observable.interval(1, TimeUnit.DAYS, scheduler))
                                         .subscribe(subscriber);
            }

            public List<Host> getHosts(int expectedCount) {
                subscriber.assertNoErrors();
                subscriber.assertValueCount(expectedCount);
                return new ArrayList<>(subscriber.getOnNextEvents());
            }

            public void assertNoHostsCompleted(List<Host> hosts) {
                for (Host host : hosts) {
                    TestSubscriber<Void> s = new TestSubscriber<>();
                    host.getCloseNotifier().subscribe(s);
                    s.assertNoTerminalEvent();
                }
            }

            public void assertAllHostsCompleted(List<Host> hosts) {
                for (Host host : hosts) {
                    assertHostCompleted(host);
                }
            }

            protected void assertHostCompleted(Host host) {
                TestSubscriber<Void> s = new TestSubscriber<>();
                host.getCloseNotifier().subscribe(s);
                s.assertCompleted();
            }

            public List<Host> assertHostsCompleted(List<Host> hosts, int... completedPorts) {
                List<Host> toReturn = new ArrayList<>();

                for (int completedPort : completedPorts) {
                    for (Host host : hosts) {
                        if (((InetSocketAddress) host.getHost()).getPort() == completedPort) {
                            assertHostCompleted(host);
                            toReturn.add(host);
                        }
                    }
                }

                return toReturn;
            }
        }

    }
}
