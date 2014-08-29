package com.netflix.zuul;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilderSuite;
import com.netflix.karyon.transport.http.ServerBootstrap;
import io.netty.buffer.ByteBuf;

/**
 * Temporary class till karyon provides the mechanism of programmatically adding annotations as opposed to just via
 * annotations.
 *
 * @author Nitesh Kant
 */
class ZuulBootstrap {

    private final Class mainClass;
    private final LifecycleInjectorBuilderSuite suite;

    ZuulBootstrap(Class mainClass) {
        this.mainClass = mainClass;
        suite = null;
    }

    ZuulBootstrap(Class mainClass, Module... additionalModules) {
        this.mainClass = mainClass;
        suite = null != additionalModules ? builder -> builder.withAdditionalModules(additionalModules) : null;
    }

    void startAndAwait() {
        Injector injector = LifecycleInjector.bootstrap(mainClass, suite);
        TypeLiteral<ServerBootstrap<ByteBuf, ByteBuf>> bootstrapTypeLiteral = new TypeLiteral<ServerBootstrap<ByteBuf, ByteBuf>>() {};
        ServerBootstrap<ByteBuf, ByteBuf> serverBootstrap = injector.getInstance(Key.get(bootstrapTypeLiteral));
        try {
            serverBootstrap.startServerAndWait();
        } catch (Exception e) {
            throw new RuntimeException("all hell hath broken loose", e);
            // TODO why is there a checked exception here?!
        }
    }
}
