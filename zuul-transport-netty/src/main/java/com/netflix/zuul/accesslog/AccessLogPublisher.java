/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.accesslog;


import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

/**
 * An abstraction to publish access logs asynchronously.
 *
 * @author Nitesh Kant
 */
public class AccessLogPublisher {

    private static final DynamicBooleanProperty DISABLE = DynamicPropertyFactory.getInstance()
            .getBooleanProperty("zuul.access.log.disable", false);

    private static final Logger logger = LoggerFactory.getLogger("ACCESS");

    private PublishSubject<AccessRecord> recordSubject = PublishSubject.create();

    public AccessLogPublisher() {
        this(Schedulers.io());
    }

    public AccessLogPublisher(Scheduler schedulerForLogWriting) {
        recordSubject.observeOn(schedulerForLogWriting).subscribe(record -> {
            if (logger.isInfoEnabled()) {
                logger.info(record.toLogLine());
            }
        });
    }

    public void publish(AccessRecord record) {
        if (! DISABLE.get()) {
            recordSubject.onNext(record);
        }
    }
}