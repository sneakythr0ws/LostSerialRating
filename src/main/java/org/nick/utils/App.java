package org.nick.utils;

import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Request;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.conditional.ITagNodeCondition;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class App {
    private static final String HTTP_WWW_LOSTFILM_TV = "http://www.lostfilm.tv";

    public static void main(String[] args) throws IOException {
        InputStream in = new URL(HTTP_WWW_LOSTFILM_TV + "/serials.php").openStream();
        TagNode node = new HtmlCleaner().clean(in, "windows-1251");
        in.close();

        List<? extends TagNode> nodes = node.getElementListByAttValue("class", "bb_a", true, true);

        final AtomicInteger count = new AtomicInteger();

        List<Serial> serials = nodes.stream().parallel()
                .map(e -> getSerial(e.getText().toString(), e.getAttributeByName("href")))
                .peek(e -> System.out.println(count.incrementAndGet()))
                .sorted((s1, s2) -> -Double.compare(s1.rating, s2.rating)).distinct().collect(Collectors.toList());

        serials.stream().forEach(System.out::println);

        PrintWriter writer = new PrintWriter(new File("/LostFilmSerials.html"), "windows-1251");
        writer.write("<html><body><table>");

        for (Serial serial : serials) {
            BigDecimal bigDecimal = new BigDecimal(serial.rating);

            writer.write("<tr><td>" + bigDecimal.setScale(1, BigDecimal.ROUND_HALF_UP) + "</td><td><a href=\"" + HTTP_WWW_LOSTFILM_TV + serial.url + "\">" + serial.title + "</a></td></tr>");
        }

        writer.write("</table></body></html>");

        writer.close();
    }

    private static Serial getSerial(String title, String href) {
        final Serial reuslt = new Serial(title, href);

        try {
            InputStream in = new URL(HTTP_WWW_LOSTFILM_TV + reuslt.url).openStream();
            TagNode node = new HtmlCleaner().clean(in, "windows-1251");
            in.close();

            OptionalDouble rating = getRating(node);
            reuslt.rating = rating.isPresent() ? rating.getAsDouble() : tryProxy(href);
        } catch (IOException e) {
            reuslt.rating = 0.0;
        }

        return reuslt;
    }

    private static OptionalDouble getRating(TagNode node) {
        return node.getElementList((ITagNodeCondition) App::isSeasonRow, true)
                .stream().flatMap(e -> e.getElementListByAttValue("align", "right", true, true).stream())
                .flatMap(e -> e.getElementListByName("label", true).stream())
                .mapToDouble(o -> Double.parseDouble(o.findElementByName("b", true).getText().toString())).average();
    }

    private static boolean isSeasonRow(TagNode node) {
        boolean isRow = node.getName().equals("div") && node.hasAttribute("class") && node.getAttributeByName("class").toLowerCase().contains("t_row");
        return isRow && !node.getElementList((ITagNodeCondition) node1 -> node1.getName().equals("label") && node1.hasAttribute("title") && node1.getAttributeByName("title").equals("Сезон полностью"), true).isEmpty();
    }

    private static double tryProxy(String href) {
        try {
            final String content = Request.Get(HTTP_WWW_LOSTFILM_TV + href).viaProxy(new HttpHost("62.23.15.92", 3128)).execute().returnContent().asString();

            return getRating(new HtmlCleaner().clean(content)).orElse(0.0);
        } catch (IOException e) {
            return 0.0;
        }
    }

    private static class Serial {
        String title;
        String url;
        double rating;

        Serial(String title, String url) {
            this.title = title;
            this.url = url;
        }

        @Override
        public String toString() {
            return "Serial{" +
                    "title='" + title + '\'' +
                    ", url='" + url + '\'' +
                    ", rating=" + rating +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Serial serial = (Serial) o;
            return Double.compare(serial.rating, rating) == 0 &&
                    Objects.equals(title, serial.title) &&
                    Objects.equals(url, serial.url);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, url, rating);
        }
    }
}
