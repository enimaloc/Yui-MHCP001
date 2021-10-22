package fr.enimaloc.yui;

import com.jagrosh.jdautilities.command.SlashCommand;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

public class CommandListener implements com.jagrosh.jdautilities.command.CommandListener {

    @Override
    public void onSlashCommandException(
            SlashCommandEvent event, SlashCommand command, Throwable throwable
    ) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("An exception as occurred while processing your command")
                .appendDescription(throwable.getClass().getName() + ": " + throwable.getLocalizedMessage());

        String bugReportUrl = getBugReportUrl("enimaloc",
                                              "Yui-MHCP001",
                                              null,
                                              null,
                                              null,
                                              null,
                                              null,
                                              String.format("""
                                                                    - Command used: %s
                                                                    - Exception: `%s: %s`
                                                                    - Branch: [%s](https://github.com/enimaloc/Yui-MHCP001/tree/%4$s)""",
                                                            event.getCommandString(),
                                                            throwable.getClass().getName(),
                                                            throwable.getLocalizedMessage(),
                                                            Constant.GIT_BRANCH));
        bugReportUrl = createShortenUrl(bugReportUrl,
                                        getBugReportUrl("enimaloc", "Yui-MHCP001"),
                                        offsetDateTime -> offsetDateTime.plusMinutes(5));
        Button button = Button.link(bugReportUrl, "Report bug on Github");
        if (event.isAcknowledged()) {
            event.getChannel()
                 .sendMessageEmbeds(builder.build())
                 .setActionRow(button)
                 .queue();
        } else {
            event.replyEmbeds(builder.build())
                 .setEphemeral(true)
                 .addActionRow(button)
                 .queue(null, unused -> event.getChannel()
                                             .sendMessageEmbeds(builder.build())
                                             .setActionRow(button)
                                             .queue());
        }
        LoggerFactory.getLogger(CommandListener.class).error("Slash command exception", throwable);
    }

    private final String defaultContent = """
            **Describe the bug**
            §DESCRIBE
                        
            **To Reproduce**
            §REPRODUCE
                        
            **Expected behavior**
            §EXPECTED
                        
            **Screenshots**
            §SCREENSHOTS
                        
            **Discord (please complete the following information):**
            §DISCORD
                        
            **Additional context**
            §ADDITIONAL""";

    public String getBugReportUrl(
            String ghUser, String ghRepositories, String describe, String reproduce, String expected,
            String screenshots, String discord, String additional
    ) {

        describe    = describe != null ? describe : "A clear and concise description of what the bug is.";
        reproduce   = reproduce != null ? reproduce : """
                Steps to reproduce the behavior:
                1. Go to '...'
                2. Click on '....'
                3. Scroll down to '....'
                4. See error""";
        expected    = expected != null ? expected : "A clear and concise description of what you expected to happen.";
        screenshots = screenshots != null ?
                screenshots :
                "If applicable, add screenshots to help explain your problem.";
        discord     = discord != null ? discord : """
                - Guild ID [e.g. 854508847896723526]
                - Version [e.g. 101451]""";
        additional  = additional != null ? additional : "Add any other context about the problem here.";

        return String.format("%s&body=%s", getBugReportUrl(ghUser, ghRepositories),
                             URLEncoder.encode(defaultContent.replaceAll("§DESCRIBE", describe)
                                                             .replaceAll("§REPRODUCE", reproduce)
                                                             .replaceAll("§EXPECTED", expected)
                                                             .replaceAll("§SCREENSHOTS", screenshots)
                                                             .replaceAll("§DISCORD", discord)
                                                             .replaceAll("§ADDITIONAL", additional),
                                               StandardCharsets.UTF_8));
    }

    public String getBugReportUrl(String ghUser, String ghRepositories) {
        return getBugReportUrl(ghUser, ghRepositories, "bug_report");
    }

    public String getBugReportUrl(String ghUser, String ghRepositories, String bugReportTemplateName) {
        return String.format("https://github.com/%s/%s/issues/new?template=%s.md", ghUser, ghRepositories,
                             bugReportTemplateName);
    }

    public String createShortenUrl(
            String originalUrl, String onError, Function<OffsetDateTime, OffsetDateTime> expiration
    ) {
        try {
            String time = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .format(expiration.apply(OffsetDateTime.now()))
                    .replaceAll("\\.[0-9]*", "");

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
                                   "\"validUntil\": \"%s\"," +
                                   "\"maxVisits\": 1," +
                                   "\"tags\": [\"yui-bug-report\"]" +
                                   "}", originalUrl, time));
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
}
