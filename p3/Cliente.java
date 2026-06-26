import java.io.*;
import java.net.*;
import java.util.*;
import javazoom.jl.player.Player;

/**
 * CLIENTE UDP — Go-Back-N con streaming en tiempo real
 *
 * DIFERENCIA GENERAL: Se eliminó el menú completo, la opción de reproducción
 * local, el HashMap de ensamblado y el FileOutputStream. El flujo ahora es
 * lineal: conectar → elegir → descargar+reproducir simultáneamente.
 */
public class Cliente {

    static final String SERVIDOR = "localhost";
    static final int PUERTO = 9876;
    static final int TIMEOUT = 2000;

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress addr = InetAddress.getByName(SERVIDOR);
        socket.setSoTimeout(TIMEOUT);
        Scanner sc = new Scanner(System.in);

        // ── 1. Pedir catálogo ─────────────────────────────────────────────────
        Paquete req = new Paquete(Paquete.CATALOGO, 0, 0, new byte[0]);
        enviar(socket, req, addr, PUERTO);

        byte[] buf = new byte[65535];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        socket.receive(dp);
        Paquete catalogo = Paquete.desdBytes(dp.getData(), dp.getLength());

        String[] canciones = new String(catalogo.datos).split("\n");
        System.out.println("=== Catálogo del Servidor ===");
        for (int i = 0; i < canciones.length; i++) {
            System.out.println(i + ". " + canciones[i]);
        }

        // ── 2. El usuario elige ───────────────────────────────────────────────
        System.out.print("\nNúmero de canción a reproducir: ");
        int eleccion = Integer.parseInt(sc.nextLine().trim());

        if (eleccion < 0 || eleccion >= canciones.length) {
            System.out.println("Selección inválida.");
            socket.close();
            return;
        }

        // ── 3. Pedir la canción al servidor ───────────────────────────────────
        Paquete pedir = new Paquete(Paquete.PEDIR, eleccion, 0, new byte[0]);
        enviar(socket, pedir, addr, PUERTO);
        System.out.println(
            "Descargando y reproduciendo '" + canciones[eleccion] + "'..."
        );

        // ── 4. Crear el pipe ──────────────────────────────────────────────────
        // DIFERENCIA: reemplaza al HashMap<Integer, byte[]>.
        // El pipe conecta el hilo de descarga con el hilo de reproducción.
        PipedOutputStream pipOut = new PipedOutputStream();
        PipedInputStream pipIn = new PipedInputStream(pipOut);

        // ── 5. Lanzar hilo de reproducción ────────────────────────────────────
        // DIFERENCIA: antes la reproducción era llamada al final tras ensamblar.
        // Ahora se lanza antes del bucle de descarga para que ambos corran en paralelo.
        // El hilo se bloquea automáticamente cuando el pipe está vacío y reanuda
        // en cuanto el hilo de descarga escribe más datos.
        Thread hiloReproduccion = new Thread(() -> reproducir(pipIn));
        hiloReproduccion.start();

        // ── 6. Bucle de descarga GBN ──────────────────────────────────────────
        int esperado = 0;

        // DIFERENCIA: se eliminó el HashMap. Ya no se acumula nada,
        // cada fragmento se escribe al pipe inmediatamente al llegar en orden.
        while (true) {
            try {
                byte[] rbuf = new byte[65535];
                DatagramPacket rdp = new DatagramPacket(rbuf, rbuf.length);
                socket.receive(rdp);
                Paquete pkt = Paquete.desdBytes(rdp.getData(), rdp.getLength());

                if (pkt.tipo == Paquete.FIN) {
                    System.out.println(
                        "\nFIN recibido. Total paquetes: " + pkt.numSec
                    );
                    break;
                }

                if (pkt.tipo == Paquete.DATOS) {
                    System.out.print(
                        "\r  Recibido seq=" + pkt.numSec + "/" + (pkt.total - 1)
                    );

                    if (pkt.numSec == esperado) {
                        // DIFERENCIA: antes era recibidos.put(pkt.numSec, pkt.datos)
                        // Ahora se escribe directo al pipe para que JLayer
                        // decodifique en tiempo real sin esperar al final.
                        pipOut.write(pkt.datos);

                        Paquete ack = new Paquete(
                            Paquete.ACK,
                            esperado,
                            0,
                            new byte[0]
                        );
                        enviar(socket, ack, addr, PUERTO);
                        esperado++;
                    } else {
                        System.out.print(" [fuera de orden, descartado]");
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("\nTimeout esperando datos.");
                break;
            }
        }

        // ── 7. Cerrar el pipe y esperar que termine la reproducción ───────────
        // DIFERENCIA: antes aquí estaba todo el bloque de ensamblado con
        // FileOutputStream y el bucle for(i=0..total). Ahora son dos líneas.
        // Cerrar el pipe le indica a JLayer que no hay más datos,
        // lo que hace que el hilo de reproducción termine limpiamente.
        pipOut.close();
        hiloReproduccion.join(); // esperar a que JLayer termine de decodificar

        socket.close();
        System.out.println("Reproducción finalizada.");
    }

    // DIFERENCIA: antes recibía un File y abría un FileInputStream internamente.
    // Ahora recibe directamente un InputStream, que puede ser cualquier fuente,
    // en este caso el extremo de lectura del pipe.
    static void reproducir(InputStream stream) {
        try {
            Player player = new Player(stream);
            player.play();
        } catch (Exception e) {
            System.err.println(
                "Error durante la reproducción: " + e.getMessage()
            );
        }
    }

    static void enviar(
        DatagramSocket s,
        Paquete p,
        InetAddress addr,
        int puerto
    ) throws Exception {
        byte[] raw = p.aBytes();
        s.send(new DatagramPacket(raw, raw.length, addr, puerto));
    }
}
