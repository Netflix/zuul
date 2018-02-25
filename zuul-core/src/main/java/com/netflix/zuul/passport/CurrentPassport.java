/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.passport;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.collect.Sets;
import com.netflix.config.CachedDynamicBooleanProperty;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.zuul.context.CommonContextKeys;
import com.netflix.zuul.context.SessionContext;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CurrentPassport
{
    private static final CachedDynamicBooleanProperty COUNT_STATES = new CachedDynamicBooleanProperty(
            "zuul.passport.count.enabled", false);

    public static final AttributeKey<CurrentPassport> CHANNEL_ATTR = AttributeKey.newInstance("_current_passport");
    private static final Ticker SYSTEM_TICKER = Ticker.systemTicker();
    private static final Set<PassportState> CONTENT_STATES = Sets.newHashSet(
            PassportState.IN_REQ_CONTENT_RECEIVED,
            PassportState.IN_RESP_CONTENT_RECEIVED,
            PassportState.OUT_REQ_CONTENT_SENDING,
            PassportState.OUT_REQ_CONTENT_SENT,
            PassportState.OUT_RESP_CONTENT_SENDING,
            PassportState.OUT_RESP_CONTENT_SENT
    );
    private static final CachedDynamicBooleanProperty CONTENT_STATE_ENABLED = new CachedDynamicBooleanProperty(
            "zuul.passport.state.content.enabled", false);

    private final Ticker ticker;
    private final LinkedList<PassportItem> history;
    private final HashSet<PassportState> statesAdded;
    private final long creationTimeSinceEpochMs;

    CurrentPassport()
    {
        this(SYSTEM_TICKER);
    }

    @VisibleForTesting
    public CurrentPassport(Ticker ticker)
    {
        this.ticker = ticker;
        this.history = new LinkedList<>();
        this.statesAdded = new HashSet<>();
        this.creationTimeSinceEpochMs = System.currentTimeMillis();
    }

    public static CurrentPassport create()
    {
        if (COUNT_STATES.get()) {
            return new CountingCurrentPassport();
        }
        return new CurrentPassport();
    }

    public static CurrentPassport fromSessionContext(SessionContext ctx)
    {
        return (CurrentPassport) ctx.get(CommonContextKeys.PASSPORT);
    }

    public static CurrentPassport createForChannel(Channel ch)
    {
        CurrentPassport passport = create();
        passport.setOnChannel(ch);
        return passport;
    }

    public static CurrentPassport fromChannel(Channel ch)
    {
        CurrentPassport passport = fromChannelOrNull(ch);
        if (passport == null) {
            passport = create();
            ch.attr(CHANNEL_ATTR).set(passport);
        }
        return passport;
    }

    public static CurrentPassport fromChannelOrNull(Channel ch)
    {
        return ch.attr(CHANNEL_ATTR).get();
    }


    public void setOnChannel(Channel ch) {
        ch.attr(CHANNEL_ATTR).set(this);
    }

    public static void clearFromChannel(Channel ch) {
        ch.attr(CHANNEL_ATTR).set(null);
    }

    public PassportState getState()
    {
        return history.peekLast().getState();
    }

    public LinkedList<PassportItem> getHistory()
    {
        return history;
    }

    public void add(PassportState state)
    {
        if (! CONTENT_STATE_ENABLED.get()) {
            if (CONTENT_STATES.contains(state)) {
                // Discard.
                return;
            }
        }
        
        history.addLast(new PassportItem(state, now()));
        statesAdded.add(state);
    }

    public void addIfNotAlready(PassportState state)
    {
        if (! statesAdded.contains(state)) {
            add(state);
        }
    }

    public long calculateTimeBetweenFirstAnd(PassportState endState)
    {
        long startTime = firstTime();
        for (PassportItem item : history) {
            if (item.getState() == endState) {
                return item.getTime() - startTime;
            }
        }
        return now() - startTime;
    }

    /**
     * NOTE: This is NOT nanos since epoch. It's just since an arbitrary point in time. So only use relatively.
     */
    public long firstTime()
    {
        return history.getFirst().getTime();
    }

    public long creationTimeSinceEpochMs()
    {
        return creationTimeSinceEpochMs;
    }

    public long calculateTimeBetween(StartAndEnd sae)
    {
        if (sae.startNotFound() || sae.endNotFound()) {
            return 0;
        }
        return sae.endTime - sae.startTime;
    }

    public long calculateTimeBetweenButIfNoEndThenUseNow(StartAndEnd sae)
    {
        if (sae.startNotFound()) {
            return 0;
        }

        // If no end state found, then default to now.
        if (sae.endNotFound()) {
            sae.endTime = now();
        }

        return sae.endTime - sae.startTime;
    }

    public StartAndEnd findStartAndEndStates(PassportState startState, PassportState endState)
    {
        StartAndEnd sae = new StartAndEnd();
        for (PassportItem item : history) {
            if (item.getState() == startState) {
                sae.startTime = item.getTime();
            }
            else if (item.getState() == endState) {
                sae.endTime = item.getTime();
            }
        }
        return sae;
    }

    public StartAndEnd findFirstStartAndLastEndStates(PassportState startState, PassportState endState)
    {
        StartAndEnd sae = new StartAndEnd();
        for (PassportItem item : history) {
            if (sae.startNotFound() && item.getState() == startState) {
                sae.startTime = item.getTime();
            }
            else if (item.getState() == endState) {
                sae.endTime = item.getTime();
            }
        }
        return sae;
    }

    public StartAndEnd findLastStartAndFirstEndStates(PassportState startState, PassportState endState)
    {
        StartAndEnd sae = new StartAndEnd();
        for (PassportItem item : history) {
            if (item.getState() == startState) {
                sae.startTime = item.getTime();
            }
            else if (sae.endNotFound() && item.getState() == endState) {
                sae.endTime = item.getTime();
            }
        }
        return sae;
    }
    
    public List<StartAndEnd> findEachPairOf(PassportState startState, PassportState endState)
    {
        ArrayList<StartAndEnd> items = new ArrayList<>();

        StartAndEnd currentPair = null;

        for (PassportItem item : history) {

            if (item.getState() == startState) {
                if (currentPair == null) {
                    currentPair = new StartAndEnd();
                    currentPair.startTime = item.getTime();
                }
            }
            else if (item.getState() == endState) {
                if (currentPair != null) {
                    currentPair.endTime = item.getTime();
                    items.add(currentPair);
                    currentPair = null;
                }
            }
        }
        
        return items;
    }

    public PassportItem findState(PassportState state)
    {
        for (PassportItem item : history) {
            if (item.getState() == state) {
                return item;
            }
        }
        return null;
    }

    public PassportItem findStateBackwards(PassportState state)
    {
        Iterator itr = history.descendingIterator();
        while (itr.hasNext()) {
            PassportItem item = (PassportItem) itr.next();
            if (item.getState() == state) {
                return item;
            }
        }
        return null;
    }

    public List<PassportItem> findStates(PassportState state)
    {
        ArrayList<PassportItem> items = new ArrayList<>();
        for (PassportItem item : history) {
            if (item.getState() == state) {
                items.add(item);
            }
        }
        return items;
    }

    public List<Long> findTimes(PassportState state)
    {
        long startTick = firstTime();
        ArrayList<Long> items = new ArrayList<>();
        for (PassportItem item : history) {
            if (item.getState() == state) {
                items.add(item.getTime() - startTick);
            }
        }
        return items;
    }

    public boolean wasProxyAttempt()
    {
        // If an attempt was made to send outbound request headers on this session, then assume it was an
        // attempt to proxy.
        return findState(PassportState.OUT_REQ_HEADERS_SENDING) != null;
    }
    
    private long now()
    {
        return ticker.read();
    }

    @Override
    public String toString()
    {
        long startTime = history.size() > 0 ? firstTime() : 0;
        long now = now();
        
        StringBuilder sb = new StringBuilder();
        sb.append("CurrentPassport {");
        sb.append("start_ms=").append(creationTimeSinceEpochMs()).append(", ");
        
        sb.append('[');
        for (PassportItem item : history) {
            sb.append('+').append(item.getTime() - startTime).append('=').append(item.getState().name()).append(", ");
        }
        sb.append('+').append(now - startTime).append('=').append("NOW");
        sb.append(']');
        
        sb.append('}');
        
        return sb.toString();
    }

    @VisibleForTesting
    public static CurrentPassport parseFromToString(String text)
    {
        CurrentPassport passport = null;
        Pattern ptn = Pattern.compile("CurrentPassport \\{start_ms=\\d+, \\[(.*)\\]\\}");
        Pattern ptnState = Pattern.compile("^\\+(\\d+)=(.+)$");
        Matcher m = ptn.matcher(text);
        if (m.matches()) {
            String[] stateStrs = m.group(1).split(", ");
            MockTicker ticker = new MockTicker();
            passport = new CurrentPassport(ticker);
            for (String stateStr : stateStrs) {
                Matcher stateMatch = ptnState.matcher(stateStr);
                if (stateMatch.matches()) {
                    String stateName = stateMatch.group(2);
                    if (stateName.equals("NOW")) {
                        long startTime = passport.getHistory().size() > 0 ? passport.firstTime() : 0;
                        long now = Long.valueOf(stateMatch.group(1)) + startTime;
                        ticker.setNow(now);
                    }
                    else {
                        PassportState state = PassportState.valueOf(stateName);
                        PassportItem item = new PassportItem(state, Long.valueOf(stateMatch.group(1)));
                        passport.getHistory().add(item);
                    }
                }
            }
        }
        return passport;
    }

    private static class MockTicker extends Ticker
    {
        private long now = -1;

        @Override
        public long read()
        {
            if (now == -1) {
                throw new IllegalStateException();
            }
            return now;
        }

        public void setNow(long now)
        {
            this.now = now;
        }
    }
}

class CountingCurrentPassport extends CurrentPassport
{
    private final static BasicCounter IN_REQ_HEADERS_RECEIVED_CNT = createCounter("in_req_hdrs_rec");
    private final static BasicCounter IN_REQ_LAST_CONTENT_RECEIVED_CNT = createCounter("in_req_last_cont_rec");

    private final static BasicCounter IN_RESP_HEADERS_RECEIVED_CNT = createCounter("in_resp_hdrs_rec");
    private final static BasicCounter IN_RESP_LAST_CONTENT_RECEIVED_CNT = createCounter("in_resp_last_cont_rec");

    private final static BasicCounter OUT_REQ_HEADERS_SENT_CNT = createCounter("out_req_hdrs_sent");
    private final static BasicCounter OUT_REQ_LAST_CONTENT_SENT_CNT = createCounter("out_req_last_cont_sent");

    private final static BasicCounter OUT_RESP_HEADERS_SENT_CNT = createCounter("out_resp_hdrs_sent");
    private final static BasicCounter OUT_RESP_LAST_CONTENT_SENT_CNT = createCounter("out_resp_last_cont_sent");

    private static BasicCounter createCounter(String name)
    {
        BasicCounter counter = new BasicCounter(MonitorConfig.builder("zuul.passport." + name).build());
        DefaultMonitorRegistry.getInstance().register(counter);
        return counter;
    }

    public CountingCurrentPassport()
    {
        super();
        incrementStateCounter(getState());
    }

    @Override
    public void add(PassportState state)
    {
        super.add(state);
        incrementStateCounter(state);
    }

    private void incrementStateCounter(PassportState state)
    {
        switch (state) {
            case IN_REQ_HEADERS_RECEIVED:
                IN_REQ_HEADERS_RECEIVED_CNT.increment();
                break;
            case IN_REQ_LAST_CONTENT_RECEIVED:
                IN_REQ_LAST_CONTENT_RECEIVED_CNT.increment();
                break;
            case OUT_REQ_HEADERS_SENT:
                OUT_REQ_HEADERS_SENT_CNT.increment();
                break;
            case OUT_REQ_LAST_CONTENT_SENT:
                OUT_REQ_LAST_CONTENT_SENT_CNT.increment();
                break;
            case IN_RESP_HEADERS_RECEIVED:
                IN_RESP_HEADERS_RECEIVED_CNT.increment();
                break;
            case IN_RESP_LAST_CONTENT_RECEIVED:
                IN_RESP_LAST_CONTENT_RECEIVED_CNT.increment();
                break;
            case OUT_RESP_HEADERS_SENT:
                OUT_RESP_HEADERS_SENT_CNT.increment();
                break;
            case OUT_RESP_LAST_CONTENT_SENT:
                OUT_RESP_LAST_CONTENT_SENT_CNT.increment();
                break;
        }
    }
}
