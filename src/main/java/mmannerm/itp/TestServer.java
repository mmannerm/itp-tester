package mmannerm.itp;

import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class TestServer {
    private final static Logger log = LoggerFactory.getLogger(TestServer.class);
    private final static String FIRST_PARTY_DOMAIN = "example.com";
    private final static String THIRD_PARTY_DOMAIN = "3rdpartytestwebkit.org"; //= "nottracker.org";
    private final static String FIRST_PARTY_HOST = "http://" + FIRST_PARTY_DOMAIN + ":4040";
    private final static String THIRD_PARTY_HOST = "http://" + THIRD_PARTY_DOMAIN + ":4040";
    private final static String REFERER_TAG = "REF_TAG";
    private final static String SESSION_COOKIE_NAME = "TESTSESSION";
    private final static String FIRST_PARTY_COOKIE = "1ST_PARTY_COOKIE";
    private final static String TOP_FRAME = "http://" + FIRST_PARTY_DOMAIN + ":8080/index.html/" + REFERER_TAG;

    private final static HttpString ALLOW_ORIGIN = new HttpString("Access-Control-Allow-Origin");

    public static void main(String[] args) throws Exception {
        start3rdParty(4040);
        start1stParty(8080);
    }

    static void sendTextPlain(final HttpServerExchange exchange,
                              final String cookie,
                              final String referer) {
        StringBuilder msg = new StringBuilder();

        msg.append("Cookie: ");
        msg.append(SESSION_COOKIE_NAME);
        msg.append('=');
        msg.append(cookie);
        msg.append('\n');

        msg.append("Referer: ");
        msg.append(referer);
        msg.append('\n');

        log.info("cookie={} referer={}", cookie, referer);

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send(msg.toString());
        exchange.endExchange();
    }

    static void sendImage(final HttpServerExchange exchange,
                          final Color color) throws IOException {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "image/png");

        BufferedImage singlePixelImage = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
        singlePixelImage.setRGB(0, 0, color.getRGB());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(singlePixelImage, "png", baos);
            exchange.getResponseSender().send(ByteBuffer.wrap(baos.toByteArray()));
        }
    }

    static void sendHTML(final HttpServerExchange exchange, final String html) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
        exchange.getResponseSender().send(html);
        exchange.endExchange();
    }

    static void set1stPartyCookie(final HttpServerExchange exchange) {
        CookieImpl cookie = new CookieImpl(SESSION_COOKIE_NAME, FIRST_PARTY_COOKIE);
        cookie.setDomain(exchange.getHostName());
        cookie.setPath("/");
        cookie.setExpires(Date.from(LocalDate.now().plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));

        exchange.getResponseCookies().put(SESSION_COOKIE_NAME, cookie);
        exchange.getResponseHeaders().put(Headers.LOCATION, TOP_FRAME);
        exchange.setStatusCode(302);
        exchange.endExchange();
    }

    static void add3rdPartyCookie(final HttpServerExchange exchange) {
        CookieImpl cookie = new CookieImpl(SESSION_COOKIE_NAME, Long.toString(System.currentTimeMillis()));
        cookie.setDomain(exchange.getHostName());
        cookie.setPath("/");
        cookie.setExpires(Date.from(LocalDate.now().plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        exchange.getResponseCookies().put(SESSION_COOKIE_NAME, cookie);
    }

    static boolean isRefererPresent(final String referer) {
        return referer.contains(REFERER_TAG);
    }

    static boolean is1stPartyCookiePresent(final String cookie) {
        return FIRST_PARTY_COOKIE.equals(cookie);
    }

    public static void start3rdParty(int port) throws IOException {
        final String iframeImage = new String(TestServer.class.getResourceAsStream("/iframeImage.html").readAllBytes(), "UTF-8");
        final String iframeGetImg = new String(TestServer.class.getResourceAsStream("/iframeGetImg.html").readAllBytes(), "UTF-8");
        final String initCookies = new String(TestServer.class.getResourceAsStream("/initCookies.html").readAllBytes(), "UTF-8");

        final Undertow.Builder builder = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setHandler((exchange) -> {
                    exchange.getResponseHeaders().put(ALLOW_ORIGIN, "*");
                    Cookie c = exchange.getRequestCookies().get(SESSION_COOKIE_NAME);

                    String cookie = c == null ? null : c.getValue();
                    String referer = exchange.getRequestHeaders().get(Headers.REFERER).toString();
                    String refColor = isRefererPresent(referer) ? "rgb(0, 255, 0)" : "rgb(255, 0, 0)";

                    String path = exchange.getRequestPath();

                    log.info("host={} path={} cookie={} referer={} ", exchange.getHostName(), path, cookie, referer);

                    if (path.startsWith("/img")) {
                        add3rdPartyCookie(exchange);
                        if (path.contains(REFERER_TAG)) {
                            sendImage(exchange, isRefererPresent(referer) ? Color.GREEN : Color.RED);
                        } else {
                            sendImage(exchange, is1stPartyCookiePresent(cookie) ? Color.BLUE : cookie != null ? Color.CYAN : Color.RED );
                        }
                    } else if (path.startsWith("/iframe3/img")) {
                        add3rdPartyCookie(exchange);
                        sendHTML(exchange, iframeImage
                                .replace("${REFCOLOR}", refColor)
                                .replace("${HOST}", THIRD_PARTY_HOST));
                    } else if (path.startsWith("/iframe3/get/img")) {
                        add3rdPartyCookie(exchange);
                        sendHTML(exchange, iframeGetImg
                                .replace("${METHOD}", "GET")
                                .replace("${HOST}", THIRD_PARTY_HOST)
                                .replace("${REFCOLOR}", refColor));
                    } else if (path.startsWith("/iframe3/post/img")) {
                        add3rdPartyCookie(exchange);
                        sendHTML(exchange, iframeGetImg
                                .replace("${METHOD}", "POST")
                                .replace("${HOST}", THIRD_PARTY_HOST)
                                .replace("${REFCOLOR}", refColor));
                    } else if (path.startsWith("/iframe1/img")) {
                        add3rdPartyCookie(exchange);
                        sendHTML(exchange, iframeImage
                                .replace("${REFCOLOR}", refColor)
                                .replace("${HOST}", FIRST_PARTY_HOST));
                    } else if (path.startsWith("/initCookies")) {
                        sendHTML(exchange, initCookies.replace("${COOKIE}", cookie == null ? "<NONE>" : cookie));
                    } else if (path.startsWith("/postInitCookies")) {
                        set1stPartyCookie(exchange);
                    } else {
                        add3rdPartyCookie(exchange);
                        sendTextPlain(exchange, cookie, referer);
                    }
                });

        Undertow server = builder.build();
        server.start();

        for (final Undertow.ListenerInfo listener : server.getListenerInfo()) {
            int listenPort = ((InetSocketAddress) listener.getAddress()).getPort();
            log.info("3rd party server listening in port {}", listenPort);
        }
    }

    public static void start1stParty(int port) throws Exception {
        final String indexPage = new String(TestServer.class.getResourceAsStream("/index.html").readAllBytes(), "UTF-8");

        final Undertow.Builder builder = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setHandler((exchange) -> {
                    exchange.getResponseHeaders().put(ALLOW_ORIGIN, "*");
                    if ("/".equals(exchange.getRequestPath())) {
                        exchange.getResponseHeaders().put(Headers.LOCATION, "/index.html/" + REFERER_TAG);
                        exchange.setStatusCode(302);
                        exchange.endExchange();
                        return;
                    }

                    Cookie c = exchange.getRequestCookies().get(SESSION_COOKIE_NAME);

                    String cookie = c == null ? null : c.getValue();

                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
                    exchange.getResponseSender().send(indexPage
                            .replace("${FIRST_PARTY_HOST}", FIRST_PARTY_HOST)
                            .replace("${THIRD_PARTY_HOST}", THIRD_PARTY_HOST)
                            .replace("${COOKIE}", cookie == null ? "<NONE>" : cookie));
                });

        Undertow server = builder.build();
        server.start();

        for (final Undertow.ListenerInfo listener : server.getListenerInfo()) {
            int listenPort = ((InetSocketAddress) listener.getAddress()).getPort();
            log.info("1st party server listening in port {}", listenPort);
        }
    }
}
