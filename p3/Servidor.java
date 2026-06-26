import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class Servidor {

    static final int PUERTO = 9876;
    static final int VENTANA = 4; // tamaño de ventana GBN
    static final int TIMEOUT = 1000; // ms
    static final int CHUNK = 1024; // bytes por paquete
    static final String CARPETA = "songs/";

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(PUERTO);
        System.out.println("Servidor escuchando en puerto " + PUERTO + "...");

        List<String> canciones = cargarCanciones();
        System.out.println("Canciones disponibles: " + canciones);

        byte[] buf = new byte[65535]; //Buffer para UDP

        while (true) {
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            socket.receive(dp);
            Paquete pkt = Paquete.desdBytes(dp.getData(), dp.getLength());

            InetAddress cliente = dp.getAddress();
            int puerto = dp.getPort();

            if (pkt.tipo == Paquete.CATALOGO) {
                //1-> Fase de catalogo | Enviar lista de canciones como texto
                String lista = String.join("\n", canciones);
                Paquete resp = new Paquete(Paquete.CATALOGO, 0, 0, lista.getBytes());
                enviar(socket, resp, cliente, puerto);
                System.out.println("Catálogo enviado a " + cliente);
            } else if (pkt.tipo == Paquete.PEDIR) {
                //2-> Fase de descarga
                int idx = pkt.numSec;
                System.out.println("Solicitud de canción #" + idx + ": " + canciones.get(idx));
                enviarCancion(socket, CARPETA + canciones.get(idx), cliente, puerto);
            }
        }
    }

    static void enviarCancion(DatagramSocket socket, String ruta, InetAddress cliente, int puerto) throws Exception {
        byte[] archivo = Files.readAllBytes(Path.of(ruta));

        //Se crea una lista de fragmentos de la cancion
        List<byte[]> fragmentos = new ArrayList<>();
        for (int i = 0; i < archivo.length; i += CHUNK) {
            int fin = Math.min(i + CHUNK, archivo.length);
            fragmentos.add(Arrays.copyOfRange(archivo, i, fin));
        }

        int total = fragmentos.size();
        System.out.println("Enviando " + total + " paquetes con ventana " + VENTANA);

        socket.setSoTimeout(TIMEOUT);

        int base = 0; // inicio de la ventana
        int sig = 0; // siguiente paquete a enviar

        while (base < total) {
            // Enviar todos los paquetes en la ventana que faltan enviar
            while (sig < base + VENTANA && sig < total) {
                Paquete p = new Paquete(Paquete.DATOS, sig, total, fragmentos.get(sig));
                enviar(socket, p, cliente, puerto);
                System.out.println("  TX seq=" + sig);
                sig++;
            }

            // Esperar ACK
            try {
                byte[] ackBuf = new byte[64];
                DatagramPacket ackDp = new DatagramPacket(ackBuf, ackBuf.length);
                socket.receive(ackDp);
                Paquete ack = Paquete.desdBytes(ackDp.getData(), ackDp.getLength());

                if (ack.tipo == Paquete.ACK) {
                    System.out.println("  ACK=" + ack.numSec);
                    base = ack.numSec + 1; // avanzar ventana
                }
            } catch (SocketTimeoutException e) {
                // Timeout: retroceder y reenviar toda la ventana
                System.out.println("  TIMEOUT, retrocediendo a base=" + base);
                sig = base;
            }
        }

        socket.setSoTimeout(0);
        Paquete fin = new Paquete(Paquete.FIN, total, total, new byte[0]);
        enviar(socket, fin, cliente, puerto);
        System.out.println("FIN enviado.");
    }

    static List<String> cargarCanciones() {
        File dir = new File(CARPETA);
        if (!dir.exists()) dir.mkdirs();
        String[] archivos = dir.list((d, n) -> n.endsWith(".mp3"));
        return archivos == null ? new ArrayList<>() : Arrays.asList(archivos);
    }

    static void enviar(DatagramSocket s, Paquete p, InetAddress addr, int puerto) throws Exception {
        byte[] raw = p.aBytes();
        s.send(new DatagramPacket(raw, raw.length, addr, puerto));
    }
}
