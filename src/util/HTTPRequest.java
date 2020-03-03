package util;

public class HTTPRequest {
    public static String GET(String url) {
        return GET(url, false);
    }

    public static String POST(String url, String body) {
        return POST(url, body, false);
    }

    public static String PUT(String url, String body) {
        return PUT(url, body, false);
    }

    public static String DELETE(String url, String body) {
        return DELETE(url, body, false);
    }

    public static String GET(String url, boolean forceOnBadResponseCode) {
        return Crawler.getContentFromUrl(url, null, "GET", null, null, forceOnBadResponseCode);
    }

    public static String POST(String url, String body, boolean forceOnBadResponseCode) {
        return Crawler.getContentFromUrl(url, null, "POST", body, null, forceOnBadResponseCode);
    }

    public static String PUT(String url, String body, boolean forceOnBadResponseCode) {
        return Crawler.getContentFromUrl(url, null, "PUT", body, null, forceOnBadResponseCode);
    }

    public static String DELETE(String url, String body, boolean forceOnBadResponseCode) {
        return Crawler.getContentFromUrl(url, null, "DELETE", body, null, forceOnBadResponseCode);
    }
}