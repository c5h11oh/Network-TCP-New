import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.*;

import Packet.*;
import Buffer.*;
import Exceptions.*;
import Statistics.Statistics;
import java.io.*;
import java.nio.file.*;

/* java TCPend -p <port> -s <remote IP> -a <remote port> –f <file name> -m <mtu> -c <sws> */

public class TCPSend {
    int bufferSize = 64 * 1024; 
    SenderBuffer sendBuffer;
    PacketManager packetManager;
    Path filePath;
    
    /****************** Runnable objects (Thread works) ******************/
    // T1: Application that puts file data into send buffer
    private class FileToBuffer implements Runnable {
        // private SenderBuffer sendBuffer;
        // private Path filePath;
        // public FileToBuffer(SenderBuffer sendBuffer, Path filePath){
        //     this.sendBuffer = sendBuffer;
        //     this.filePath = filePath;
        // }
        public void run() {
            try (InputStream in = Files.newInputStream(filePath, StandardOpenOption.READ)) {
                BufferedInputStream bin = new BufferedInputStream(in);
                int bufFreeSpace, writeLength;
                while((writeLength = bin.available()) > 0){
                    bufFreeSpace = sendBuffer.waitForFreeSpace();
    
                    writeLength = Math.min(writeLength, bufFreeSpace);
                    byte[] data = new byte[writeLength];
                    bin.read(data, 0, data.length);
                    sendBuffer.put(data);
                }
                sendBuffer.setFileToBufferFinished();
                bin.close();
            }
            catch (NullPointerException e) {
                System.err.println("TCPSend: NullPointerException: " + e);
                System.exit(1);
            }
            catch (FileNotFoundException e){
                System.err.println("FileNotFoundException:" + e.toString());
                System.exit(1);
            }
            catch (BufferInsufficientSpaceException e){
                System.err.println("BufferInsufficientSpaceException:" + e.toString());
                System.exit(1);
            }
            catch (IOException e){
                System.err.println(e);
            }
            
        }
        
    }

    // T2: Find NEW data in buffer and add them to PacketManager
    private static class NewPacketSender implements Runnable {
        public void run() {

        }
    }
    
    // TCPSend Constructor
    public TCPSend(String fileName, int mtu, int windowSize) throws SocketException, BufferSizeException {
        sendBuffer = new SenderBuffer(bufferSize, mtu, windowSize);
        packetManager = new PacketManager(windowSize);
        filePath = Paths.get(fileName);
    }

    // Main running program
    public void work(int localPort, InetAddress remoteIp, int remotePort) throws InterruptedException {
        try (DatagramSocket udpSocket = new DatagramSocket(localPort)) {
            Thread T1_fileToBuffer = new Thread(new FileToBuffer());
            Thread T2_newPacketSender = new Thread(new NewPacketSender());
            
            T1_fileToBuffer.start();
            T2_newPacketSender.start();
            
            T1_fileToBuffer.join();
            T2_newPacketSender.join();
        }
        catch (SocketException e) {
            System.err.println("SocketException: " + e);
            System.exit(1);
        }
    }

    public String getStatisticsString(){
        Statistics statistics = this.packetManager.getStatistics();
        return "Statistics not ready!";
    }
}