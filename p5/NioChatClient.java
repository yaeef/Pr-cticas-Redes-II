import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets; //pARA LOS EMOJIS
import java.util.Iterator;
import java.util.Scanner;

public class NioChatClient {

    private static final String HOST = "localhost";
    private static final int PORT = 9015;
    private SocketChannel socketChannel;
    private Selector selector;
    private String username;
    private String currentRoom = "general";

    public void start() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Ingrese su nombre de usuario: ");
        username = scanner.nextLine().trim();

        selector = Selector.open();
        socketChannel = SocketChannel.open(new InetSocketAddress(HOST, PORT));
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);

        sendPacket("nuevo_usuario", "", username, "");

        // Hilo de la Consola (UI)
        new Thread(() -> {
            System.out.println("\n--- INTERFAZ DISPONIBLE ---");
            System.out.println(
                " /create <sala> -> Crea una sala (Seras el due\u00f1o)"
            );
            System.out.println(
                " /delete <sala> -> Borra una sala (Solo si eres due\u00f1o)"
            );
            System.out.println(" /exit -> Cerrar sesion.");
            System.out.println(" /join <sala>   -> Ingresa a una sala");
            System.out.println(" /leave         -> Regresa a la sala general");
            System.out.println(
                " Escribe texto libre para mandar mensajes a tu sala actual.\n"
            );

            while (true) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                try {
                    if (line.startsWith("/create ")) {
                        String targetRoom = line.substring(8).trim();
                        sendPacket("crear_sala", targetRoom, username, "");
                    } else if (line.startsWith("/delete ")) {
                        String targetRoom = line.substring(8).trim();
                        sendPacket("borrar_sala", targetRoom, username, "");
                    } else if (line.startsWith("/join ")) {
                        String newRoom = line.substring(6).trim();
                        currentRoom = newRoom;
                        sendPacket("ingresar", currentRoom, username, "");
                    } else if (line.equals("/leave")) {
                        sendPacket("salir", currentRoom, username, "");
                        currentRoom = "general";
                    } else if (line.equals("/exit")) {
                        System.out.println(
                            "[+] Cerrando aplicacion de forma segura..."
                        );
                        socketChannel.close(); // El servidor detectara el cierre de canal instantaneamente
                        System.exit(0);
                    } else {
                        sendPacket("msj", currentRoom, username, line);
                    }
                } catch (IOException e) {
                    System.out.println(
                        "[-] Error de conexion con el core de red."
                    );
                    break;
                }
            }
        }).start();

        // Reactor loop No Bloqueante
        while (true) {
            if (selector.select() == 0) continue;
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (key.isReadable()) {
                    handleRead();
                }
            }
        }
    }

    private void handleRead() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(2048);
        int bytesRead = socketChannel.read(buffer);
        if (bytesRead == -1) {
            System.out.println("[-] Desconectado del servidor.");
            System.exit(0);
        }
        String msg = new String(
            buffer.array(),
            0,
            bytesRead,
            java.nio.charset.StandardCharsets.UTF_8
        ); //Codificación para emojis :)
        System.out.print(msg);
    }

    private void sendPacket(
        String tipo,
        String sala,
        String usuario,
        String contenido
    ) throws IOException {
        StringBuilder packet = new StringBuilder();
        packet.append("<tipo>").append(tipo);
        if (!sala.isEmpty()) packet.append("<sala>").append(sala);
        if (!usuario.isEmpty()) packet.append("<usuario>").append(usuario);
        if (!contenido.isEmpty()) packet
            .append("<contenido>")
            .append(contenido);

        socketChannel.write(
            ByteBuffer.wrap(
                packet
                    .toString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        ); //Codificación UTF-8
    }

    public static void main(String[] args) throws IOException {
        //Forzar stdout a UTF-8
        System.setOut(
            new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8)
        );
        System.setErr(
            new java.io.PrintStream(System.err, true, StandardCharsets.UTF_8)
        );
        new NioChatClient().start();
    }
}
