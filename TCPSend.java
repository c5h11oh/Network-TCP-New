import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
    int mtu;
    DatagramSocket udpSocket;
    Timeout timeOut;
    int initTimeOutInMilli = 5 * 1000; // in ms
    final int maxDatagramPacketLength = 1518; // in byte

    /*********************************************************************/
    /********************** Connections and Packets **********************/
    /*********************************************************************/

    /** Establish UDP connection and perform 3-way handshake. Return if the 3-way handshake is successful. */
    public boolean estConnection(InetAddress remoteIp, int remotePort) {
        // datagram connect
        try {
            udpSocket.connect(remoteIp, remotePort);

            Packet synPkt = new Packet(packetManager.getLocalSequenceNumberCounter(), System.nanoTime());
            // ^^ Note: The timeStamp should be in nanoseconds (specified in 2.1.1)
            Packet.setFlag(synPkt, true, false, false);
            Packet.calculateAndSetChecksum(synPkt);
            // send SYN
            DatagramPacket udpSyn = toUDP(synPkt, remoteIp, remotePort);
            udpSocket.send(udpSyn);
            // wait to receive SYN+ ACK
            udpSocket.setSoTimeout(initTimeOutInMilli);
            byte[] r = new byte[256]; // pkt buffer for reverse direction
            DatagramPacket dgR = new DatagramPacket(r, r.length); // datagram of r
            udpSocket.receive(dgR);

            //after we receive replied ACK
            packetManager.setLocalSequenceNumberCounter(1); //local seq increase after SYN, when send data, use buffer index as seqNum

            // checksum
            Packet synAckPkt = Packet.deserialize(r);
            if (!synAckPkt.verifyChecksum()) {
                return false;
            }
            // check flag
            if (!(Packet.checkSYN(synAckPkt) && Packet.checkACK(synAckPkt))) {
                return false;
            }
            //check correct ACK
            if( synAckPkt.getByteSeqNum() != 0){
                return false;
            }
            // update remote seqNum
            packetManager.setRemoteSequenceNumberCounter(synAckPkt.getByteSeqNum());
            
            // calculate timeout
            timeOut.update(synAckPkt);

            // reply with ACK
            Packet ackPkt = new Packet(packetManager.getLocalSequenceNumberCounter(), System.nanoTime());
            //same seqNum start from 1 after SYN, but not increase here with ACK sent 
            Packet.setFlag(ackPkt, false, false, true);
            ackPkt.setACK(packetManager.getRemoteSequenceNumberCounter() +1 );
            Packet.calculateAndSetChecksum(ackPkt);
            //send ACK as udp
            DatagramPacket udpAck = toUDP(ackPkt, remoteIp, remotePort);
            udpSocket.send(udpAck);

            // return false if timeout
        } catch (SocketTimeoutException ste) {
            // retransmit if timeout (init timeout = 5
           
            System.out.println("establish connection timeout: " + ste);
            return false;
        } catch (Exception e) {
            // IllegalArgumentException from connect() - if the IP address is null, or the
            // port is out of range
            // other IOException

        }
        return false;
    }

    /**
     * encapsulate a tcp pkt to udp pkt
     */
    public DatagramPacket toUDP(Packet pkt, InetAddress remoteIp, int remotePort) {
        byte[] data = Packet.serialize(pkt);
        DatagramPacket udpPkt = new DatagramPacket(data, data.length, remoteIp, remotePort);
        return udpPkt;
    }
    
    
    /*********************************************************************/
    /******************** Runnable objects (Threads) *********************/
    /*********************************************************************/

    /** T1: Application that puts file data into send buffer */
    private class FileToBuffer implements Runnable {
        // private SenderBuffer sendBuffer;
        // private Path filePath;
        // public FileToBuffer(SenderBuffer sendBuffer, Path filePath){
        // this.sendBuffer = sendBuffer;
        // this.filePath = filePath;
        // }
        public void run() {
            try (InputStream in = Files.newInputStream(filePath, StandardOpenOption.READ)) {
                BufferedInputStream bin = new BufferedInputStream(in);
                int bufFreeSpace, writeLength;
                while ((writeLength = bin.available()) > 0) {
                    bufFreeSpace = sendBuffer.waitForFreeSpace();

                    writeLength = Math.min(writeLength, bufFreeSpace);
                    byte[] data = new byte[writeLength];
                    bin.read(data, 0, data.length);
                    sendBuffer.put(data);
                }
                sendBuffer.setFileToBufferFinished();
                bin.close();
            } catch (NullPointerException e) {
                System.err.println("TCPSend: NullPointerException: " + e);
                System.exit(1);
            } catch (FileNotFoundException e) {
                System.err.println("FileNotFoundException:" + e.toString());
                System.exit(1);
            } catch (BufferInsufficientSpaceException e) {
                System.err.println("BufferInsufficientSpaceException:" + e.toString());
                System.exit(1);
            } catch (IOException e) {
                System.err.println(e);
            }

        }

    }

    /** T2: Find NEW data in buffer and add them to PacketManager */
    private class NewPacketSender implements Runnable {
        private InetAddress remoteIp;
        private int remotePort;

        public NewPacketSender(InetAddress remoteIP, int remotePort) {
            this.remoteIp = remoteIP;
            this.remotePort = remotePort;

        }

        public void run() {
            try {
                // check if 1 mtu data available or have no unAcked data in sendBuffer
                int seqNum;
                if (sendBuffer.getAvailableDataSize() >= mtu
                        || (sendBuffer.getLastByteACK() == sendBuffer.getLastByteSent())) {

                    seqNum = sendBuffer.getLastByteSent() + 1;
                    byte[] data = sendBuffer.getDataToSend(mtu); // lastByteSent updated

                    // make tcp packet with the data
                    Packet tcpPkt = makeDataPacket(seqNum, data);
                    // make and send udp pkt

                    DatagramPacket udpPkt = toUDP(tcpPkt, remoteIp, remotePort);
                    udpSocket.send(udpPkt);

                    // insert to packet manager
                    PacketWithInfo infoPkt = new PacketWithInfo(tcpPkt, tcpPkt.getTimeStamp());
                    packetManager.getQueue().add(infoPkt);
                    packetManager.setLocalSequenceNumberCounter( packetManager.getLocalSequenceNumberCounter()+ data.length);

                } else {
                    // wait for more data
                    // TODO: not sure if this is necessary, will encounter situatoin where the
                    // sender buffer has less than mtu bytes unsent ?
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }

                }
                // should the above code be in a loop?
                
                // ...

                // All buffered data has been stored as Packet in PacketManager. Set flag.
                packetManager.setAllPacketsEnqueued();

            } catch (Exception e) {
                // TODO: different actions for different exceptions
                // exceptions from datagram socket
                // self defined exceptions
                System.out.println(e);

            }

        }

        public Packet makeDataPacket(int seqNum, byte[] data) {
            Packet newPkt = new Packet(seqNum, System.nanoTime());
            
            Packet.setDataAndLength(newPkt, data);
            Packet.setFlag(newPkt, false, false, true);
            // TODO: verify if this variable is the correct ACK << think should be sequence num + 1
            newPkt.setACK(packetManager.getRemoteSequenceNumberCounter() + 1);
            Packet.calculateAndSetChecksum(newPkt);

            return newPkt;
        }

    }

    // T3: Receiving ACK, update PacketManager. Use RTT to update timeout. If Triple DupACK, send packet to socket
    private class ACKReceiver implements Runnable {
        public void run() {
            try{
                byte[] b = new byte[maxDatagramPacketLength];
                DatagramPacket p = new DatagramPacket(b, b.length);
                while(!packetManager.isAllPacketsEnqueued()){
                    if(packetManager.getQueue().isEmpty()){
                        // No packet waiting for an ACK. Yield.
                        Thread.yield();
                    }
                    else{
                        p.setLength(b.length);
                        udpSocket.receive(p);
                        Packet ACKpkt = Packet.deserialize(p.getData());
                    }
                }
            } catch (Exception e) {}
        }
    }

    // T4 check packets in packet manager to see if any timeout 
    // stop checking and sleep when seeing the fisrt unexpired packet 
    //retransmit expired

    private class timeOutChecker implements Runnable{

        public void run(){

            return; 
        }
    }



    /****************************************************************************/
    /****************** Constructor, main work, and statistics ******************/
    /****************************************************************************/

    // TCPSend Constructor
    public TCPSend(String fileName, int mtu, int windowSize) throws SocketException, BufferSizeException {
        sendBuffer = new SenderBuffer(bufferSize, mtu, windowSize);
        packetManager = new PacketManager(windowSize);
        timeOut = new Timeout(0.875, 0.75, initTimeOutInMilli * 1000000);
        filePath = Paths.get(fileName);
        this.mtu = mtu;
    }

    // Main running program
    public void work(int localPort, InetAddress remoteIp, int remotePort) throws InterruptedException {
        try {
            udpSocket = new DatagramSocket(localPort);
            // try to handshake until connection established
            while (!estConnection(remoteIp, remotePort)) {
            }
            Thread T1_fileToBuffer = new Thread(new FileToBuffer());
            Thread T2_newPacketSender = new Thread(new NewPacketSender(remoteIp, remotePort));

            T1_fileToBuffer.start();
            T2_newPacketSender.start();

            T1_fileToBuffer.join();
            T2_newPacketSender.join();
        } catch (SocketException e) {
            System.err.println("SocketException: " + e);
            System.exit(1);
        }
    }

    // Get statistics
    public String getStatisticsString() {
        Statistics statistics = this.packetManager.getStatistics();
        return "Statistics not ready!";
    }

    

}
