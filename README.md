# html-metadata-crawler

This application uses a list of website URLs to find site metadata and related content.

- RSS Feeds
- Twitter Cards
- Open Graph Data
- JSON Linked Data
- Microsoft Tiles Metadata
- IOS Icons


Add any required sites to sites.txt in the root directory.

Run the application

> mvn spring-boot:run

This will update the feeds.json and incomplete.json files.
