package org.nick.utils;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.conditional.ITagNodeCondition;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Hello world!
 */
public class App {
    private static final String HTTP_WWW_LOSTFILM_TV = "http://www.lostfilm.tv";
    private static final String CH_WINDOWS_1251 = "windows-1251";

    public static void main(String[] args) throws IOException {
        InputStream in = new URL(HTTP_WWW_LOSTFILM_TV + "/serials.php").openStream();
        TagNode node = new HtmlCleaner().clean(in, CH_WINDOWS_1251);
        in.close();

        List<? extends TagNode> nodes = node.getElementListByAttValue("class", "bb_a", true, true);

        final AtomicInteger count = new AtomicInteger();

        List<Serial> serials = nodes.stream().parallel().limit(8)
                .map(e -> getSerial(e.getText().toString(), e.getAttributeByName("href")))
                .peek(e -> System.out.println(count.incrementAndGet()))
                .sorted((s1, s2) -> Double.compare(s1.rating, s2.rating)).collect(Collectors.toList());

        serials.stream().forEach(System.out::println);

        //System.out.println(getSerial("1", "/browse.php?cat=157"));
    }

    private static Serial getSerial(String title, String href) {
        final Serial reuslt = new Serial(title, href);

        try {
            InputStream in = new URL(HTTP_WWW_LOSTFILM_TV + reuslt.url).openStream();
            HtmlCleaner cleaner = new HtmlCleaner();
            TagNode node = cleaner.clean(in, CH_WINDOWS_1251);
            in.close();

            reuslt.rating = node.getElementList((ITagNodeCondition) App::isSeasonRow, true)
                    .stream().flatMap(e -> e.getElementListByAttValue("align", "right", true, true).stream())
                    .flatMap(e -> e.getElementListByName("label", true).stream())
                    .mapToDouble(o -> Double.parseDouble(o.findElementByName("b", true).getText().toString())).average().orElse(0.0);
        } catch (IOException e) {
            reuslt.rating = 0.0;
        }

        return reuslt;
    }

    private static boolean isSeasonRow(TagNode node) {
        boolean isRow = node.getName().equals("div") && node.hasAttribute("class") && node.getAttributeByName("class").toLowerCase().contains("t_row");
        return isRow && !node.getElementList((ITagNodeCondition) node1 -> node1.getName().equals("label") && node1.hasAttribute("title") && node1.getAttributeByName("title").equals("Сезон полностью"), true).isEmpty();
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
    }
}
