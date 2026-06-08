import java.io.*;
import java.lang.annotation.Documented;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class wget_sencillo {

    private static int depthLevel = 2; //Max de profundidad :p
    private static final int HILOS = 8; //Hilos simultaneos
    private static final Set<String> visited = new HashSet<>();
    private static final Object visitedLock = new Object();
    private static ThreadPoolExecutor pool;

    //Main
    public static void main(String[] args) throws Exception {
        //Entradas
        Scanner sc = new Scanner(System.in);

        System.out.print("Ingresa la URL: ");
        String url = sc.nextLine().trim();

        System.out.print("Ingresa el nivel de profundidad:  ");
        depthLevel = Integer.parseInt(sc.nextLine().trim());
        sc.close();

        //Creando carpeta
        String host = new URI(url).getHost();
        createDirectory(host);

        //Pool de Hilos
        pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(HILOS);

        //Primera tarea base
        submitDownload(url, host, 0);

        //Tareas
        while (pool.getActiveCount() > 0 || pool.getQueue().size() > 0) {
            Thread.sleep(200);
        }
        pool.shutdown();

        //Dercarga términada
        System.out.println("\n Descarga terminada :).");
    }

    //Hace la petición de descarga si es que aun no se ha descargado el archivo
    private static void submitDownload(String url, String baseDir, int depth) {
        if (depth > depthLevel) return;

        //Solo un hilo a la vez con synchronized
        synchronized (visitedLock) {
            if (visited.contains(url)) return; //Si ya se visito ya no lo agregues
            visited.add(url); //Si no se visito entonces agregalo
        }

        //Enviamos la descarga al pool
        pool.submit(() -> downloadFile(url, baseDir, depth));
    }

    //Método principal que hace la descarga del archivo
    private static void downloadFile(String urlStr, String baseDir, int depth) {
        try {
            URI uri = new URI(urlStr);
            URL url = uri.toURL();
            String scheme = uri.getScheme();
            String path = uri.getPath();

            //Se abre la conexión
            HttpURLConnection conn;
            if (scheme.equalsIgnoreCase("https")) {
                conn = (HttpsURLConnection) url.openConnection();
            } else if (scheme.equalsIgnoreCase("http")) {
                conn = (HttpURLConnection) url.openConnection();
            } else {
                System.err.println(
                    "[ERROR] El protocolo debe ser http o https: " + urlStr
                );
                return;
            }

            //Petición GET
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(8_000); //Máximo 8s para establecer conec tcp
            conn.setReadTimeout(15_000); //Máximo 15s esperando respuesta

            //Lectura de código de estado
            int code = conn.getResponseCode();

            //200
            if (code == HttpURLConnection.HTTP_OK) {
                String contentType =
                    conn.getContentType() != null ? conn.getContentType() : "";
                String filePath;

                if (path == null || path.equals("/") || path.isEmpty()) {
                    filePath = "./" + baseDir + "/index.html";
                } else {
                    filePath = "./" + baseDir + path;
                    if (filePath.endsWith("/")) {
                        filePath += "index.html";
                    } else if (
                        contentType.contains("text/html") &&
                        !filePath.toLowerCase().endsWith(".html") &&
                        !filePath.toLowerCase().endsWith(".htm")
                    ) {
                        filePath += ".html";
                    }
                }

                final String finalPath = filePath;

                //Lectura de GET Response Body
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
                }

                //Guardado en Disco
                Path p = Paths.get(finalPath);
                Files.createDirectories(p.getParent());
                Files.write(p, baos.toByteArray());
                System.out.printf("[OK] %s%n", finalPath);

                //Búsqueda de elementos incrustados en el html/css/js/
                String lowerPath = finalPath.toLowerCase();
                String body = baos.toString("UTF-8");

                if (contentType.contains("text/html") && depth < depthLevel) {
                    pool.submit(() ->
                        getDependences(finalPath, urlStr, baseDir, depth + 1)
                    );
                } else if (
                    contentType.contains("text/css") ||
                    lowerPath.endsWith(".css")
                ) {
                    pool.submit(() ->
                        getCssDependences(body, urlStr, baseDir, depth + 1)
                    );
                } else if (
                    contentType.contains("javascript") ||
                    lowerPath.endsWith(".js")
                ) {
                    pool.submit(() ->
                        getJsDependences(body, urlStr, baseDir, depth + 1)
                    );
                }
            } else if (
                code == 301 || code == 302 || code == 307 || code == 308
            ) {
                //Recurso en otro lado
                String location = conn.getHeaderField("Location");
                if (location != null) submitDownload(location, baseDir, depth);
            } else {
                System.out.printf("[%d] %s%n", code, urlStr);
            }
        } catch (Exception e) {
            System.err.printf("[ERROR] %s -> %s%n", urlStr, e.getMessage());
        }
    }

    //Crear carpeta raiz
    private static void createDirectory(String name) {
        try {
            Files.createDirectories(Paths.get("./" + name));
        } catch (Exception e) {
            System.err.println(
                "No se pudo crear el directorio raiz :( :" + e.getMessage()
            );
        }
    }

    //Parseo de objetos incrustados
    private static void getDependences(
        String filePath,
        String baseUri,
        String baseDir,
        int depth
    ) {
        try {
            Document doc = Jsoup.parse(new File(filePath), "UTF-8", baseUri);

            for (Element el : doc.select("[src],[href],[data-src],[poster]")) {
                String abs = "";

                if (el.hasAttr("src")) abs = el.absUrl("src");
                else if (el.hasAttr("href")) abs = el.absUrl("href");
                else if (el.hasAttr("data-src")) abs = el.absUrl("data-src");
                else if (el.hasAttr("poster")) abs = el.absUrl("poster");

                if (
                    !abs.isEmpty() &&
                    !abs.startsWith("mailto:") &&
                    !abs.startsWith("javascript:") &&
                    !abs.startsWith("#")
                ) {
                    submitDownload(abs, baseDir, depth);
                }
            }
        } catch (IOException e) {
            System.err.println("[HTML-DEP] " + e.getMessage());
        }
    }

    //Objetos CSS incrustados
    private static void getCssDependences(
        String css,
        String baseUri,
        String baseDir,
        int depth
    ) {
        var pattern = java.util.regex.Pattern.compile(
            "url\\(['\"]?([^'\"\\)]+)['\"]?\\)"
        );
        var matcher = pattern.matcher(css);
        while (matcher.find()) {
            try {
                String abs = new URI(baseUri)
                    .resolve(matcher.group(1).trim())
                    .toURL()
                    .toString();
                submitDownload(abs, baseDir, depth);
            } catch (Exception ignored) {}
        }
    }

    //Objetos JS incrustados
    private static void getJsDependences(
        String js,
        String baseUri,
        String baseDir,
        int depth
    ) {
        var pattern = java.util.regex.Pattern.compile(
            "?:src|href|url|import|fetch)\\s*\\(?['\"]([^'\"\\)]+)['\"]\\)?"
        );
        var matcher = pattern.matcher(js);
        while (matcher.find()) {
            String res = matcher.group(1).trim();
            if (
                !res.startsWith("data:") &&
                !res.startsWith("mailto:") &&
                !res.startsWith("javascript")
            ) {
                try {
                    String abs = new URI(baseUri)
                        .resolve(res)
                        .toURL()
                        .toString();
                    submitDownload(abs, baseDir, depth);
                } catch (Exception ignored) {}
            }
        }
    }
}
