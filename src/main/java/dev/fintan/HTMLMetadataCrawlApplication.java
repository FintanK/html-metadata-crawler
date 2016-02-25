package dev.fintan;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.Iterator;

@SpringBootApplication
public class HTMLMetadataCrawlApplication {

	public static void main(String[] args) throws IOException {


        SpringApplication.run(HTMLMetadataCrawlApplication.class, args);

        Document doc = Jsoup.connect("http://googledevelopers.blogspot.ca").get();


        System.out.println("\n\nSite Data\n\n");

        for(Element meta : doc.select("meta")) {

            if (meta.attr("name").equals("application-name")) {
                System.out.println("Site Title: " + meta.attr("content"));
            }

            if (meta.attr("name").equals("description")) {
                System.out.println("Description: " + meta.attr("content"));
            }

            if (meta.attr("name").indexOf("twitter") > -1) {
                System.out.println("Twitter Card: " + meta.attr("name").substring(meta.attr("name").lastIndexOf(":") + 1)  + ": " + meta.attr("content"));
            }

            if (meta.attr("name").indexOf("msapplication") > -1) {
                System.out.println("Windows Tiles: " + meta.attr("name").substring(meta.attr("name").lastIndexOf("-") + 1)  + ": " + meta.attr("content"));
            }

            if (meta.attr("property").indexOf("og") > -1) {
                System.out.println("Open Graph: " + meta.attr("property").substring(meta.attr("property").lastIndexOf(":") + 1)  + ": " + meta.attr("content"));
            }
        }

        for(Element link : doc.select("link[type=application/rss+xml]")) {
            System.out.println("RSS Feed: " + link.attr("title") + ", Content: " + link.attr("href"));
        }


        // JSON LD

        for(Element JSONLD : doc.select("script[type=application/ld+json]")) {
            JSONObject jsonObject = new JSONObject(JSONLD.data());

            Iterator<?> keys = jsonObject.keys();

            while( keys.hasNext() ) {
                String key = (String)keys.next();
                if (!(jsonObject.get(key) instanceof JSONObject)) {
                    System.out.println("JSON LD : " + key + ": " + jsonObject.get(key));
                }
            }

        }

        for(Element link : doc.select("link")) {
            if (link.attr("rel").indexOf("apple-touch") > -1) {
                System.out.println("IOS Icon: " + link.attr("rel")  + ": " + link.attr("sizes") + " : " + link.attr("href"));
            }
        }
	}
}
