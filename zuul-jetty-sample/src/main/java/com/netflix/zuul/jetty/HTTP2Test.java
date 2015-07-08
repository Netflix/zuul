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
package com.netflix.zuul.jetty;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.IStream;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.client.AbstractTest;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.PriorityFrame;
import org.eclipse.jetty.http2.frames.PushPromiseFrame;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Jetty;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.server.Dispatcher;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HTTP2Test extends AbstractTest
{
//    public static void main(String[] args)
//    {
//        try {
//            HTTP2Test test = new HTTP2Test();
//            test.testRequestNoContentResponseContent();
//            test.dispose();
//
//            test = new HTTP2Test();
//            test.testMultipleRequests();
//            test.dispose();
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    //@Test
    public void testRequestNoContentResponseContent() throws Exception
    {
        final byte[] content = "Hello World!".getBytes(StandardCharsets.UTF_8);
        start(new HttpServlet()
        {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
            {
                resp.getOutputStream().write(content);
            }
        });

        Session session = newClient(new Session.Listener.Adapter());

        HttpFields fields = new HttpFields();
        MetaData.Request metaData = newRequest("GET", fields);
        HeadersFrame frame = new HeadersFrame(1, metaData, null, true);
        final CountDownLatch latch = new CountDownLatch(2);
        session.newStream(frame, new Promise.Adapter<Stream>(), new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                Assert.assertTrue(stream.getId() > 0);

                Assert.assertFalse(frame.isEndStream());
                Assert.assertEquals(stream.getId(), frame.getStreamId());
                Assert.assertTrue(frame.getMetaData().isResponse());
                MetaData.Response response = (MetaData.Response)frame.getMetaData();
                Assert.assertEquals(200, response.getStatus());

                latch.countDown();
            }

            @Override
            public void onData(Stream stream, DataFrame frame, Callback callback)
            {
                Assert.assertTrue(frame.isEndStream());
                Assert.assertEquals(ByteBuffer.wrap(content), frame.getData());

                callback.succeeded();
                latch.countDown();
            }
        });

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    //@Test
    public void testMultipleRequests() throws Exception
    {
        final String downloadBytes = "X-Download";
        start(new HttpServlet()
        {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
            {
                int download = request.getIntHeader(downloadBytes);
                byte[] content = new byte[download];
                new Random().nextBytes(content);
                response.getOutputStream().write(content);
            }
        });

        int requests = 20;
        Session session = newClient(new Session.Listener.Adapter());

        Random random = new Random();
        HttpFields fields = new HttpFields();
        fields.putLongField(downloadBytes, random.nextInt(128 * 1024));
        fields.put("User-Agent", "HTTP2Client/" + Jetty.VERSION);
        MetaData.Request metaData = newRequest("GET", fields);
        HeadersFrame frame = new HeadersFrame(1, metaData, null, true);
        final CountDownLatch latch = new CountDownLatch(requests);
        for (int i = 0; i < requests; ++i)
        {
            session.newStream(frame, new Promise.Adapter<Stream>(), new Stream.Listener.Adapter()
            {
                @Override
                public void onData(Stream stream, DataFrame frame, Callback callback)
                {
                    callback.succeeded();
                    if (frame.isEndStream())
                        latch.countDown();
                }
            });
        }

        Assert.assertTrue(latch.await(requests, TimeUnit.SECONDS));
    }


    //@Test
    public void testPush() throws Exception
    {
        final byte[] content = "Hello World!".getBytes(StandardCharsets.UTF_8);
        final byte[] pushContent = "Hello Push World!".getBytes(StandardCharsets.UTF_8);

        // Setup test server.
        start(new ServerSessionListener.Adapter()
        {
            @Override
            public Stream.Listener onNewStream(Stream stream, HeadersFrame frame)
            {
                // TODO - we're currently assuming that the receieved frame is Headers.

                // First send a push promise to client.
                stream.push(new PushPromiseFrame(stream.getId(), 0, frame.getMetaData()), new Promise<Stream>()
                {
                    @Override
                    public void succeeded(Stream pushStream)
                    {
                        // Now that the push promise has been sent, also send the corresponding response data frame on the
                        // newly created stream.
                        DataFrame ppDataFrame = new DataFrame(pushStream.getId(), ByteBuffer.wrap(pushContent), true);
                        pushStream.data(ppDataFrame, new Callback()
                        {
                            @Override
                            public void succeeded() {
                                System.out.println("Successfully sent push promise's data frame.");
                            }

                            @Override
                            public void failed(Throwable x) {
                                System.err.println("Could not send push promise's data frame!");
                            }
                        });
                    }

                    @Override
                    public void failed(Throwable x) {
                        System.err.println("Could not push frame!");
                        x.printStackTrace();
                    }
                }, new Stream.Listener.Adapter()); // TODO: handle reset from the client ?


                // Send a response to the original request.
                HttpFields fields = new HttpFields();
                fields.add(HttpHeader.CONTENT_TYPE, "text/plain");
                MetaData responseMetaData = new MetaData.Response(HttpVersion.HTTP_2, 200, fields);
                stream.headers(new HeadersFrame(stream.getId(), responseMetaData, null, false), new Callback() {
                    @Override
                    public void succeeded() {
                        System.out.println("Successfully sent response header frame.");
                    }

                    @Override
                    public void failed(Throwable x) {
                        System.err.println("Could not send response header frame!");
                        x.printStackTrace();
                    }
                });
                DataFrame dataFrame = new DataFrame(stream.getId(), ByteBuffer.wrap(content), true);
                stream.data(dataFrame, new Callback()
                {
                    @Override
                    public void succeeded() {
                        System.out.println("Successfully sent response data frame.");
                    }

                    @Override
                    public void failed(Throwable x) {
                        System.err.println("Could not send response data frame!");
                        x.printStackTrace();
                    }
                });

                // TODO - return a Stream.Listener that responds to further events on this Stream (ie. data frames of request body).
                return new Stream.Listener.Adapter() {
                    @Override
                    public void onData(Stream stream, DataFrame frame, Callback callback) {
                        super.onData(stream, frame, callback);
                    }
                };
            }
        });


        Session session = newClient(new Session.Listener.Adapter());

        HttpFields fields = new HttpFields();
        MetaData.Request metaData = newRequest("GET", fields);
        HeadersFrame frame = new HeadersFrame(1, metaData, null, true);
        final CountDownLatch latch = new CountDownLatch(4);
        session.newStream(frame, new Promise.Adapter<Stream>(), new Stream.Listener.Adapter()
        {
            @Override
            public void onHeaders(Stream stream, HeadersFrame frame)
            {
                Assert.assertEquals(1, stream.getId());

                Assert.assertFalse(frame.isEndStream());
                Assert.assertEquals(stream.getId(), frame.getStreamId());
                Assert.assertTrue(frame.getMetaData().isResponse());
                MetaData.Response response = (MetaData.Response)frame.getMetaData();
                Assert.assertEquals(200, response.getStatus());
                Assert.assertEquals("text/plain", frame.getMetaData().getFields().get("Content-Type"));

                latch.countDown();
            }

            @Override
            public void onData(Stream stream, DataFrame frame, Callback callback)
            {
                Assert.assertTrue(frame.isEndStream());

                if (stream.getId() == 1) {
                    Assert.assertEquals(ByteBuffer.wrap(content), frame.getData());
                }
                else if (stream.getId() == 2) {
                    Assert.assertEquals(ByteBuffer.wrap(pushContent), frame.getData());
                }
                else {
                    Assert.fail("Unexpected stream ID!");
                }

                callback.succeeded();
                latch.countDown();
            }

            @Override
            public Stream.Listener onPush(Stream stream, PushPromiseFrame frame)
            {
                Assert.assertEquals(2, stream.getId());


                latch.countDown();
                return this;
            }
        });

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
