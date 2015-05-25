/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

import org.junit.Assert;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Bill Gallagher
 */
public final class OriginTest {

    @Test
    public void testOrigin() throws MalformedURLException {
        Origin origin = new Origin("http", "twitter.com");
        Assert.assertEquals("http", origin.getScheme());
        Assert.assertEquals("twitter.com", origin.getHost());
        Assert.assertEquals(80, origin.getPort());
        Assert.assertEquals("http://twitter.com:80", origin.toString());

        origin = new Origin("https", "twitter.com");
        Assert.assertEquals("https", origin.getScheme());
        Assert.assertEquals("twitter.com", origin.getHost());
        Assert.assertEquals(443, origin.getPort());
        Assert.assertEquals("https://twitter.com:443", origin.toString());

        origin = new Origin("http", "twitter.com", 8000);
        Assert.assertEquals("http", origin.getScheme());
        Assert.assertEquals("twitter.com", origin.getHost());
        Assert.assertEquals(8000, origin.getPort());
        Assert.assertEquals("http://twitter.com:8000", origin.toString());

        origin = new Origin("https", "twitter.com", 8443);
        Assert.assertEquals("https", origin.getScheme());
        Assert.assertEquals("twitter.com", origin.getHost());
        Assert.assertEquals(8443, origin.getPort());
        Assert.assertEquals("https://twitter.com:8443", origin.toString());

        origin = new Origin(new URL("http://twitter.com"));
        Assert.assertEquals("http", origin.getScheme());
        Assert.assertEquals("twitter.com", origin.getHost());
        Assert.assertEquals(80, origin.getPort());
        Assert.assertEquals("http://twitter.com:80", origin.toString());

        origin = new Origin(new URL("https://twitter.com"));
        Assert.assertEquals("https", origin.getScheme());
        Assert.assertEquals("twitter.com", origin.getHost());
        Assert.assertEquals(443, origin.getPort());
        Assert.assertEquals("https://twitter.com:443", origin.toString());

        origin = new Origin(new URL("http://twitter.com:8000"));
        Assert.assertEquals("http", origin.getScheme());
        Assert.assertEquals("twitter.com", origin.getHost());
        Assert.assertEquals(8000, origin.getPort());
        Assert.assertEquals("http://twitter.com:8000", origin.toString());

        origin = new Origin(new URL("https://twitter.com:8443"));
        Assert.assertEquals("https", origin.getScheme());
        Assert.assertEquals("twitter.com", origin.getHost());
        Assert.assertEquals(8443, origin.getPort());
        Assert.assertEquals("https://twitter.com:8443", origin.toString());
    }

}
