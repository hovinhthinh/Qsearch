package util;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPInputStream;

class ProxyCollection {
    private static final int PROXY_LIST_REFRESH_INTERVAL = 300 * 1000;
    private static long LAST_PROXY_LIST_REFRESH_TIMESTAMP = -1;
    private static ArrayList<Proxy> PROXY_LIST = new ArrayList<>();
    private static Random RAND = new Random();

    public static synchronized Proxy getRandomProxy() {
        if (LAST_PROXY_LIST_REFRESH_TIMESTAMP + PROXY_LIST_REFRESH_INTERVAL <= System.currentTimeMillis()) {
            PROXY_LIST = getProxiesFromFreeProxyNet();
            LAST_PROXY_LIST_REFRESH_TIMESTAMP = System.currentTimeMillis();
        }
        return PROXY_LIST.isEmpty() ? null : PROXY_LIST.get(RAND.nextInt(PROXY_LIST.size()));
    }

    public static ArrayList<Proxy> getProxiesFromFreeProxyNet() {
        return getProxiesFromFreeProxyNet(-1);
    }

    public static ArrayList<Proxy> getProxiesFromFreeProxyNet(int limit) {
        String content = Crawler.getContentFromUrl("https://free-proxy-list.net/", Proxy.NO_PROXY);
        ArrayList<Proxy> res = new ArrayList<>();
        if (content == null) {
            return res;
        }
        String tbody = TParser.getContent(content, "<tbody>", "</tbody>");
        ArrayList<String> trs = TParser.getContentList(tbody, "<tr[^>]*+>", "</tr>");

        for (String tr : trs) {
            try {
                ArrayList<String> tds = TParser.getContentList(tr, "<td[^>]*+>", "</td>");
                if (tds.get(6).equals("no")) { // only HTTPS
                    continue;
                }
                res.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(tds.get(0), Integer.parseInt(tds.get(1)))));
            } catch (Exception ex) {
            }
            if (res.size() == limit) {
                break;
            }
        }
        return res;
    }
}

public class Crawler {
    // These params could be set for flexibility.
    public static int CONNECT_TIME_OUT = 10 * 1000;
    public static int READ_TIME_OUT = 300 * 1000;
    public static int NUM_RETRY_CONNECTION = 3;

    public static boolean USE_RANDOM_PROXY = false;
    public static int NUM_RETRY_RANDOM_PROXY_CONNECTION = 5;

    private static final int MAX_CONTENT_LENGTH = 1024 * 1024 * 1024; // 1G
    private static final int BUFFER_SIZE = 8 * 1024; // 8K

    //	If content length < MIN_CONTENT_LENGTH then page is redirected or error.
    //	public static final int MIN_CONTENT_LENGTH = 512; // 512B

    //	Allow redirecting.
    private static final boolean FOLLOW_REDIRECT = true;

    private static final String J_CONNECTION = "close";
    private static final String J_USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like " +
            "Gecko) Chrome/35.0.1916.153 Safari/537.36";
    private static final String J_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8";
    private static final String J_ACCEPT_CHARSET = "UTF-8,iso-8859-1;q=0.7,*;q=0.7";
    private static final String J_ACCEPT_ENCODING = "gzip,deflate,sdch";
    private static final String J_ACCEPT_LANGUAGE = "vi-VN,vi;q=0.8,fr-FR;q=0.6,fr;q=0.4,en-US;q=0.2,en;q=0.2";

    private static HttpURLConnection connect(URL url, Map<String, String> extendedHeader, Proxy proxy, String method) {
        try {
            URLConnection ucon;
            if (proxy == null) {
                if (USE_RANDOM_PROXY) {
                    ucon = url.openConnection(ProxyCollection.getRandomProxy());
                } else {
                    ucon = url.openConnection();
                }
            } else {
                ucon = url.openConnection(proxy);
            }
            HttpURLConnection conn = (HttpURLConnection) ucon;
            // conn.setRequestProperty("Cookie", "");
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(true);
            conn.setRequestMethod(method);

            conn.setConnectTimeout(CONNECT_TIME_OUT);
            conn.setReadTimeout(READ_TIME_OUT);

            HttpURLConnection.setFollowRedirects(FOLLOW_REDIRECT);
            conn.setInstanceFollowRedirects(FOLLOW_REDIRECT);

            conn.addRequestProperty("Connection", J_CONNECTION);
            conn.addRequestProperty("User-Agent", J_USER_AGENT);
            conn.addRequestProperty("Accept", J_ACCEPT);
            conn.addRequestProperty("Accept-Charset", J_ACCEPT_CHARSET);
            conn.addRequestProperty("Accept-Encoding", J_ACCEPT_ENCODING);
            conn.addRequestProperty("Accept-Language", J_ACCEPT_LANGUAGE);

            if (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT")) {
                conn.addRequestProperty("Content-Type", "application/json");
            }

            if (extendedHeader != null) {
                for (String field : extendedHeader.keySet()) {
                    conn.addRequestProperty(field, extendedHeader.get(field));
                }
            }

            conn.setDoOutput(true);
            conn.connect();
            return conn;
        } catch (Exception e) {
//			e.printStackTrace();
            return null;
        }
    }

    /* Use for download file, no proxy supported */
    public static byte[] getContentBytesFromUrl(String url) {
        for (int i = 0; i < NUM_RETRY_CONNECTION; i++) {
            try {
                HttpURLConnection hc = connect(new URL(url), null, null, "GET");
                int contentLength = hc.getContentLength();
                String contentType = hc.getContentType();
                if (contentType.startsWith("text/") || contentLength == -1) {
                    throw new IOException("This is not a binary file: " + url);
                }

                InputStream in = new BufferedInputStream(hc.getInputStream());
                byte[] data = new byte[contentLength];
                int bytesRead = 0;
                int offset = 0;
                while (offset < contentLength) {
                    bytesRead = in.read(data, offset, data.length - offset);
                    if (bytesRead < 0) {
                        break;
                    }
                    offset += bytesRead;
                }
                in.close();
                if (offset != contentLength) {
                    throw new IOException("only read " + offset + " bytes; expected " + contentLength + " bytes: " + url);
                }

                return data;
            } catch (Exception ex) {
//				ex.printStackTrace();
            }
        }
        return null;
    }

    public static String getContentFromUrl(String url) {
        return getContentFromUrl(url, null, "GET", null, null);
    }

    public static String getContentFromUrl(String url, Proxy p) {
        return getContentFromUrl(url, null, "GET", null, p);
    }

    public static String getContentFromUrl(String url, Map<String, String> extendedHeader, String method, String body, Proxy proxy) {
        return getContentFromUrl(url, extendedHeader, method, body, proxy, false);
    }

    // proxy = null means using the global setting stated by USE_RANDOM_PROXY variables.
    public static String getContentFromUrl(String url, Map<String, String> extendedHeader, String method, String body, Proxy proxy, boolean forceOnBadResponseCode) {
        boolean useGZip = false;
        int nRetry = (proxy == null && USE_RANDOM_PROXY) ? NUM_RETRY_RANDOM_PROXY_CONNECTION : NUM_RETRY_CONNECTION;
        for (int i = 0; i < nRetry; i++) {
            try {
                HttpURLConnection hc = connect(new URL(url), extendedHeader, proxy, method);
                if (body != null) {
                    try (OutputStream os = hc.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                }

                int content_length = hc.getContentLength();
                if ((content_length > MAX_CONTENT_LENGTH) || (content_length == -1)) {
                    content_length = MAX_CONTENT_LENGTH;
                }

                String contentEncoding = null;
                try {
                    contentEncoding = hc.getContentEncoding();
                } catch (Exception ex) {
                }
                if (contentEncoding != null && contentEncoding.equals("gzip")) useGZip = true;
                StringBuilder sb = new StringBuilder();
                int c = 0;
                InputStream is = null;
                int responseCode = hc.getResponseCode();

                if (!forceOnBadResponseCode || (responseCode != -1 && hc.getResponseCode() < 400)) {
                    is = hc.getInputStream();
                } else {
                    is = hc.getErrorStream();
                }

                if (useGZip) {
                    is = new GZIPInputStream(is);
                    content_length = (MAX_CONTENT_LENGTH / 8) * 6;
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                char[] ch = new char[BUFFER_SIZE];

                while (c < content_length) {
                    int t = br.read(ch, 0, ch.length);
                    if (t >= 0) {
                        sb.append(ch, 0, t);
                        c = c + t;
                    } else {
                        break;
                    }
                }

                br.close();
                return sb.toString();
            } catch (Exception ex) {
            }
        }
        return null;
    }
}