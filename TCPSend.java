import Packet.*;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.*;

import Buffer.*;
import Exceptions.*;
import Statistics.Statistics;

/* java TCPend -p <port> -s <remote IP> -a <remote port> –f <file name> -m <mtu> -c <sws> */

public class TCPSend {
    private int bufferSize = 134217728; // 128 M

    private SenderBuffer sendBuffer;
    private PacketManager packetManager;
    private Path filePath;
    
    public TCPSend(String fileName, int mtu, int windowSize) throws SocketException, BufferSizeException {
        sendBuffer = new SenderBuffer(bufferSize, windowSize);
        packetManager = new PacketManager(windowSize);
        filePath = Paths.get(fileName);
    }

    public void work(int localPort, InetAddress remoteIp, int remotePort) {
        try (DatagramSocket udpSocket = new DatagramSocket(localPort)) {
            Thread T1_sendFile = new Thread(new Worker.Sender.SendFile(sendBuffer, filePath));
            T1_sendFile.start();
        }
        catch (SocketException e) {
            System.err.println("SocketException: " + e);
        }
    }

    public String getStatisticsString(){
        Statistics statistics = this.packetManager.getStatistics();
        return "Statistics not ready!";
    }
}
