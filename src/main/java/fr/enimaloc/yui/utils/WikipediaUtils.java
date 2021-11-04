package fr.enimaloc.yui.utils;

import fr.enimaloc.enutils.classes.NumberUtils;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.fastily.jwiki.core.Wiki;
import org.fastily.jwiki.dwrap.PageSection;

public class WikipediaUtils {
    public static final Pattern TO_URL  = Pattern.compile("\\[\\[(?<reference>[^]]*)]]");
    public static final Pattern TO_BOLD = Pattern.compile("'''(?<bolded>[^']*)'''");

    public static EmbedBuilder contentToDiscordEmbed(Matcher matcher) {
        return contentToDiscordEmbed(new EmbedBuilder(), matcher);
    }

    public static EmbedBuilder contentToDiscordEmbed(EmbedBuilder builder, Matcher matcher) {
        String article = matcher.group("article");
        String header  = matcher.group("anchor");
        Wiki wiki = new Wiki.Builder()
                .withDomain(matcher.group("domain"))
                .build();

        article = wiki.resolveRedirect(article);
        header  = header != null ? URLDecoder.decode(header.replaceAll("_", " "), StandardCharsets.UTF_8) : "";

        String thumbnail = null;
        if (wiki.getImagesOnPage(article).size() != 0 &&
            wiki.getImageInfo(wiki.getImagesOnPage(article).get(0)).size() != 0) {
            thumbnail = wiki.getImageInfo(wiki.getImagesOnPage(article).get(0)).get(0).url.url().toString();
        }

        ArrayList<PageSection> sections = wiki.splitPageByHeader(article);
        int                    i        = 0;
        PageSection            section  = sections.get(i);
        while (!header.isEmpty() && section.header != null && section.header.equalsIgnoreCase(header)) {
            section = sections.get(i++);
        }

        String text = section.text;
        System.out.println("text = " + text);

        builder.setTitle(URLDecoder.decode(article, StandardCharsets.UTF_8).replaceAll("_", " "), matcher.group())
               .setThumbnail(thumbnail);

        String content = switch (matcher.group("lang")) {
            case "fr" -> french(builder, matcher, wiki, text);
            case "de" -> deutsch(builder, matcher, wiki, text);
            default -> english(builder, matcher, wiki, text);
        };

        content = parseContent(matcher.group("baseWiki"), wiki, content);

        builder.setDescription(
                content.length() > MessageEmbed.DESCRIPTION_MAX_LENGTH - 3 ?
                        content.substring(0, MessageEmbed.DESCRIPTION_MAX_LENGTH - 3) + "..." :
                        content);

        return builder;
    }

    private static String english(EmbedBuilder builder, Matcher matcher, Wiki wiki, String text) {
        for (String s : text.split("\n")) {
            System.out.println(s);
        }
        return text;
    }

    private static String french(EmbedBuilder builder, Matcher matcher, Wiki wiki, String text) {
        StringBuilder newline = null;
        int           newLine = 0;
        List<String>  output  = new ArrayList<>();
        for (String line : text.split("\n")) {
            System.out.println(line);
            if (!line.startsWith("{{") && newline == null) {
                output.add(line);
                continue;
            }
            if (!line.startsWith("{{") && newline != null) {
                newline.append((char) newLine).append(line);
                if (line.endsWith("}}")) {
                    newLine--;
                }
            }
            line = line.replaceFirst("\\{\\{", "").replaceAll("}}", "");
            String[] content = Arrays.stream(line.split("\\|"))
                                     .map(String::strip)
                                     .toArray(String[]::new);


            if (content[0].equalsIgnoreCase("ébauche")) {
                output.add("> Cette article est une ébauche de " + content[1]);
            } else if (content[0].equalsIgnoreCase("Taxobox début")) {
                // ignored
            } else if (content[0].equalsIgnoreCase("Taxobox")) {
                builder.addField(content[1],
                                 wiki.exists(content[2]) ?
                                         String.format("[%s](%s)", content[2],
                                                       matcher.group("baseWiki") +
                                                       URLEncoder.encode(content[2],
                                                                         StandardCharsets.UTF_8)) :
                                         content[2],
                                 true);
            } else if (content[0].equalsIgnoreCase("Taxobox taxon")) {
                // ignored
            } else if (content[0].equalsIgnoreCase("Taxobox fin")) {
                // ignored
            } else if (!line.endsWith("}}") && newLine == 0) {
                newLine++;
                newline = new StringBuilder(content[0].split(" ", 2)[1]);
            } else if (newLine == 0) {
                String[] split = newline.toString().split("\u0001", 2);
                if (split[0].startsWith("Sportif")) {
                    for (String field : split[1].split("\u0001")) {
                        String[] values = field.split(" = ");
                        builder.addField(values[0], values.length == 1 ? "none" : values[1], true);
                    }
                } else {
                    builder.addField(split[0], split[1].replace("}}", ""), true);
                }
                newline = null;
            }
        }

        return String.join("\n", output);
    }

    private static String deutsch(EmbedBuilder builder, Matcher matcher, Wiki wiki, String text) {
        boolean                   attribute = false;
        List<String>              output    = new ArrayList<>();
        List<Map<String, String>> taxons    = new ArrayList<>();
        for (String line : text.split("\n")) {
            System.out.println(line);
            attribute = line.startsWith("{{") || attribute;
            if (line.startsWith("<!-- ")) {
                continue;
            }
            if (!attribute) {
                output.add(line);
                continue;
            }
            attribute = !line.endsWith("}}");
            line      = line.replaceFirst(" \\| ", "")
                            .replaceFirst("\\{\\{", "")
                            .replaceAll("}}", "");
            String[] content = Arrays.stream(line.split(" = "))
                                     .map(String::strip)
                                     .toArray(String[]::new);

            Matcher m;
            if ((m = Pattern.compile("Taxon(?<number>[0-9]*)_(?<part>[^ ]*)").matcher(content[0])).find()) {
                int                 number   = NumberUtils.getSafe(m.group("number"), Integer.class).orElse(1) - 1;
                boolean             contains = taxons.size() - 1 >= number;
                Map<String, String> taxon    = contains ? taxons.get(number) : new HashMap<>();
                taxon.put(m.group("part"), content[1]);
                if (contains) {
                    taxons.set(number, taxon);
                } else {
                    taxons.add(taxon);
                }
            }

        }
        for (Map<String, String> taxon : taxons) {
            builder.addField(taxon.get("Rang"),
                             String.format("%s (%s)",
                                           parseUrl(matcher.group("baseWiki"), wiki, taxon.get("Name")),
                                           taxon.get("WissName")),
                             true);
            System.out.println("-");
            taxon.forEach((s, s2) -> System.out.println(s + " = " + s2));
        }

        return String.join("\n", output);
    }

    private static String parseContent(String baseWikiUrl, Wiki wiki, String content) {
        content = TO_URL.matcher(content).replaceAll(match -> {
            String group = match.group()
                                .replaceFirst("\\[\\[", "")
                                .replaceAll("]]", "");
            return parseUrl(baseWikiUrl, wiki, group);
        });
        content = TO_BOLD.matcher(content).replaceAll(match -> "**" + match.group().replaceAll("'''", "") + "**");
        return content;
    }

    private static String parseUrl(String baseWikiUrl, Wiki wiki, String group) {
        String art   = group;
        String label = group;
        if (group.contains("|")) {
            art   = group.split("\\|")[0];
            label = group.split("\\|")[1];
        }
        return parseUrl(baseWikiUrl, wiki, art, label);
    }

    private static String parseUrl(String baseWikiUrl, Wiki wiki, String article, String label) {
        article = URLEncoder.encode(article.replaceAll(" ", "_"), StandardCharsets.UTF_8);
        if (wiki.exists(article)) {
            return String.format("[%s](%s)", label, baseWikiUrl + article);
        } else {
            return label;
        }
    }
}
