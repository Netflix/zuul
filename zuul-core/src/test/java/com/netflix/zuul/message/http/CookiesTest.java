/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.zuul.message.http;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CookiesTest {
    @Test
    public void getAll_returnsAllValues() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        List<Cookie> allCookies = cookies.getAllCookies();

        assertThat(allCookies).containsExactly(
                new DefaultCookie("robots", "humans"), new DefaultCookie("robots", "cyborgs"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void getAll_returnsAllValues_deprecated() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        List<io.netty.handler.codec.http.Cookie> allCookies = cookies.getAll();

        assertThat(allCookies).containsExactly(
                new io.netty.handler.codec.http.DefaultCookie("robots", "humans"),
                new io.netty.handler.codec.http.DefaultCookie("robots", "cyborgs"));
    }

    @Test
    public void getAll_returnsEmpty() {
        Cookies cookies = new Cookies();

        List<Cookie> allCookies = cookies.getAllCookies();

        assertThat(allCookies).isEmpty();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void getAll_returnsEmpty_deprecated() {
        Cookies cookies = new Cookies();

        List<io.netty.handler.codec.http.Cookie> allCookies = cookies.getAll();

        assertThat(allCookies).isEmpty();
    }

    @Test
    public void getCookies_present() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("aliens", "predators"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));


        List<Cookie> allCookies = cookies.getCookies("robots");

        assertThat(allCookies).containsExactly(
                new DefaultCookie("robots", "humans"), new DefaultCookie("robots", "cyborgs"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void getCookies_present_deprecated() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("aliens", "predators"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        List<io.netty.handler.codec.http.Cookie> allCookies = cookies.get("robots");

        assertThat(allCookies).containsExactly(
                new io.netty.handler.codec.http.DefaultCookie("robots", "humans"),
                new io.netty.handler.codec.http.DefaultCookie("robots", "cyborgs"));
    }

    @Test
    public void getCookies_absentReturnsEmpty() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        List<Cookie> allCookies = cookies.getCookies("aliens");

        assertThat(allCookies).isEmpty();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void getCookies_absentReturnsNull_deprecated() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        List<io.netty.handler.codec.http.Cookie> allCookies = cookies.get("aliens");

        assertThat(allCookies).isNull();
    }

    @Test
    public void getFirstCookie_present() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        Cookie cookie = cookies.getFirstCookie("robots");

        assertThat(cookie).isEqualTo(new DefaultCookie("robots", "humans"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void getFirstCookie_present_deprecated() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        io.netty.handler.codec.http.Cookie cookie = cookies.getFirst("robots");

        assertThat(cookie).isEqualTo(new io.netty.handler.codec.http.DefaultCookie("robots", "humans"));
    }

    @Test
    public void getFirstCookie_absentReturnsNull() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        Cookie cookie = cookies.getFirstCookie("aliens");

        assertThat(cookie).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void getFirstCookie_absentReturnsNull_deprecated() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        io.netty.handler.codec.http.Cookie cookie = cookies.getFirst("aliens");

        assertThat(cookie).isNull();
    }

    @Test
    public void getFirstValue_present() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        String value = cookies.getFirstValue("robots");

        assertThat(value).isEqualTo("humans");
    }

    @Test
    public void getFirstValue_absentReturnsNull() {
        Cookies cookies = new Cookies();
        cookies.add(new DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        String value = cookies.getFirstValue("aliens");

        assertThat(value).isNull();
    }

    // This checks that mixed cookie types can convert
    @Test
    @SuppressWarnings("deprecation")
    public void addMixedDeprecated() {
        Cookies cookies = new Cookies();
        cookies.add(new io.netty.handler.codec.http.DefaultCookie("robots", "humans"));
        cookies.add(new DefaultCookie("robots", "cyborgs"));

        List<Cookie> allCookies = cookies.getAllCookies();

        assertThat(allCookies).containsExactly(
                new DefaultCookie("robots", "humans"),
                new io.netty.handler.codec.http.DefaultCookie("robots", "cyborgs"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void convertImplOtherThanDefaultCookie() {
        Cookie cookie = mock(Cookie.class);
        when(cookie.name()).thenReturn("robots");
        when(cookie.value()).thenReturn("humans");
        when(cookie.domain()).thenReturn("domain");
        when(cookie.isHttpOnly()).thenReturn(true);
        when(cookie.isSecure()).thenReturn(true);
        when(cookie.path()).thenReturn("/path");

        io.netty.handler.codec.http.Cookie oldCookie = Cookies.convert(cookie);

        assertThat(oldCookie.name()).isEqualTo("robots");
        assertThat(oldCookie.value()).isEqualTo("humans");
        assertThat(oldCookie.domain()).isEqualTo("domain");
        assertThat(oldCookie.isSecure()).isEqualTo(true);
        assertThat(oldCookie.isHttpOnly()).isEqualTo(true);
        assertThat(oldCookie.path()).isEqualTo("/path");
    }
}
