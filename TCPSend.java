import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.util.NoSuchElementException;
import java.io.*;

import Packet.*;
import Buffer.*;
import Exceptions.*;
import Statistics.Statistics;

public class TCPSend {
    int bufferSize; // will be determined in construction: 1.5*sws*mtu
    SenderBuffer sendBuffer;
    PacketManager packetManager;
    Path filePath;
    int mtu;
    DatagramSocket udpSocket;
    Timeout timeOut;
    int initTimeOutInMilli = 5 * 1000; // in ms
    final int maxDatagramPacketLength = 1518; // in byte
    final int localPort;
    final InetAddress remoteIp;
    final int remotePort;

    /*********************************************************************/
    /********************** Connections and Packets **********************/
    /*********************************************************************/

    /** Establish UDP connection and perform 3-way handshake. Return if the 3-way handshake is successful. */
    public boolean estConnection(InetAddress remoteIp, int remotePort) {
        // datagram connect
        try {
            udpSocket.connect(remoteIp, remotePort);

            Packet synPkt = new Packet(packetManager.getLocalSequenceNumber()); // localSeqNum is 0
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
            packetManager.setLocalSequenceNumber(1); // local seq increase after SYN, when send data, use buffer index as seqNum

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
            packetManager.setRemoteSequenceNumber(synAckPkt.getByteSeqNum());
            
            // calculate timeout
            timeOut.update(synAckPkt);

            // reply with ACK
            Packet ackPkt = new Packet(packetManager.getLocalSequenceNumber());
            //same seqNum start from 1 after SYN, but not increase here with ACK sent 
            Packet.setFlag(ackPkt, false, false, true);
            ackPkt.setACK(packetManager.getRemoteSequenceNumber() +1 );
            Packet.calculateAndSetChecksum(ackPkt);
            //send ACK as udp
            DatagramPacket udpAck = toUDP(ackPkt, remoteIp, remotePort);
            udpSocket.send(udpAck);

            // return false if timeout
        } catch (SocketTimeoutException ste) {
            // retransmit if timeout (init timeout = 5
           
            System.err.println("establish connection timeout: " + ste);
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
                while (sendBuffer.isFileToBufferFinished() == false) {
                    bufferToPacket();
                    packetManager.trySendNewData(udpSocket, remotePort, remoteIp);
                }

                while (sendBuffer.getAvailableDataSize() > 0) {
                    bufferToPacket();
                    packetManager.trySendNewData(udpSocket, remotePort, remoteIp);
                }
            
                // All buffered data has been stored as Packet in PacketManager. Set flag.
                packetManager.setAllPacketsEnqueued();

            } catch (Exception e) {
                System.out.println(e);
            }
        }

        private void bufferToPacket() throws Exception {
            int seqNum = packetManager.getLocalSequenceNumber(); // use packet manager's local sequence number instead of buffer's index, since our buffer is unlikely to be INT_MAX in size.
            byte[] data = sendBuffer.getDataToSend(mtu); // sendBuffer.lastByteSent updated
            // make packet with the data
            Packet tcpPkt = makeDataPacket(seqNum, data);
            
            // insert Pkt to packet manager
            PacketWithInfo infoPkt = new PacketWithInfo(tcpPkt);
            infoPkt.timeOut = timeOut.getTimeout();
            packetManager.getQueue().add(infoPkt);
            
            // increment local sequence number
            if (data != null) {
                packetManager.increaseLocalSequenceNumber(data.length);
            }
        }

        private Packet makeDataPacket(int seqNum, byte[] data) {
            Packet newPkt = new Packet(seqNum);
            Packet.setDataAndLength(newPkt, data);
            Packet.setFlag(newPkt, false, false, true);
            newPkt.setACK(packetManager.getRemoteSequenceNumber() + 1);
            Packet.calculateAndSetChecksum(newPkt);

            return newPkt;
        }
    }

    /** T3: Receiving ACK, update PacketManager. Use RTT to update timeout. If Triple DupACK, send packet to socket  */
    private class ACKReceiver implements Runnable {
        public void run() {
            try{
                byte[] b = new byte[maxDatagramPacketLength];
                DatagramPacket ACKpktSerial = new DatagramPacket(b, b.length);
                
                /** 
                 * If new ACK num < lastACKnum, we know that the ACK num/ByteSeqNum wraps, so
                 * we need to remove packets accordingly.
                 */
                int lastACKnum = 0; // If the new received ACK# is smaller than `lastACKnum`, it may imply a sequence number wrap has occured.
                
                while(!packetManager.isAllPacketsEnqueued() || !packetManager.getQueue().isEmpty()){
                    if(packetManager.getQueue().isEmpty()){
                        // No packet waiting for an ACK. Yield.
                        Thread.yield();
                    }
                    else{
                        ACKpktSerial.setLength(b.length);
                        udpSocket.receive(ACKpktSerial);
                        Packet ACKpkt = Packet.deserialize(ACKpktSerial.getData());
                        int ACKnum = ACKpkt.ACK;
                        if (ACKnum == lastACKnum){ // must be a duplicate ACK
                            PacketWithInfo pp = null;
                            for (PacketWithInfo p : packetManager.getQueue()) {
                                if(p.packet.byteSeqNum == ACKnum){
                                    pp = p;
                                    p.ACKcount++;
                                    break;
                                }
                            }
                            if (pp == null){
                                throw new DupACKPacketNotExistException();
                            }
                            
                            // Check triple dup ACK?
                            if (pp.ACKcount == 4) { // triple dup ACK
                                dupACKResend(pp);
                            }
                        }
                        else if (ACKnum > lastACKnum) { // May be a new ACK or a dup ACK, ACK number is not wrapped
                            for(PacketWithInfo p : packetManager.getQueue()){
                                if (p.packet.byteSeqNum < ACKnum){
                                    // If seqNum+length==ACKnum -> update RTT, timeout
                                    int ACKnumMatch = p.packet.byteSeqNum + p.packet.getDataLength();
                                    if (ACKnumMatch < 0) { ACKnumMatch += Integer.MAX_VALUE; ACKnumMatch += 1; } // wrap
                                    if (ACKnumMatch == ACKnum) {timeOut.update(p.packet);}

                                    // remove received packets from queue
                                    assert packetManager.getQueue().remove(p) == true;
                                    packetManager.decrementInTransitPacket();
                                }
                                else if (p.packet.byteSeqNum == ACKnum){
                                    if((++p.ACKcount) == 4) {
                                        dupACKResend(p);
                                    }
                                }
                            }
                        }
                        else { // May be a new ACK or a dup ACK, ACK number is wrapped
                            for(PacketWithInfo p : packetManager.getQueue()){
                                if (p.packet.byteSeqNum < ACKnum || p.packet.byteSeqNum >= lastACKnum){
                                    assert packetManager.getQueue().remove(p) == true;
                                    packetManager.decrementInTransitPacket();
                                }
                                else if (p.packet.byteSeqNum == ACKnum){
                                    if((++p.ACKcount) == 4) {
                                        dupACKResend(p);
                                    }                                
                                }
                            }
                        }

                        lastACKnum = ACKnum;
                    }
                }
            } catch (Exception e) {
                System.err.println("T3-ACK_Receiver: An error occured:");
                System.err.println(e);
            }
        }

        private void dupACKResend(PacketWithInfo p) throws IOException {
            // Make resend packet with info
            PacketWithInfo resndPWI = p.getResendPacketWithInfo(packetManager.getRemoteSequenceNumber());

            // Update PacketManager (remove old, add new)
            assert packetManager.getQueue().remove(p) == true;
            packetManager.getQueue().add(resndPWI);

            // Resend packet
            packetManager.dupACKFastRetransmit(resndPWI, udpSocket, remotePort, remoteIp);
        }
    }

    /** T4: check packets in packet manager to see if any timeout stop checking and sleep when seeing the fisrt unexpired packet retransmit expired */
    private class timeoutChecker implements Runnable{

        public void run(){
            try {
                packetManager.checkExpire(  udpSocket,  remotePort,  remoteIp);
            }
            catch (IOException e){
                System.err.println("T4-timeoutChecker: IOException:");
                System.err.println(e);
            }
            catch (NoSuchElementException e){
                System.err.println("T4-timeoutChecker: NoSuchElementException:");
                System.err.println(e);
            }
            return; 
        }
    }



    /****************************************************************************/
    /****************** Constructor, main work, and statistics ******************/
    /****************************************************************************/

    /* java TCPend -p <port> -s <remote IP> -a <remote port> –f <file name> -m <mtu> -c <sws> */
    // TCPSend Constructor
    public TCPSend(int localPort, InetAddress remoteIp, int remotePort, String fileName, int mtu, int windowSize) throws SocketException, BufferSizeException {
        this.localPort = localPort;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        bufferSize = (int)(mtu * windowSize * 3 / 2);
        sendBuffer = new SenderBuffer(bufferSize, mtu, windowSize);
        packetManager = new PacketManager(windowSize, new PacketWithInfoComparator());
        timeOut = new Timeout(0.875, 0.75, initTimeOutInMilli * 1000000);
        filePath = Paths.get(fileName);
        this.mtu = mtu;
    }

    // Main running program
    public void work() throws InterruptedException {
        try {
            udpSocket = new DatagramSocket(localPort);
            // try to handshake until connection established
            while (!estConnection(remoteIp, remotePort)) {
            }
            Thread T1_fileToBuffer = new Thread(new FileToBuffer());
            Thread T2_newPacketSender = new Thread(new NewPacketSender(remoteIp, remotePort));
            Thread T3_ACKReceiver = new Thread(new ACKReceiver());
            Thread T4_timeoutChecker = new Thread( new timeoutChecker());

            T1_fileToBuffer.start();
            T2_newPacketSender.start();
            T3_ACKReceiver.start();
            T4_timeoutChecker.start();

            T1_fileToBuffer.join();
            T2_newPacketSender.join();
            T3_ACKReceiver.join();
            T4_timeoutChecker.join();

            // TODO: Close connection

        } catch (SocketException e) {
            System.err.println("SocketException: " + e);
            System.exit(1);
        }
    }

    // Get statistics
    public String getStatisticsString() {
        // TODO: Statistics
        Statistics statistics = this.packetManager.getStatistics();
        return "Statistics not ready!";
    }
}
