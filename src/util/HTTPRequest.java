package util;

public class HTTPRequest {
    public static String GET(String url) {
        return Crawler.getContentFromUrl(url);
    }

    public static String POST(String url, String body) {
        return Crawler.getContentFromUrl(url, null, "POST", body, null);
    }

    public static String PUT(String url, String body) {
        return Crawler.getContentFromUrl(url, null, "PUT", body, null);
    }

    public static String DELETE(String url, String body) {
        return Crawler.getContentFromUrl(url, null, "DELETE", body, null);
    }

}