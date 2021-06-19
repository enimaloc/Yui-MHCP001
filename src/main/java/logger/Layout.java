/*
 * Copyright 2021 Antoine(enimaloc) SAINTY
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

package logger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.LayoutBase;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Layout extends LayoutBase<ILoggingEvent> {
    
    public static final String ANSI_COLOR_PREFIX = "\u001B[";
    public static final String ANSI_COLOR_SUFFIX = "m";
    
    public static final String RESET = ANSI_COLOR_PREFIX + "0" + ANSI_COLOR_SUFFIX;
    
    public static final String BLACK   = "0";
    public static final String RED     = "1";
    public static final String GREEN   = "2";
    public static final String YELLOW  = "3";
    public static final String BLUE    = "4";
    public static final String MAGENTA = "5";
    public static final String CYAN    = "6";
    public static final String WHITE   = "7";
    
    public static final String FOREGROUND         = "3";
    public static final String FOREGROUND_BLACK   = ANSI_COLOR_PREFIX + FOREGROUND + BLACK + ANSI_COLOR_SUFFIX;
    public static final String FOREGROUND_RED     = ANSI_COLOR_PREFIX + FOREGROUND + RED + ANSI_COLOR_SUFFIX;
    public static final String FOREGROUND_GREEN   = ANSI_COLOR_PREFIX + FOREGROUND + GREEN + ANSI_COLOR_SUFFIX;
    public static final String FOREGROUND_YELLOW  = ANSI_COLOR_PREFIX + FOREGROUND + YELLOW + ANSI_COLOR_SUFFIX;
    public static final String FOREGROUND_BLUE    = ANSI_COLOR_PREFIX + FOREGROUND + BLUE + ANSI_COLOR_SUFFIX;
    public static final String FOREGROUND_MAGENTA = ANSI_COLOR_PREFIX + FOREGROUND + MAGENTA + ANSI_COLOR_SUFFIX;
    public static final String FOREGROUND_CYAN    = ANSI_COLOR_PREFIX + FOREGROUND + CYAN + ANSI_COLOR_SUFFIX;
    public static final String FOREGROUND_WHITE   = ANSI_COLOR_PREFIX + FOREGROUND + WHITE + ANSI_COLOR_SUFFIX;
    
    public static final String BACKGROUND         = "4";
    public static final String BACKGROUND_BLACK   = ANSI_COLOR_PREFIX + BACKGROUND + BLACK + ANSI_COLOR_SUFFIX;
    public static final String BACKGROUND_RED     = ANSI_COLOR_PREFIX + BACKGROUND + RED + ANSI_COLOR_SUFFIX;
    public static final String BACKGROUND_GREEN   = ANSI_COLOR_PREFIX + BACKGROUND + GREEN + ANSI_COLOR_SUFFIX;
    public static final String BACKGROUND_YELLOW  = ANSI_COLOR_PREFIX + BACKGROUND + YELLOW + ANSI_COLOR_SUFFIX;
    public static final String BACKGROUND_BLUE    = ANSI_COLOR_PREFIX + BACKGROUND + BLUE + ANSI_COLOR_SUFFIX;
    public static final String BACKGROUND_MAGENTA = ANSI_COLOR_PREFIX + BACKGROUND + MAGENTA + ANSI_COLOR_SUFFIX;
    public static final String BACKGROUND_CYAN    = ANSI_COLOR_PREFIX + BACKGROUND + CYAN + ANSI_COLOR_SUFFIX;
    public static final String BACKGROUND_WHITE   = ANSI_COLOR_PREFIX + BACKGROUND + WHITE + ANSI_COLOR_SUFFIX;
    
    String  traceColor         = FOREGROUND_BLUE;
    String  debugColor         = FOREGROUND_CYAN;
    String  infoColor          = FOREGROUND_GREEN;
    String  warnColor          = FOREGROUND_YELLOW;
    String  errorColor         = FOREGROUND_BLACK + BACKGROUND_RED;
    String  dateFormat;
    boolean displayThreadName  = true;
    boolean displayLoggerName  = true;
    boolean sendErrorToSentry  = true;
    int     minWidthLevel      = 5;
    int     minWidthThreadName = 22;
    int     minWidthLoggerName = 1;
    
    @Override
    public String doLayout(ILoggingEvent event) {
        String reportId = "";
        if (sendErrorToSentry && event.getThrowableProxy() instanceof ThrowableProxy) {
            ThrowableProxy tp = (ThrowableProxy) event.getThrowableProxy();
            
            Message message = new Message();
            message.setMessage(event.getMessage());
            message.setFormatted(event.getFormattedMessage());
            message.setParams(toParams(event.getArgumentArray()));
            
            SentryEvent sentryEvent = new SentryEvent(new Date(event.getTimeStamp()));
            sentryEvent.setMessage(message);
            sentryEvent.setLogger(event.getLoggerName());
            sentryEvent.setExtra("thread_name", event.getThreadName());
            sentryEvent.getContexts().put(
                    "MDC",
                    CollectionUtils.filterMapEntries(event.getMDCPropertyMap(), entry -> entry.getValue() != null)
            );
            sentryEvent.setThrowable(tp.getThrowable());
            
            reportId = Sentry.captureEvent(sentryEvent).toString();
            tp.getThrowable().printStackTrace();
        }
        
        StringBuilder out = new StringBuilder();
        switch (event.getLevel().toInt()) {
            case Level.TRACE_INT -> out.append(traceColor);
            case Level.DEBUG_INT -> out.append(debugColor);
            case Level.INFO_INT -> out.append(infoColor);
            case Level.WARN_INT -> out.append(warnColor);
            case Level.ERROR_INT -> out.append(errorColor);
        }
        out.append('[').append((dateFormat != null ? new SimpleDateFormat(dateFormat) : new SimpleDateFormat())
                .format(new Date(event.getTimeStamp())))
           .append("] [").append(String.format("%-" + minWidthLevel + "s", event.getLevel().toString()));
        if (displayThreadName) {
            out.append("] [").append(String.format("%-" + minWidthThreadName + "s", event.getThreadName()));
        }
        if (displayLoggerName) {
            out.append("] [").append(String.format(
                    "%-" + minWidthLoggerName + "s",
                    event.getLoggerName().substring(event.getLoggerName().lastIndexOf('.') + 1)
            ));
        }
        out.append("] ").append(event.getFormattedMessage());
        if (!reportId.isEmpty()) {
            out.append(" {Report ID: ").append(reportId).append("}");
        }
        out.append(RESET).append('\n');
        return out.toString();
    }
    
    private List<String> toParams(Object[] arguments) {
        if (arguments != null) {
            return Arrays.stream(arguments)
                         .filter(Objects::nonNull)
                         .map(Object::toString)
                         .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }
}
