package dev.fintan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.json.JsonJsonParser;
import org.springframework.boot.json.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@SpringBootApplication
public class HTMLMetadataCrawlApplication {

    public static void main(String[] args) throws IOException, URISyntaxException {

        List<JSONObject> feeds = new ArrayList<>();
        List<String> sitesNotFound = new ArrayList<>();
        List<String> sitesRSSNotFound = new ArrayList<>();
        List<JSONObject> sitesInCompleteData = new ArrayList<>();

        SpringApplication.run(HTMLMetadataCrawlApplication.class, args);

        FileInputStream fstream = new FileInputStream("sites.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

        String site;

        // Read File Line By Line
        while ((site = br.readLine()) != null)   {

            processSites(feeds, sitesNotFound, sitesRSSNotFound, sitesInCompleteData, site);
        }

        generateResults(feeds, sitesNotFound, sitesRSSNotFound, sitesInCompleteData, br);

    }

    private static void generateResults(List<JSONObject> feeds, List<String> sitesNotFound, List<String> sitesRSSNotFound, List<JSONObject> sitesInCompleteData, BufferedReader br) throws IOException {
        for (String siteNotFound : sitesNotFound) {
            System.out.println("Unable to connect to site: " + siteNotFound);
        }

        for (String siteRSSNotFound : sitesRSSNotFound) {
            System.out.println("No RSS Feeds found for site: " + siteRSSNotFound);
        }



        System.out.println("\n\nWriting news feeds to feeds.json\n");

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonParser jp = new JsonJsonParser();

        Object je = jp.parseList(feeds.toString());
        String prettyJsonString = gson.toJson(je);

        PrintWriter writer = new PrintWriter("feeds.json", "UTF-8");
        writer.print(prettyJsonString);
        writer.close();


        System.out.println("Writing incomplete data to incomplete.json");
        je = jp.parseList(sitesInCompleteData.toString());
        prettyJsonString = gson.toJson(je);


        writer = new PrintWriter("incomplete.json", "UTF-8");
        writer.print(prettyJsonString);
        writer.close();

        //Close the input stream
        br.close();

        System.out.println("Success! Processing complete.");
    }

    private static void processSites(List<JSONObject> feeds, List<String> sitesNotFound, List<String> sitesRSSNotFound, List<JSONObject> sitesInCompleteData, String site) throws URISyntaxException {
        Document doc = new Document("");

        String feedIcon = "";
        String feedDescription = "";
        String feedCategoryId = site.contains("[") ? (site.substring(site.indexOf("[") + 1, site.indexOf("]"))) : "software-development";

        site = site.replace("[" + feedCategoryId + "]", "");

        try {
            doc = Jsoup.connect(site).get();
            System.out.println("Connected to site - "+ site);
        } catch (Exception e) {
            sitesNotFound.add(site);
        }

        System.out.println("\n\nProcessing site - " + site);

        // For each link DOM element
        for (Element link : doc.select("link")) {

            if (link.attr("rel").contains("icon")) {
                feedIcon = link.attr("href");
            }

            if (link.attr("rel").contains("apple-touch")) {

                if (feedIcon.equals("") && link.attr("sizes").length() > 0) {
                    feedIcon = link.attr("href");
                }

                System.out.println("IOS Icon: " + link.attr("rel") + ": " + link.attr("sizes") + " : " + link.attr("href"));
            }
        }

        // For each Meta DOM element
        for (Element meta : doc.select("meta")) {

            if (meta.attr("name").equals("application-name")) {
                System.out.println("Site Title: " + meta.attr("content"));
            }

            if (meta.attr("name").equals("description")) {
                System.out.println("Description: " + meta.attr("content"));
            }

            if (meta.attr("name").contains("twitter")) {
                System.out.println("Twitter Card: " + meta.attr("name").substring(meta.attr("name").lastIndexOf(":") + 1) + ": " + meta.attr("content"));
            }

            if (meta.attr("name").contains("msapplication")) {
                System.out.println("Windows Tiles: " + meta.attr("name").substring(meta.attr("name").lastIndexOf("-") + 1) + ": " + meta.attr("content"));
            }

            if (meta.attr("property").contains("og")) {

                if (feedIcon.equals("") && meta.attr("property").substring(meta.attr("property").lastIndexOf(":") + 1).equals("image")) {
                    feedIcon = meta.attr("content");
                }

                if (meta.attr("property").substring(meta.attr("property").lastIndexOf(":") + 1).equals("description")) {
                    feedDescription = meta.attr("content");
                }

                System.out.println("Open Graph: " + meta.attr("property").substring(meta.attr("property").lastIndexOf(":") + 1) + ": " + meta.attr("content"));
            }
        }

        // JSON LD
        for (Element JSONLD : doc.select("script[type=application/ld+json]")) {
            JSONObject jsonObject = new JSONObject(JSONLD.data());

            Iterator<?> keys = jsonObject.keys();

            while (keys.hasNext()) {
                String key = (String) keys.next();
                if (!(jsonObject.get(key) instanceof JSONObject)) {
                    System.out.println("JSON LD : " + key + ": " + jsonObject.get(key));
                }
            }

        }

        // If unable to locate RSS feeds
        if(doc.select("link[type=application/rss+xml]").isEmpty()) {
            System.out.println("No RSS Feeds found for site" + site);
            sitesRSSNotFound.add(site);
        }

        // For each link DOM element with RSS XML content
        for (Element link : doc.select("link[type=application/rss+xml]")) {

            boolean dataIncomplete = false;

            JSONObject feed = new JSONObject();
            feed.put("categoryId", feedCategoryId);
            feed.put("description", feedDescription);
            feed.put("id", link.attr("title").toLowerCase().replaceAll("[+.^:,|»]", "").replaceAll(" ", "-").replaceAll("--", ""));

            if (feedIcon.contains("http")) {
                feed.put("image", feedIcon);
            } else if (feedIcon.length() > 0) {
                URI uri = new URI(site);
                String domain = uri.getHost();
                feed.put("image", domain + feedIcon);
            }


            try {
                HttpURLConnection.setFollowRedirects(true);
                // note : you may also need
                //        HttpURLConnection.setInstanceFollowRedirects(false)
                HttpURLConnection con =
                        (HttpURLConnection) new URL(feed.get("image").toString()).openConnection();
                con.setRequestMethod("HEAD");
                con.connect();

                if(con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    feed.put("image", "http://www.crownmountainmovers.com/wp-content/uploads/2015/10/arrow-right1.png");
                }
            }
            catch (Exception e) {
                feed.put("image", "http://www.crownmountainmovers.com/wp-content/uploads/2015/10/arrow-right1.png");
            }

            if (link.attr("title").contains("rss") || link.attr("title").contains("RSS")) {
                Elements title = doc.select("title");
                feed.put("title", title.text());
            } else if (link.attr("title").length() > 0) {
                feed.put("title", link.attr("title"));
            } else {
                feed.put("title", "");
            }


            if (link.attr("href").contains("http")) {
                feed.put("url", link.attr("href"));
            } else if (link.attr("href").length() > 0) {
                feed.put("url", site + link.attr("href"));
            }


            Iterator<?> keys = feed.keys();

            while (keys.hasNext()) {
                String key = (String) keys.next();
                if ((feed.get(key) == null || feed.get(key) == "") && !(key.equals("description"))) {
                    dataIncomplete = true;
                }
            }

            if (dataIncomplete) {
                sitesInCompleteData.add(feed);
            } else {
                feeds.add(feed);
            }

            System.out.println("RSS Feed: " + link.attr("title") + ", Content: " + link.attr("href"));
        }
    }
}
