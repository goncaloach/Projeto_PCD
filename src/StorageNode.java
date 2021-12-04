import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Scanner;

public class StorageNode {

    //TODO comunicar com o diretorio (pedir num nodes)
    //TODO implementar syncronized list para byteblockrequest
    //TODO cada byteblock contem 100 bytes
    //TODO Cada um destes processos ligeiros deve contar quantos blocos descarrega, e esta contagem deve
    //ser imprimida na consola, juntamente com a identificação do nó remoto, após a conclusão do
    //descarregamento dos dados.
    //Deve ser implementada uma estrutura de coordenação para combinar os blocos recebidos


    private CloudByte[] cloudBytes = new CloudByte[1000000];
    private BufferedReader inDirectory;
    private PrintWriter outDirectory;
    private ServerSocket storageNodeServerSocket;
    //private List<ByteBlockRequest> requestsQueue = Collections.synchronizedList(new ArrayList<>());


    public StorageNode(String directoryAddressText, int directoryPort, int nodePort, String fileName) {
        try {
            this.storageNodeServerSocket = new ServerSocket(nodePort);
            connectToDirectory(directoryAddressText, directoryPort, nodePort);
        } catch (IOException e) {
            System.err.println("Error while connecting to Directory");
            e.printStackTrace();
        }

        createCloudBytes(fileName);
        new injectErrorsFromConsole().start();

        try {
            startServing();
        } catch (IOException e) {
            System.err.println("Error while connecting to Clients");
            e.printStackTrace();
        }

        System.out.println("You were not supposed to reach here :/");
    }

    public StorageNode(String directoryAddressText, int directoryPort, int nodePort) {
        try {
            this.storageNodeServerSocket = new ServerSocket(nodePort);
            connectToDirectory(directoryAddressText, directoryPort, nodePort);
        } catch (IOException e) {
            System.err.println("Error while connecting to Directory");
            e.printStackTrace();
        }

        getCloudBytesFromStorageNodes();
        new injectErrorsFromConsole().start();

        try {
            startServing();
        } catch (IOException e) {
            System.err.println("Error while connecting to Clients");
            e.printStackTrace();
        }

        System.out.println("You were not supposed to reach here v2 :/");
    }

    //DONE
    private void connectToDirectory(String directoryAddressText, int directoryPort, int nodePort) throws IOException {
        InetAddress directoryAddress = InetAddress.getByName(directoryAddressText);
        Socket directorySocket = new Socket(directoryAddress, directoryPort);

        this.inDirectory = new BufferedReader(new InputStreamReader(directorySocket.getInputStream()));
        this.outDirectory = new PrintWriter(new BufferedWriter(new OutputStreamWriter(directorySocket.getOutputStream())), true);

        InetAddress nodeAddress = InetAddress.getByName(null);
        String msg = "INSC " + nodeAddress + " " + nodePort;
        System.out.println("Sending to Directory: " + msg);
        outDirectory.println(msg);
    }

    //DONE
    private void createCloudBytes(String fileName) {
        try {
            byte[] fileContents = Files.readAllBytes(new File(fileName).toPath());
            for (int i = 0; i < fileContents.length; i++)
                cloudBytes[i] = new CloudByte(fileContents[i]);
            System.out.println("Loaded data from file: " + cloudBytes.length);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error while reading file");
        }
    }

    //DONE
    private class injectErrorsFromConsole extends Thread {

        private boolean checkArguments(String[] strArgs) {
            if (strArgs.length != 2 || !strArgs[0].equals("ERROR")) {
                System.err.println("Wrong Arguments");
                return false;
            }
            try {
                int num = Integer.parseInt(strArgs[1]);
                if (num < 1 || num > 1000000) {
                    System.err.println("Second Argument must be a number between 1 and 1000000");
                    return false;
                }
                return true;
            } catch (Exception e) {
                System.err.println("Second Argument must be a number");
                return false;
            }
        }

        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] strArgs = line.split("\\s+");
                if (checkArguments(strArgs)) {
                    int numByte = Integer.parseInt(strArgs[1]) - 1;
                    cloudBytes[numByte].makeByteCorrupt();
                    System.out.println("Injected Error into byte number " + (numByte + 1) + ": " + cloudBytes[numByte]);
                }
            }
        }

    }

    //TODO
    private class errorCorrector extends Thread{

    }

    //TODO
    private void correctError(){

    }

    //TODO
    private void getCloudBytesFromStorageNodes(){
        ArrayList<NodeInformation> list = getListOfStorageNodes();

    }

    private class NodeInformation{
        private InetAddress address;
        private int port;

        public NodeInformation(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }

        public void setAddress(InetAddress address) {
            this.address = address;
        }

        public int getPort() {
            return port;
        }
    }

    private ArrayList<NodeInformation> getListOfStorageNodes()  {
        ArrayList<NodeInformation> list = new ArrayList<>();
        outDirectory.println("nodes");
        try {
            while (true){
                String line = inDirectory.readLine();
                if (line.equals("end"))
                    break;
                String[] values = line.split("\\s+");
                if(values[1].equals("localhost/127.0.0.1"))     //InetAddress.getByName can't recognize this address
                    values[1]="localhost";
                list.add(new NodeInformation(InetAddress.getByName(values[1]),Integer.parseInt(values[2])));
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error while trying to receive list of StorageNodes");
        }
        return list;
    }

    private void startServing() throws IOException {
        try {
            System.out.println("Awaiting connections...");
            while (true) {
                Socket clientSocket = storageNodeServerSocket.accept();
                new DealWithClient(clientSocket).start();
            }
        } finally {
            //not needed
            storageNodeServerSocket.close();
        }
    }

    private class DealWithClient extends Thread {

        private ObjectInputStream in;
        private ObjectOutputStream out;

        public DealWithClient(Socket s) throws IOException {
            out = new ObjectOutputStream(s.getOutputStream());
            in = new ObjectInputStream(s.getInputStream());
        }

        @Override
        public void run() {
            while (true) {
                try {
                    ByteBlockRequest msgReceived = (ByteBlockRequest) in.readObject();
                    int startIndex = msgReceived.getStartIndex();
                    int length = msgReceived.getLength();
                    CloudByte[] msgResponse = new CloudByte[length];
                    for (int i = startIndex - 1; i < startIndex - 1 + length; i++) {
                        if (!cloudBytes[i].isParityOk()) {
                            System.out.println("Error in byte number " + (i + 1) + ": " + cloudBytes[i]);
                            //TODO corrigir erro
                        }
                    }


                    for (int i = startIndex - 1, j = 0; i < startIndex - 1 + length; i++, j++)
                        msgResponse[j] = cloudBytes[i];

                    out.reset();
                    out.writeObject(msgResponse);
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 4)
            new StorageNode(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), args[3]);
        if (args.length == 3)
            new StorageNode(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
    }

}
