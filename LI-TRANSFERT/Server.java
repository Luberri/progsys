import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private static final int PORT = 12345;
    private static final String CACHE_DIR = "cache";
    private static final String FILE_DIRECTORY = "server_files";  // Le répertoire où sont stockés les fichiers reçus
    private static final List<SlaveInfo> slaves = new ArrayList<>();
    private static Map<String, Socket> clients = new HashMap<>();

    // Ajout de slaves à la liste (remplacez par des IP et des ports appropriés)
    static {
        slaves.add(new SlaveInfo("127.0.0.1", 1235));
        // slaves.add(new SlaveInfo("192.168.162.168", 1235)); // Slave 1 // Slave 2
    }

    public static void main(String[] args) {
        try {
            // Créer le répertoire s'il n'existe pas
            File directory = new File(FILE_DIRECTORY);
            if (!directory.exists()) {
                directory.mkdir();
            }

            // Créer un serveur Socket pour écouter les connexions
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Serveur démarré. En attente de connexion...");

            while (true) {
                // Accepter la connexion du client
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connecté : " + clientSocket.getInetAddress());

                // Lancer un nouveau thread pour gérer la communication avec ce client
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Classe pour gérer la communication avec un client
    static class ClientHandler extends Thread {
        private Socket clientSocket;
        private InputStream in;
        private OutputStream out;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                in = clientSocket.getInputStream();
                out = clientSocket.getOutputStream();

                // Lire la commande envoyée par le client
                byte[] buffer = new byte[1024];
                int bytesRead;
                StringBuilder commandBuilder = new StringBuilder();

                // Lire la commande
                while ((bytesRead = in.read(buffer)) != -1) {
                    commandBuilder.append(new String(buffer, 0, bytesRead));
                    if (commandBuilder.toString().contains("\n")) {
                        break;
                    }
                }

                String command = commandBuilder.toString().trim();
                System.out.println("Commande reçue : " + command);

                if ("SEND".equals(command)) {
                    // Si la commande est SEND, recevoir le fichier
                    try {
                        receiveFile();
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } else if ("GET".equals(command)) {
                    // Si la commande est GET, envoyer le fichier
                    try {
                        sendFile();
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } else {
                    out.write("Commande non reconnue.\n".getBytes());
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (clientSocket != null) clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Méthode pour recevoir le fichier
        private void receiveFile() throws Exception {
            try {
                byte[] buffer = new byte[1024];
                int bytesRead;
                StringBuilder fileNameBuilder = new StringBuilder();
                
                // Lire le nom du fichier
                while ((bytesRead = in.read(buffer)) != -1) {
                    String part = new String(buffer, 0, bytesRead);
                    if (part.contains("\n")) {
                        fileNameBuilder.append(part.split("\n")[0]);
                        break;
                    }
                    fileNameBuilder.append(part);
                }

                String fileName = fileNameBuilder.toString().trim();
                System.out.println("Nom du fichier reçu: " + fileName);

                // Créer un fichier pour stocker les données reçues
                File outputFile = new File(FILE_DIRECTORY, fileName);
                try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                    // Recevoir les données du fichier
                    while ((bytesRead = in.read(buffer)) != -1) {
                        fileOut.write(buffer, 0, bytesRead);
                        if (new String(buffer, 0, bytesRead).contains("END")) {
                            break;
                        }
                    }
                    fileOut.close();
                    // Transfert du fichier vers le slave
                    sendtoSlave(fileName);
                    System.out.println("Fichier reçu et sauvegardé sous : " + outputFile.getAbsolutePath());
                    out.write("Fichier reçu avec succès.\n".getBytes());
                }
                if(outputFile.delete())
                {
                    System.out.println("SUCCESS");
                }else{
                    System.out.println("NONE");
                }

            } catch (IOException ex) {
                out.write("Erreur lors de la réception du fichier.\n".getBytes());
                ex.printStackTrace();
            }
        }

        private void sendFile() throws Exception {
            File histo = new File(FILE_DIRECTORY);

            if (histo.exists() && histo.isDirectory()) {
                File[] files = histo.listFiles();
                if (files != null && files.length > 0) {
                    System.out.println("Liste des fichiers dans le répertoire :");
                    for (File file : files) {
                        if (file.isFile()) {
                            System.out.println(file.getName() + "\n");
                            out.write((file.getName() + "\n").getBytes());
                        }
                    }
                    out.write("FIN\n".getBytes());

                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    String choosenFile = null;
                    StringBuilder message = new StringBuilder();
                    while ((bytesRead = in.read(buffer)) != -1) {
                        message.append(new String(buffer, 0, bytesRead));
                        if (message.toString().contains("\n")) {
                            choosenFile = message.toString().trim();
                            break;
                        }
                    }
                    System.out.println(choosenFile);
                    String[]parts=choosenFile.split("\\.");
                    choosenFile=parts[0]+"."+parts[1];
                    int numpart=Integer.parseInt(parts[2]);
                    int nbSlave=slaves.size();
                    int cpt=1;
                    for(int i=0;i<nbSlave;i++)
                    {
                    
                        String test=choosenFile+".part"+cpt;
                        if(getfromSlave(test, slaves.get(i))){
                            i--;
                            cpt++;
                        }
                        if(cpt>numpart)
                        {
                            break;
                        }
                    }
                    if(cpt<numpart)
                    {
                        System.out.println("fichier manquant");
                    }
                    cpt=1;
                    File tena=new File(CACHE_DIR,choosenFile);
                    FileOutputStream maka=new FileOutputStream(tena);
                    for(int i=0;i<numpart;i++)
                    {
                        String test=choosenFile+".part"+cpt;
                        File file = new File(CACHE_DIR, test);
                        try (FileInputStream fileIn = new FileInputStream(file)) {
                            while ((bytesRead = fileIn.read(buffer)) != -1) {
                                maka.write(buffer, 0, bytesRead);
                            }
                        }
                        cpt++;
                    }

                    File send=new File(CACHE_DIR,choosenFile);
                    
                    try (FileInputStream envoie=new FileInputStream(send);) {
                        while ((bytesRead = envoie.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        out.write("END\n".getBytes());
                    }
                    
                    
                    
                    
                    
                } else {
                    System.out.println("Aucun fichier trouvé dans le répertoire.");
                }
            } else {
                System.out.println("Le répertoire spécifié n'existe pas ou ce n'est pas un répertoire.");
            }
        }

        private void sendtoSlave(String fileName) throws Exception {
            int numparts=slaves.size();
            String histoname=fileName+"."+numparts+".txt";
            File history=new File(FILE_DIRECTORY,histoname);
            BufferedWriter writer = new BufferedWriter(new FileWriter(history));
            writer.write("Fichier divise en "+numparts);
            File division=new File(FILE_DIRECTORY,fileName);
            long filesize=division.length();
            long partsize=(filesize + numparts - 1) / numparts;

            byte[] buffer = new byte[1024];

            try (FileInputStream fis = new FileInputStream(division)) {
                for(int nb=0;nb<numparts;nb++){
                    try (Socket slaSocket = new Socket(slaves.get(0).getIp(), slaves.get(0).getPort());
                    OutputStream slaveOut = slaSocket.getOutputStream()) {
                        for (int i = 0; i < numparts; i++) {
                            String fichiernom=fileName +".part"+ (i + 1);
                            File outputFile = new File(fichiernom);
                            slaveOut.write(("PUT " + fichiernom + "\n").getBytes());
                            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                long bytesRead = 0;
                                int read;
                                while (bytesRead < partsize && (read = fis.read(buffer)) != -1) {
                                    slaveOut.write(buffer, 0, read);
                                    
                                }
                                slaveOut.write("END\n".getBytes());
                                System.out.println("Partie " + (i + 1) + " écrite dans : " + outputFile.getName());
                            }
                        }
                    }
                }
            }

        }

        private Boolean getfromSlave(String fileName,SlaveInfo slave) throws Exception {
            Boolean rep=true;
            try (Socket slaSocket = new Socket(slave.getIp(), slave.getPort());
                 InputStream slaveIn = slaSocket.getInputStream();
                 OutputStream slaveOut = slaSocket.getOutputStream();
                 FileOutputStream fileOut = new FileOutputStream(new File(CACHE_DIR, fileName))) {

                slaveOut.write(("GET " + fileName + "\n").getBytes());

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = slaveIn.read(buffer)) != -1) {
                    if (new String(buffer, 0, bytesRead).contains("NOPE")) {
                        rep=false;
                        break;
                    }
                    fileOut.write(buffer, 0, bytesRead);
                    if (new String(buffer, 0, bytesRead).contains("END")) {
                        break;
                    }
                }
            }
            System.out.println("Fichier récupéré depuis le slave.");
            return rep;
        }
    }

    private static class SlaveInfo {
        private String ip;
        private int port;

        public SlaveInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }
    }
}
