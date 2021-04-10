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
    int initTimeOut = 5*1000; //in ms
    
    
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
    private  class NewPacketSender implements Runnable {
        private InetAddress remoteIp; 
        private int remotePort;
        

        public NewPacketSender( InetAddress remoteIP,int remotePort ){
            this.remoteIp = remoteIP;
            this.remotePort = remotePort;
            
        }

        public void run() {
            try{
                //check if 1 mtu data available or have no unAcked data in sendBuffer
                int seqNum;  
                if(sendBuffer.getAvailableDataSize() >= mtu || (sendBuffer.getLastByteACK() == sendBuffer.getLastByteSent()) ){

                    seqNum = sendBuffer.getLastByteSent() +1; 
                    byte[] data = sendBuffer.getDataToSend(mtu); //lastByteSent updated

                    //make tcp packet with the data 
                    Packet tcpPkt = makeDataPacket( seqNum,  data);
                    //make and send udp pkt 
                    
                    DatagramPacket udpPkt = toUDP(tcpPkt, remoteIp, remotePort);
                    udpSocket.send(udpPkt); 

                    //insert to packet manager 
                    PacketWithInfo infoPkt = new PacketWithInfo(tcpPkt, tcpPkt.getTimeStamp()); 
                    packetManager.add(infoPkt);         

                }else{
                    //wait for more data 
                    //TODO: not sure if this is necessary, will encounter situatoin where the sender buffer has less than mtu bytes unsent ? 
                    try{
                        wait();
                    } catch (InterruptedException e) {}
                
                }

            }catch(Exception e){
                //TODO: different actions for different exceptions 
                //exceptions from datagram socket 
                //self defined exceptions 
                System.out.println(e);
                
            }
            

        }

        public Packet makeDataPacket(int seqNum, byte[] data){
            Packet newPkt = new Packet(seqNum, System.currentTimeMillis()); 
            Packet.setDataAndLength(newPkt, data);
            Packet.setFlag(newPkt,false, false, true);
            //TODO: verify if this variable is the correct ACK 
            newPkt.setACK(packetManager.getRemoteSequenceNumberCounter());
            Packet.calculateAndSetChecksum(newPkt);

            return newPkt; 
        }

         
    }
    
    // TCPSend Constructor
    public TCPSend(String fileName, int mtu, int windowSize) throws SocketException, BufferSizeException {
        sendBuffer = new SenderBuffer(bufferSize, mtu, windowSize);
        packetManager = new PacketManager(windowSize);
        filePath = Paths.get(fileName);
        this.mtu = mtu; 
    }

    // Main running program
    public void work(int localPort, InetAddress remoteIp, int remotePort) throws InterruptedException {
        try {
            udpSocket = new DatagramSocket(localPort);
            //try to handshake until connection established 
            while(! estConnection(remoteIp, remotePort)){}
            Thread T1_fileToBuffer = new Thread(new FileToBuffer());
            Thread T2_newPacketSender = new Thread(new NewPacketSender( remoteIp, remotePort));
            
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

    public boolean estConnection( InetAddress remoteIp, int remotePort){
        //datagram connect
        try{
        udpSocket.connect(remoteIp, remotePort);

        Packet synPkt = new Packet(packetManager.getLocalSequenceNumberCounter(),System.currentTimeMillis());
        Packet.setFlag(synPkt, true, false, false);
        Packet.calculateAndSetChecksum(synPkt);
        //send SYN
        DatagramPacket udpSyn = toUDP(synPkt, remoteIp, remotePort);
        udpSocket.send(udpSyn);
        //wait to receive SYN+ ACK
        udpSocket.setSoTimeout(initTimeOut);
        byte[] r = new byte[256]; //pkt buffer from reverse direction 
        DatagramPacket dgR = new DatagramPacket(r, r.length); //datagram of r 
        udpSocket.receive(dgR);

        
        //checksum 
        Packet synAckPkt = Packet.deserialize(r);
        if(! synAckPkt.verifyChecksum()){
            return false; 
        }
        //check flag
        if(! ( Packet.checkSYN(synAckPkt) && Packet.checkACK(synAckPkt)) ){
            return false; 
        }
        //update remote seqNum 
        packetManager.setRemoteSeq(synAckPkt.getByteSeqNum());
        //TODO: calculate timeout 
       
        //reply with ACK
        Packet ackPkt = new Packet();
        //TODO: to be continued 




        //return false if timeout
        }catch(SocketTimeoutException ste){
            //retransmit if timeout (init timeout = 5
            System.out.println(ste);
            return false; 
        }
        catch( Exception e) {
            //IllegalArgumentException from connect() - if the IP address is null, or the port is out of range
            //other IOException 

        }
        return false; 
    }



    public String getStatisticsString(){
        Statistics statistics = this.packetManager.getStatistics();
        return "Statistics not ready!";
    }

    /*
    encapsulate a tcp pkt to udp pkt 
    */
    public DatagramPacket toUDP( Packet pkt, InetAddress remoteIp, int remotePort){
        byte[] data = Packet.serialize(pkt);
        DatagramPacket udpPkt = new DatagramPacket(data, data.length, remoteIp, remotePort);
        return udpPkt; 
    }

    
}

