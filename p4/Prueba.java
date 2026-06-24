import java.io.*;
import java.net.*;
// Biblioteca de I/O y red
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
// Biblioteca Jsoup para scraping
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Prueba {

    //Creamos un diccionario para los url visitados
    private static Set<String> visited = new HashSet<>();

    //Pool de 5 hilos para los elementos incrustados
    private static ExecutorService poolHilos = Executors.newFixedThreadPool(5);

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        System.out.print("Ingresa una URL: ");
        String dir = sc.nextLine();

        System.out.print("Ingresa el nivel de profundidad : ");
        int profundidad = sc.nextInt();

        URI uri = new URI(dir);
        String host = uri.getHost();

        createDirectory(host);

        //Invocamos a la descarga pasandole el host y la profundidad
        descarga(uri.toString(), host, profundidad, 0);

        poolHilos.shutdown();
        sc.close();
    }

    //nivelRestante me ayuda a parar al alcanzar la profundidad indicada
    private static void descarga(
        String urlStr,
        String baseDir,
        int nivelRestante,
        int nivelActual
    ) {
        //Validamos en el diccionario si ya visitamos o no este link :p
        if (visited.contains(urlStr)) return;
        visited.add(urlStr);

        try {
            URI uri = new URI(urlStr);
            URL url = uri.toURL();
            HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setInstanceFollowRedirects(false);

            int responseCode = connection.getResponseCode();

            //Majeno de los errores 301 y 302
            if (
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP
            ) {
                String nuevaUrl = connection.getHeaderField("Location");
                System.out.println("Redirección 301/302 -> " + nuevaUrl);
                descarga(nuevaUrl, baseDir, nivelRestante, nivelActual); //No se decrementa el nivel porque solo lo estamos alcanzando en su nueva direccion
                return;
            } else if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                System.out.println("Error 403 (Prohibido :()) en: " + urlStr);
                return;
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                System.out.println(
                    "Error 404 (No encontrado :()) en: " + urlStr
                );
                return;
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                System.out.println("Error " + responseCode + " en: " + urlStr);
                return;
            }

            //Construimos el path donde se van a descargar los recursos
            String slashPath = uri.getPath();
            if (
                slashPath == null ||
                slashPath.equals("/") ||
                slashPath.isEmpty()
            ) {
                slashPath = "/index.html";
            } else if (slashPath.endsWith("/")) {
                slashPath += "index.html";
            }

            String filePath = "./" + baseDir + slashPath;
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());

            //Escritura en el disco
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192]; //Usamos un buffer de 8 kb
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                Files.write(path, baos.toByteArray());
            }

            System.out.println(
                "Descargado [Nivel : " + nivelActual + "]: " + filePath
            );

            String contentType = connection.getContentType();
            if (contentType != null && contentType.contains("text/html")) {
                //Si encontramos un HTML, entonces buscamos elementos incrustados
                File file = new File(filePath);
                Document doc = Jsoup.parse(file, "UTF-8", urlStr);

                Elements incrustados = doc.select(
                    "img[src], link[rel=stylesheet], script[src]"
                );
                for (Element elem : incrustados) {
                    //Para cada elemento incrustado se usa un hilo del pool
                    String absUrl = elem.hasAttr("src")
                        ? elem.absUrl("src")
                        : elem.absUrl("href");
                    if (!absUrl.isEmpty() && !visited.contains(absUrl)) {
                        poolHilos.submit(() ->
                            descargarElementoConHilo(absUrl, baseDir)
                        );
                    }
                }

                //Evaluamos en que nivel estamos, condicion base de la recursión
                if (nivelRestante > 0) {
                    Elements hipervinculos = doc.select("a[href]");
                    for (Element link : hipervinculos) {
                        String absUrl = link.absUrl("href");
                        if (absUrl.startsWith("http")) {
                            descarga(
                                absUrl,
                                baseDir,
                                nivelRestante - 1,
                                nivelActual + 1
                            ); //Aqui se decrementa para alcanzar la condicion base y no crear bucles
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error en " + urlStr + ": " + e.getMessage());
        }
    }

    //Función para descargar elementos incrustados con hilos del pool
    private static void descargarElementoConHilo(
        String urlStr,
        String baseDir
    ) {
        if (visited.contains(urlStr)) return;
        visited.add(urlStr);

        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET"); //Petición GET
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                //SI obtenemos un 200 significa que todo ok
                String pathName = url.getPath();
                if (pathName == null || pathName.equals("/")) pathName =
                    "/recurso_" + System.currentTimeMillis();

                Path targetPath = Paths.get("./" + baseDir + pathName);
                Files.createDirectories(targetPath.getParent());

                try (
                    InputStream in = connection.getInputStream();
                    OutputStream out = Files.newOutputStream(targetPath)
                ) {
                    byte[] buffer = new byte[8192]; //Buffer de 8 kb
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                System.out.println("  [Hilo] -> " + targetPath.getFileName());
            }
        } catch (Exception e) {
            //Ignoramos errores
        }
    }

    //Función para crear directorios
    private static void createDirectory(String directoryName) {
        String dir = "./" + directoryName;
        Path path = Paths.get(dir);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            System.out.println("No se pudo crear el directorio base");
        }
    }
}
