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
import fr.enimaloc.yui.Constant;
import fr.enimaloc.yui.Yui;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.json.JSONObject;

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
    int     minWidthLevel      = 5;
    int     minWidthThreadName = 22;
    int     minWidthLoggerName = 1;

    Color discordTraceColor = Color.BLUE;
    Color discordDebugColor = Color.CYAN;
    Color discordInfoColor  = Color.GREEN;
    Color discordWarnColor  = Color.YELLOW;
    Color discordErrorColor = Color.RED;

    @Override
    public String doLayout(ILoggingEvent event) {
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
        out.append(RESET).append('\n');

        File        stacktrace = null;
        TextChannel channel;
        if (event.getLevel().isGreaterOrEqual(Level.toLevel(
                System.getenv("REPORT_LEVEL_TO_DISCORD") != null ?
                        System.getenv("REPORT_LEVEL_TO_DISCORD").toUpperCase(Locale.ROOT) :
                        null,
                Level.WARN
        )) && System.getenv("REPORT_DISCORD_CHANNEL") != null
            && (channel = Yui.getJda().getTextChannelById(System.getenv("REPORT_DISCORD_CHANNEL"))) != null) {

            EmbedBuilder builder = new EmbedBuilder();
            builder.appendDescription(out.toString()
                                         .replaceAll(Pattern.quote(ANSI_COLOR_PREFIX) + "\\d*" +
                                                     Pattern.quote(ANSI_COLOR_SUFFIX), ""))
                   .setTimestamp(Instant.ofEpochMilli(event.getTimeStamp()))
                   .setColor(switch (event.getLevel().toInt()) {
                       case Level.TRACE_INT -> discordTraceColor;
                       case Level.DEBUG_INT -> discordDebugColor;
                       case Level.INFO_INT -> discordInfoColor;
                       case Level.WARN_INT -> discordWarnColor;
                       case Level.ERROR_INT -> discordErrorColor;
                       default -> throw new IllegalStateException("Unexpected value: " + event.getLevel().toInt());
                   });

            List<String> lines = null;
            if (event.getThrowableProxy() instanceof ThrowableProxy throwable) {
                try {
                    stacktrace = File.createTempFile("yui-", "");
                    stacktrace.deleteOnExit();
                    throwable.getThrowable().printStackTrace(new PrintStream(stacktrace));

                    lines = Files.readAllLines(stacktrace.toPath());
                    StringBuilder stringBuilder = new StringBuilder();
                    for (String line : lines) {
                        if (stringBuilder.length() + line.length() < MessageEmbed.VALUE_MAX_LENGTH) {
                            stringBuilder.append("\n").append(line);
                        }
                    }
                    builder.addField("Exception", stringBuilder.toString().replaceFirst("\n", ""), false)
                           .setFooter("Wait a couple of minute to have formatted output, stacktrace can be less than expected");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            MessageAction messageAction = channel.sendMessageEmbeds(builder.build());
            if (stacktrace != null) {
                messageAction = messageAction.addFile(stacktrace, "stacktrace.txt");
            }
            List<String> finalLines = lines;
            messageAction.queue(message -> {
                if (finalLines != null) {
                    new Thread(() -> formatStacktrace(finalLines, message)).start();
                }
            });
        }
        if (event.getThrowableProxy() instanceof ThrowableProxy throwable) {
            throwable.getThrowable().printStackTrace();
        }

        return out.toString();
    }

    private void formatStacktrace(List<String> lines, Message message) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String line : lines) {
            String githubUrl = getGithubUrl(line);
            githubUrl = createShortenUrl(githubUrl, githubUrl);
            int returnCode = getReturnCode(githubUrl);
            if (returnCode == 200) {
                line = line.replaceAll("\\((.*)\\)", "([$1](" + githubUrl + "))");
            }
            if (stringBuilder.length() + line.length() < MessageEmbed.VALUE_MAX_LENGTH) {
                stringBuilder.append("\n").append(line);
            } else {
                break;
            }
        }
        message.editMessageEmbeds(
                new EmbedBuilder(message.getEmbeds().get(0))
                        .clearFields()
                        .addField("Exception", stringBuilder.toString().replaceFirst("\n", ""), false)
                        .setFooter(null)
                        .build()
        ).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
    }

    private String getGithubUrl(String line) {
        line = line.replaceFirst("\tat ", "");
        String url = "https://github.com/";
        if (line.startsWith("fr.enimaloc.yui")) {
            url += "enimaloc/Yui-MHCP001/tree/" + Constant.GIT_BRANCH + "/src/main/java/";
//        } else if (line.startsWith("com.jagrosh.jdautilities")) {
//            url += "Chew/JDA-Chewtils/tree/master/src/main/java/";
        } else if (line.startsWith("net.dv8tion.jda")) {
            url += "DV8FromTheWorld/JDA/tree/release/src/main/java/";
        } else {
            return null;
        }
        return getGithubUrl(url, line);
    }

    private String getGithubUrl(String repositoryUrl, String line) {
        try {
            line = line.replaceFirst("\tat ", "");
            String file = line.substring(0, line.lastIndexOf('('));
            file = file.substring(0, file.lastIndexOf('.'));
            file = Arrays.stream(file.split("\\."))
                         .map(s -> s.contains("$") ? s.split("\\$")[0] : s)
                         .collect(Collectors.joining("."));
            String lineNumber = line.replaceFirst("[^(]*\\([^:]*", "");
            lineNumber = lineNumber.substring(1, lineNumber.length() - 1);
            repositoryUrl += file.replaceAll("\\.", "/");
            repositoryUrl += ".";
            repositoryUrl += line.split("\\.")[line.split("\\.").length - 1].split(":")[0];
            repositoryUrl += "#L" + lineNumber;

            return repositoryUrl;
        } catch (Exception e) {
            return null;
        }
    }

    private int getReturnCode(String url) {
        try {
            URL               page       = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) page.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            return connection.getResponseCode();
        } catch (IOException ignored) {
        }

        return 456;
    }

    public String createShortenUrl(String originalUrl, String onError) {
        try {
            new URL(originalUrl);
        } catch (MalformedURLException e) {
            return onError;
        }
        try {
            URL               page       = new URL("https://lnk.enimaloc.fr/rest/v2/short-urls");
            HttpURLConnection connection = (HttpURLConnection) page.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("X-Api-Key", System.getenv("LNK_API_KEY"));
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setDoInput(true);

            OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
            wr.write(String.format("{" +
                                   "\"longUrl\": \"%s\"," +
                                   "\"crawlable\": false," +
                                   "\"tags\": [\"yui-bug-shortlink\"]" +
                                   "}", originalUrl));
            wr.flush();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(),
                                                                              StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    if (connection.getResponseCode() != 200) {
                        System.out.println("responseLine = " + responseLine);
                    } else {
                        JSONObject object = new JSONObject(responseLine);
                        if (object.has("shortUrl")) {
                            return object.getString("shortUrl");
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return onError;
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
