import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private static String DOWNLOAD_DIRECTORY = "recues";

    public static void main(String[] args) {
        try {
            // Créer une connexion au serveur
            Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Créer une interface Swing pour permettre à l'utilisateur de choisir un fichier
            JFrame frame = new JFrame("Client - Choisir un fichier");
            frame.setSize(400, 200);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            JButton chooseFileButton = new JButton("Choisir un fichier à envoyer");
            JButton getFileButton = new JButton("Choisir le fichier à récupérer");
            frame.add(chooseFileButton, BorderLayout.NORTH);
            frame.add(getFileButton, BorderLayout.SOUTH);

            // Afficher l'interface
            frame.setVisible(true);

            // Action pour l'envoi de fichier
            chooseFileButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    JFileChooser fileChooser = new JFileChooser();
                    int returnValue = fileChooser.showOpenDialog(frame);
                    if (returnValue == JFileChooser.APPROVE_OPTION) {
                        File file = fileChooser.getSelectedFile();
                        // Utilisation de SwingWorker pour envoyer le fichier dans un thread séparé
                        new SwingWorker<Void, Void>() {
                            @Override
                            protected Void doInBackground() throws Exception {
                                // Envoyer le fichier au serveur
                                try {
                                    out.write("SEND\n".getBytes());  // Ajout de la commande SEND
                                    out.write((file.getName() + "\n").getBytes());

                                    byte[] buffer = new byte[1024];
                                    FileInputStream fileIn = new FileInputStream(file);
                                    int bytesRead;
                                    while ((bytesRead = fileIn.read(buffer)) != -1) {
                                        out.write(buffer, 0, bytesRead);
                                    }
                                    out.write("END\n".getBytes());
                                    fileIn.close();

                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                                return null;
                            }

                            @Override
                            protected void done() {
                                JOptionPane.showMessageDialog(frame, "Fichier envoyé avec succès.");
                            }
                        }.execute();
                    }
                }
            });

            // Action pour récupérer un fichier depuis le serveur
            getFileButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Utilisation de SwingWorker pour récupérer le fichier dans un thread séparé
                    new SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            try {
                                out.write("GET\n".getBytes());

                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                StringBuilder serverResponse = new StringBuilder();
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    serverResponse.append(new String(buffer, 0, bytesRead));
                                    if (serverResponse.toString().contains("FIN")) {
                                        break;
                                    }
                                }

                                System.out.println("Réponse du serveur : \n" + serverResponse.toString());
                                String[] fileNames = serverResponse.toString().split("\n");
                                List<String> fileList = new ArrayList<>();
                                for (String fileName : fileNames) {
                                    if (!fileName.trim().isEmpty() && !fileName.equals("FIN_HISTORIQUE")) {
                                        fileList.add(fileName);
                                    }
                                }
                                if (fileList.isEmpty()) {
                                    JOptionPane.showMessageDialog(frame, "Aucun fichier disponible sur le serveur.");
                                    return null;
                                }

                                JList<String> fileJList = new JList<>(fileList.toArray(new String[0]));
                                JScrollPane scrollPane = new JScrollPane(fileJList);
                                JOptionPane.showMessageDialog(frame, scrollPane, "Sélectionnez un fichier", JOptionPane.PLAIN_MESSAGE);

                                // Obtenir le fichier sélectionné
                                String chosenFile = fileJList.getSelectedValue();

                                if (chosenFile != null && !chosenFile.isEmpty()) {
                                    out.write((chosenFile + "\n").getBytes());
                                    System.out.println("Fichier choisi pour téléchargement : " + chosenFile);
                                    String[]parts=chosenFile.split("\\.");
                                    chosenFile=parts[0]+"."+parts[1];
                                    System.out.println(chosenFile);

                                    File saveFile = new File(DOWNLOAD_DIRECTORY, chosenFile);
                                    FileOutputStream fileOut = new FileOutputStream(saveFile);

                                    // Recevoir le fichier du serveur
                                    while ((bytesRead = in.read(buffer)) != -1) {
                                        fileOut.write(buffer, 0, bytesRead);
                                        if (new String(buffer, 0, bytesRead).contains("END")) {
                                            break;
                                        }
                                    }
                                    fileOut.close();
                                    System.out.println("Fichier téléchargé avec succès : " + chosenFile);
                                    JOptionPane.showMessageDialog(frame, "Le fichier a été récupéré avec succès.");
                                } else {
                                    JOptionPane.showMessageDialog(frame, "Aucun fichier sélectionné.");
                                }

                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                            return null;
                        }

                        @Override
                        protected void done() {
                            // Lorsque l'opération est terminée
                            JOptionPane.showMessageDialog(frame, "Opération terminée.");
                        }
                    }.execute();
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
