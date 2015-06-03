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