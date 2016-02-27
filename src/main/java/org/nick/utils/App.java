package org.nick.utils;

import com.ui4j.api.browser.BrowserEngine;
import com.ui4j.api.browser.Page;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.conditional.ITagNodeCondition;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.ui4j.api.browser.BrowserFactory.getWebKit;

/**
 * Hello world!
 */
public class App {
    private static final String HTTP_WWW_LOSTFILM_TV = "http://www.lostfilm.tv";

    private static String getHtml(String url) {
        BrowserEngine webKit = getWebKit();
        try (Page page = webKit.navigate(url)) {
            return (String) page.executeScript("document.documentElement.innerHTML");
        }
    }

    public static void main(String[] args) throws IOException {
        TagNode node = new HtmlCleaner().clean(getHtml(HTTP_WWW_LOSTFILM_TV + "/serials.php"));


        List<? extends TagNode> nodes = node.getElementListByAttValue("class", "bb_a", true, true);

        final AtomicInteger count = new AtomicInteger();

        List<Serial> serials = nodes.stream().parallel().limit(8)
                .map(e -> getSerial(e.getText().toString(), e.getAttributeByName("href")))
                .peek(e -> System.out.println(count.incrementAndGet()))
                .sorted((s1, s2) -> -Double.compare(s1.rating, s2.rating)).collect(Collectors.toList());

        serials.stream().forEach(System.out::println);

        PrintWriter writer = new PrintWriter(new File("/LostFilmSerials.html"), "windows-1251");
        writer.write("<html><body><table>");

        for (Serial serial : serials) {
            BigDecimal bigDecimal = new BigDecimal(serial.rating);

            writer.write("<tr><td>" + bigDecimal.setScale(1, BigDecimal.ROUND_HALF_UP) + "</td><td><a href=\"" + HTTP_WWW_LOSTFILM_TV + serial.url + "\">" + serial.title + "</a></td></tr>");
        }

        writer.write("</table></body></html>");

        writer.close();

        //System.out.println(getSerial("1", "/browse.php?cat=157"));
        getWebKit().shutdown();
    }

    private static Serial getSerial(String title, String href) {
        final Serial reuslt = new Serial(title, href);

        String html = getHtml(HTTP_WWW_LOSTFILM_TV + reuslt.url);
        TagNode node = new HtmlCleaner().clean(html);

        reuslt.rating = node.getElementList((ITagNodeCondition) App::isSeasonRow, true)
                .stream().flatMap(e -> e.getElementListByAttValue("align", "right", true, true).stream())
                .flatMap(e -> e.getElementListByName("label", true).stream())
                .mapToDouble(o -> Double.parseDouble(o.findElementByName("b", true).getText().toString())).average().orElse(0.0);

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
