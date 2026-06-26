import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NioChatServer {

    private static final int PORT = 9015;
    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;

    // Estado del Sistema
    private final Map<SocketChannel, String> channelToUser =
        new ConcurrentHashMap<>();
    private final Map<String, Set<SocketChannel>> rooms =
        new ConcurrentHashMap<>();
    private final Map<String, String> roomToCreator = new ConcurrentHashMap<>(); // Rastrear dueños

    public NioChatServer() throws IOException {
        this.selector = Selector.open();
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.bind(new InetSocketAddress(PORT));
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Sala por defecto
        rooms.put("general", Collections.synchronizedSet(new HashSet<>()));
        System.out.println("[-] Servidor iniciado en el puerto " + PORT);
        printServerStatus();
    }

    public void start() throws IOException {
        while (true) {
            if (selector.select() == 0) continue;

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) continue;

                if (key.isAcceptable()) {
                    handleAccept();
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
    }

    private void handleRead(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(2048);
        try {
            int bytesRead = channel.read(buffer);
            if (bytesRead == -1) {
                disconnect(channel);
                return;
            }

            String rawMessage = new String(
                buffer.array(),
                0,
                bytesRead,
                StandardCharsets.UTF_8
            ).trim();
            processProtocol(channel, rawMessage);
        } catch (IOException e) {
            disconnect(channel);
        }
    }

    private void processProtocol(SocketChannel sender, String rawMessage)
        throws IOException {
        String tipo = parseTag(rawMessage, "tipo");
        String usuario = parseTag(rawMessage, "usuario");
        String salaName = parseTag(rawMessage, "sala");
        String contenido = parseTag(rawMessage, "contenido");

        switch (tipo) {
            case "nuevo_usuario":
                channelToUser.put(sender, usuario);
                rooms.get("general").add(sender);
                broadcastToRoom(
                    "general",
                    "[SERVER] " + usuario + " entro al servidor."
                );
                printServerStatus();
                break;
            case "crear_sala":
                if (rooms.containsKey(salaName)) {
                    sendDirectMessage(
                        sender,
                        "[SERVER ERROR] La sala '" + salaName + "' ya existe.\n"
                    );
                } else if (salaName.equalsIgnoreCase("general")) {
                    sendDirectMessage(
                        sender,
                        "[SERVER ERROR] Nombre reservado por el sistema.\n"
                    );
                } else {
                    rooms.put(
                        salaName,
                        Collections.synchronizedSet(new HashSet<>())
                    );
                    roomToCreator.put(salaName, usuario);
                    sendDirectMessage(
                        sender,
                        "[SERVER] Sala '" +
                            salaName +
                            "' creada. Eres el administrador.\n"
                    );
                    printServerStatus();
                }
                break;
            case "borrar_sala":
                if (!rooms.containsKey(salaName)) {
                    sendDirectMessage(
                        sender,
                        "[SERVER ERROR] La sala '" + salaName + "' no existe.\n"
                    );
                } else if (salaName.equals("general")) {
                    sendDirectMessage(
                        sender,
                        "[SERVER ERROR] Operacion prohibida: Sala general es estructural.\n"
                    );
                } else {
                    String creator = roomToCreator.get(salaName);
                    if (creator != null && creator.equals(usuario)) {
                        // Expulsar de forma segura a los miembros de vuelta a general
                        Set<SocketChannel> members = rooms.get(salaName);
                        broadcastToRoom(
                            salaName,
                            "[SERVER] Esta sala ha sido destruida por su creador (" +
                                creator +
                                "). Volviendo a general..."
                        );

                        synchronized (members) {
                            for (SocketChannel member : members) {
                                rooms.get("general").add(member);
                            }
                        }
                        rooms.remove(salaName);
                        roomToCreator.remove(salaName);

                        printServerStatus();
                        broadcastToRoom(
                            "general",
                            "[SERVER] La sala '" +
                                salaName +
                                "' fue eliminada de la topologia global."
                        );
                    } else {
                        sendDirectMessage(
                            sender,
                            "[SERVER ERROR] Permiso denegado. Solo el creador (" +
                                creator +
                                ") puede borrarla.\n"
                        );
                    }
                }
                break;
            case "ingresar":
                if (!rooms.containsKey(salaName)) {
                    sendDirectMessage(
                        sender,
                        "[SERVER ERROR] La sala '" +
                            salaName +
                            "' no existe. Creala primero con /create\n"
                    );
                    return;
                }
                removeFromAllRooms(sender);
                rooms.get(salaName).add(sender);

                if (salaName.equals("general")) {
                    sendUserList(sender);
                } else {
                    broadcastToRoom(
                        salaName,
                        "[SALA] " + usuario + " se unio a la sala."
                    );
                }
                printServerStatus();
                break;
            case "msj":
                if (
                    rooms.containsKey(salaName) &&
                    rooms.get(salaName).contains(sender)
                ) {
                    broadcastToRoom(salaName, "<" + usuario + "> " + contenido);
                }
                break;
            case "salir":
                if (rooms.containsKey(salaName)) {
                    rooms.get(salaName).remove(sender);
                    broadcastToRoom(
                        salaName,
                        "[SALA] " + usuario + " salio de la sala."
                    );
                    rooms.get("general").add(sender);
                    sendUserList(sender);
                    printServerStatus();
                }
                break;
        }
    }

    private void printServerStatus() {
        System.out.println(
            "\n======================= LOG DE ESTADO (NIO) ======================="
        );
        System.out.println(
            "[USUARIOS CONECTADOS (" +
                channelToUser.size() +
                ")]: " +
                channelToUser.values()
        );
        System.out.println("[SALAS ACTIVAS Y METADATOS]:");
        for (String room : rooms.keySet()) {
            String owner = roomToCreator.getOrDefault(room, "Sistema");
            int size = rooms.get(room).size();
            System.out.println(
                "  -> #" +
                    room +
                    " | Creador/Owner: " +
                    owner +
                    " | Miembros adentro: " +
                    size
            );
        }
        System.out.println(
            "===================================================================\n"
        );
    }

    private void broadcastToRoom(String roomName, String message)
        throws IOException {
        Set<SocketChannel> members = rooms.get(roomName);
        if (members == null) return;
        ByteBuffer buffer = ByteBuffer.wrap(
            (message + "\n").getBytes(StandardCharsets.UTF_8)
        ); //Codificación utf-8
        synchronized (members) {
            for (SocketChannel client : members) {
                if (client.isOpen()) {
                    buffer.rewind();
                    client.write(buffer);
                }
            }
        }
    }

    private void sendDirectMessage(SocketChannel client, String msg)
        throws IOException {
        if (client.isOpen()) {
            client.write(ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void sendUserList(SocketChannel receiver) throws IOException {
        StringBuilder sb = new StringBuilder(
            "[SALA GENERAL] Lista de usuarios globales:\n"
        );
        for (String user : channelToUser.values()) {
            sb.append(" -> ").append(user).append("\n");
        }
        sendDirectMessage(receiver, sb.toString());
    }

    private void removeFromAllRooms(SocketChannel client) {
        for (Set<SocketChannel> room : rooms.values()) {
            room.remove(client);
        }
    }

    private void disconnect(SocketChannel channel) {
        try {
            String user = channelToUser.remove(channel);
            removeFromAllRooms(channel);
            if (user != null) {
                broadcastToRoom(
                    "general",
                    "[SERVER] " + user + " cerro sesion."
                );
                printServerStatus();
            }
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String parseTag(String msg, String tag) {
        String open = "<" + tag + ">";
        int start = msg.indexOf(open);
        if (start == -1) return "";
        start += open.length();
        int end = msg.indexOf("<", start);
        if (end == -1) return msg.substring(start);
        return msg.substring(start, end);
    }

    public static void main(String[] args) throws IOException {
        new NioChatServer().start();
    }
}
