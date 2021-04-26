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
    int initTimeOutInMilli = 600 * 1000; // in ms
    final int maxDatagramPacketLength = 1518; // in byte
    final int localPort;
    final InetAddress remoteIp;
    final int remotePort;
    int lastACKExpected = -1; //use to indicate the start of connection closing 
    long initTime;

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
            packetManager.output(synPkt, "snd");
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
                packetManager.getStatistics().incrementIncChecksum(1);
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
            packetManager.output(synAckPkt, "rcv");
            
            // calculate timeout
            timeOut.update(synAckPkt);

            // reply with ACK
            Packet ackPkt = packetManager.makeACKPacket();
            //send ACK as udp
            DatagramPacket udpAck = toUDP(ackPkt, remoteIp, remotePort);
            udpSocket.send(udpAck);
            packetManager.output(ackPkt, "snd");

            // return false if timeout
        } catch (SocketTimeoutException ste) {
            // retransmit if timeout (init timeout = 5
           
            System.err.println("establish connection timeout: " + ste);
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
        return true;
    }

    /*
    This function signifies the receiver to close the connection and waits for response 
    */
    public boolean activeClose() throws DebugException{
        //send FIN
        Packet f = packetManager.makeFINPacket();
        try{
        DatagramPacket udpFin = toUDP(f, remoteIp, remotePort);
        udpSocket.send(udpFin);
        packetManager.output(f, "snd");
        // wait to receive ACK
        udpSocket.setSoTimeout( (int) timeOut.getTimeout() / 1000000);
        byte[] r = new byte[maxDatagramPacketLength]; // pkt buffer for reverse direction
        DatagramPacket dgR = new DatagramPacket(r, r.length); // datagram of r
        udpSocket.receive(dgR);
        
        //check valid ACK: ACK value, checksum, flag 
        Packet ackPkt = Packet.deserialize(r);
        if( !ackPkt.verifyChecksum()){ 
            packetManager.getStatistics().incrementIncChecksum(1);
            return false;}
        if( !Packet.checkACK(ackPkt) || Packet.checkFIN( ackPkt) || Packet.checkSYN(ackPkt)){return false; }
        if(ackPkt.getACK() != packetManager.getLocalSequenceNumber()+1){ return false; }
        packetManager.output(ackPkt, "rcv");
       
        //wait for FIN
        r = new byte[maxDatagramPacketLength];
        udpSocket.receive(dgR); 
        //check valid ACK: ACK value, checksum, flag 
        Packet f2 = Packet.deserialize(r);
        if( !f2.verifyChecksum()){ 
            packetManager.getStatistics().incrementIncChecksum(1);
            return false;}
        if( Packet.checkACK(f2) || !Packet.checkFIN( f2) || Packet.checkSYN(f2)){return false; }
        packetManager.output(f2, "rcv");
        int finACK = f2.getByteSeqNum(); 

        //reply ACK
        Packet a2 = packetManager.makeACKPacket();
        //assert a2.getACK() == finACK+1 : "sender reply receiver's FIN with incorrect ACK"; 
        if( a2.getACK() == finACK+1){
            throw new DebugException();
        }

        DatagramPacket udpA2 = toUDP(a2,remoteIp, remotePort );
        udpSocket.send( udpA2);
        packetManager.output(a2, "snd");

        
            //wait for FIN
            r = new byte[maxDatagramPacketLength];
            udpSocket.receive(dgR); 
            //check valid ACK: ACK value, checksum, flag 
            Packet f2 = Packet.deserialize(r);
            if( !f2.verifyChecksum()){ 
                packetManager.getStatistics().incrementIncChecksum(1);
                return false;}
            if( Packet.checkACK(f2) || !Packet.checkFIN( f2) || Packet.checkSYN(f2)){return false; }
            packetManager.output(f2, "rcv");
            int finACK = f2.getByteSeqNum(); 

            //reply ACK
            Packet a2 = packetManager.makeACKPacket();
            assert a2.getACK() == finACK+1 : "sender reply receiver's FIN with incorrect ACK"; 

            DatagramPacket udpA2 = toUDP(a2,remoteIp, remotePort );
            udpSocket.send( udpA2);
            packetManager.output(a2, "snd");
        }
        catch(IOException ioe){
            System.err.println("sender close fails: " + ioe);
            return false;
        }
        //close
        udpSocket.close(); 
        return true; 
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

    /** T2: Find NEW data in buffer and add them to PacketManager, send to receiver from packet manager in this thread  */
    private class NewPacketSender implements Runnable {
        private InetAddress remoteIp;
        private int remotePort;

        public NewPacketSender(InetAddress remoteIP, int remotePort) {
            this.remoteIp = remoteIP;
            this.remotePort = remotePort;

        }

        public void run() {
            try {
                Packet lastPkt = null;

                while (sendBuffer.isFileToBufferFinished() == false) {
                    bufferToPacket();
                    lastPkt = packetManager.trySendNewData(udpSocket, remotePort, remoteIp);
                    packetManager.getStatistics().incrementPacketCount(1);
                }

                while (sendBuffer.getAvailableDataSize() > 0) {
                    bufferToPacket();
                    lastPkt = packetManager.trySendNewData(udpSocket, remotePort, remoteIp);
                    packetManager.getStatistics().incrementPacketCount(1);
                }
            
                // All buffered data has been stored as Packet in PacketManager. Set flag.
                packetManager.setAllPacketsEnqueued();
                lastACKExpected = lastPkt.getByteSeqNum() + lastPkt.getDataLength() +1 ;

                //when all data are sent, we can send FIN when the lastACKExpected received 

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
        public void run(){
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
                        byte[] bb = new byte[ACKpktSerial.getLength()];
                        System.arraycopy(b, 0, bb, 0, ACKpktSerial.getLength());
                        Packet ACKpkt = Packet.deserialize(bb);
                        packetManager.output(ACKpkt, "rcv");
                        int ACKnum = ACKpkt.ACK;
                       

                        if (ACKnum == lastACKnum){ // must be a duplicate ACK
                            PacketWithInfo pp = null;
                            for (PacketWithInfo p : packetManager.getQueue()) {
                                if(p.packet.byteSeqNum == ACKnum){
                                    pp = p;
                                    p.ACKcount++;
                                    packetManager.getStatistics().incrementDupACKCount();
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
                                    if (packetManager.getQueue().remove(p) != true) {
                                        throw new RuntimeException("assert problem");
                                    }
                                    packetManager.decrementInTransitPacket();
                                }
                                else if (p.packet.byteSeqNum == ACKnum){
                                    packetManager.getStatistics().incrementDupACKCount();
                                    if((++p.ACKcount) == 4) {
                                        dupACKResend(p);
                                        
                                    }
                                }
                            }
                        }
                        else { // May be a new ACK or a dup ACK, ACK number is wrapped
                            for(PacketWithInfo p : packetManager.getQueue()){
                                if (p.packet.byteSeqNum < ACKnum || p.packet.byteSeqNum >= lastACKnum){
                                    //assert packetManager.getQueue().remove(p) == true;
                                    if(!packetManager.getQueue().remove(p)){
                                        throw new RuntimeException("assert problem");
                                    }
                                    packetManager.decrementInTransitPacket();
                                }
                                else if (p.packet.byteSeqNum == ACKnum){
                                    packetManager.getStatistics().incrementDupACKCount();
                                    if((++p.ACKcount) == 4) {
                                        dupACKResend(p);
                                    }                                
                                }
                            }
                        }

                        lastACKnum = ACKnum;
                    }
                }

                
                if( lastACKnum != lastACKExpected){
                    System.out.println(" inconsistent ACK before close");
                    System.exit(1);
                }

                while( !activeClose()){} //keep closing until return true

               
            } catch (Exception e) {
                System.err.println("T3-ACK_Receiver: An error occured:");
                System.err.println(e);
            }
        }

        private void dupACKResend(PacketWithInfo p) throws IOException, DebugException {
            // Make resend packet with info
            PacketWithInfo resndPWI = p.getResendPacketWithInfo(packetManager.getRemoteSequenceNumber());

            // Update PacketManager (remove old, add new)
            //assert packetManager.getQueue().remove(p) == true;
            if( ! packetManager.getQueue().remove(p)){
                throw new DebugException();
            }
            packetManager.getQueue().add(resndPWI);

            // Resend packet
            packetManager.dupACKFastRetransmit(resndPWI, udpSocket, remotePort, remoteIp);

            //update statistics
            packetManager.getStatistics().incrementRetransCount();
        }
    }

    /** T4: check packets in packet manager to see if any timeout stop checking and sleep when seeing the fisrt unexpired packet retransmit expired */
    private class timeoutChecker implements Runnable{

        public void run() {
            try {
                packetManager.checkExpire(  udpSocket,  remotePort,  remoteIp);
            }
            catch (IOException e){
                System.err.println("T4-timeoutChecker: IOException:");
                System.err.println(e);
            }
            catch (DebugException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
            // catch (NoSuchElementException e){
            //     System.err.println("T4-timeoutChecker: NoSuchElementException:");
            //     System.err.println(e);
            //     throw e;
            // }
            return; 
        }
    }



    /****************************************************************************/
    /****************** Constructor, main work, and statistics ******************/
    /****************************************************************************/

    /* java TCPend -p <port> -s <remote IP> -a <remote port> –f <file name> -m <mtu> -c <sws> */
    // TCPSend Constructor
    public TCPSend(int localPort, InetAddress remoteIp, int remotePort, String fileName, int mtu, int windowSize, long initTime) throws SocketException, BufferSizeException {
        this.localPort = localPort;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        bufferSize = (int)(mtu * windowSize * 3 / 2);
        sendBuffer = new SenderBuffer(bufferSize, mtu, windowSize);
        packetManager = new PacketManager(windowSize, new PacketWithInfoComparator(), initTime);
        timeOut = new Timeout(0.875, 0.75, initTimeOutInMilli * 1000000);
        filePath = Paths.get(fileName);
        this.mtu = mtu;
        this.initTime = initTime; //time in ms 
    }

    // Main running program
    public void work() throws InterruptedException {
        try {
            udpSocket = new DatagramSocket(localPort);
            // try to handshake until connection established
            while (!estConnection(remoteIp, remotePort)) {
            }
            Thread T0_fileToBuffer = new Thread(new FileToBuffer());
            Thread T1_newPacketSender = new Thread(new NewPacketSender(remoteIp, remotePort));
            Thread T2_ACKReceiver = new Thread(new ACKReceiver());
            Thread T3_timeoutChecker = new Thread( new timeoutChecker());

            T0_fileToBuffer.start();
            T1_newPacketSender.start();
            T2_ACKReceiver.start();
            T3_timeoutChecker.start();

            T0_fileToBuffer.join();
            T1_newPacketSender.join();
            T2_ACKReceiver.join();
            T3_timeoutChecker.join();

    

        } catch (SocketException e) {
            System.err.println("SocketException: " + e);
            System.exit(1);
        }
    }

    // Get statistics
    public String getStatisticsString() {
        
        Statistics statistics = this.packetManager.getStatistics();
        return statistics.senderStat();
    }

    
}
