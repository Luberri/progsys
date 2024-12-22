import java.io.*;
import java.net.*;

public class Slave {
    private static final String STORAGE_DIR = "slave/"; // Dossier de stockage des fichiers dans le PUT
    
    public static void main(String[] args) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(1235)) {
            System.out.println("Slave en attente de connexion...");
            
            // Vérification de l'existence du répertoire de stockage
            File storageDir = new File(STORAGE_DIR);
            if (!storageDir.exists()) {
                storageDir.mkdirs(); // Création du répertoire s'il n'existe pas
            }
            
            while (true) {
                try {
                    Socket sock = serverSocket.accept();
                    InputStream in = sock.getInputStream();
                    OutputStream out = sock.getOutputStream();
                    System.out.println("Client connecté au Slave.");

                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    String command = null;
                    
                    // Lire la commande du client
                    while ((bytesRead = in.read(buffer)) != -1) {
                        String part = new String(buffer, 0, bytesRead);
                        if (part.contains("\n")) {
                            command = part.split("\n")[0];
                            break;
                        }
                    }
                    System.out.println("Commande reçue : " + command);

                    // Traiter la commande reçue
                    if (command.startsWith("GET")) {
                        String fileName = command.split(" ")[1].trim();
                        sendFile(fileName, out); // Envoie du fichier
                    } else if (command.startsWith("PUT")) {
                        String fileName = command.split(" ")[1].trim();
                        System.out.println("Réception du fichier : " + fileName);
                        try {
                            receiveFile(fileName, in); // Réception du fichier
                            out.write("Fichier reçu avec succès.\n".getBytes()); // Confirmer la réception
                        } catch (Exception ex) {
                            out.write("Erreur lors de la réception du fichier.\n".getBytes());
                            ex.printStackTrace();
                        }
                    } else {
                        System.out.println("Commande invalide.");
                    }

                    // Réponse de confirmation
                    out.flush();
                    
                    // Fermer la connexion
                    sock.close();
                    System.out.println("Serveur prêt à accepter une nouvelle connexion...");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Méthode pour recevoir le fichier
    private static void receiveFile(String filename, InputStream in) throws Exception {
        File outputFile = new File(STORAGE_DIR, filename);
        FileOutputStream fileOut = new FileOutputStream(outputFile);
        byte[] buffer = new byte[1024];
        int bytesRead;
        long totalBytesRead = 0;

        // Recevoir les données du fichier
        while ((bytesRead = in.read(buffer)) != -1) {
            fileOut.write(buffer, 0, bytesRead);
            totalBytesRead += bytesRead;
            if (new String(buffer, 0, bytesRead).contains("END")) {
                break;
            }
        }
        fileOut.close();
        System.out.println("Fichier reçu et sauvegardé sous : " + outputFile.getAbsolutePath());
    }

    // Méthode pour envoyer le fichier
    private static void sendFile(String filename, OutputStream out) throws Exception {
        File outputFile = new File(STORAGE_DIR, filename);
        if (!outputFile.exists()) {
            out.write("NOPE\n".getBytes());
            return;
        }
        
        FileInputStream fileIn = new FileInputStream(outputFile);
        byte[] buffer = new byte[1024];
        int bytesRead;

        // Envoyer les données du fichier
        while ((bytesRead = fileIn.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        
        out.write("END\n".getBytes()); // Signaler la fin du transfert du fichier
        fileIn.close();

        System.out.println("Fichier envoyé : " + outputFile.getAbsolutePath());
    }
}
