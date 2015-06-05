/*
 * Copyright (c) 2015 Twitter, Inc. All rights reserved.
 * Licensed under the Apache License v2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.twitter.whiskey.util;

/**
 * @author Michael Schore
 */
public interface Logger {
    public void fatal(String s);
    public void error(String s);
    public void warn(String s);
    public void info(String s);
    public void debug(String s);
    public void trace(String s);
}
