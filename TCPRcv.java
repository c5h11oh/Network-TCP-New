import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;

import Buffer.ReceiverBuffer;
import Exceptions.BufferSizeException;
import Packet.*;
import Statistics.Statistics;


//java TCPend -p <port> -m <mtu> -c <sws> -f <file name>

public class TCPRcv{
    ReceiverBuffer rcvBuffer;
    PacketManager packetManager;
    int bufferSize;  // will be determined in construction: 1.5*sws*mtu
    DatagramSocket udpSocket;
    int listenPort; //local port 
    int mtu;
    int windowSize; 
    String filename; 
    final int maxDatagramPacketLength = 1518; // in byte
    //remote Ip and port 
    InetAddress senderIp; 
    int senderPort; 
    Statistics statistics;

    public TCPRcv(int listenPort, int mtu, int windowSize, String filename) throws BufferSizeException{
        this.listenPort = listenPort;
        this.mtu = mtu;
        this.windowSize = windowSize;
        this.filename = filename; 
        bufferSize = mtu * windowSize * 3 / 2;
        rcvBuffer = new ReceiverBuffer(bufferSize, mtu, windowSize);
        packetManager = new PacketManager(windowSize, new RcvPacketComparator());
        // Note: See PacketManager.java to get localSequenceNumber and remoteSequenceNumber's definition.
        // packetManager's remoteSequenceNumber stores the last contiguous byte received from the sender
        // its local seq num stores any sequence number of packet sent by the receiver (mostly not change besides after SYN and FIN)
        
    }

    //Thread 1: receive byte, checksum, store to pkt manager and call pkt manager's function to reply ACK
    private class ByteRcvr implements Runnable{

        public void run(){
            while (true) {
                //receiving new UDP packet 
                byte[] b = new byte[maxDatagramPacketLength];
                DatagramPacket p = new DatagramPacket(b, maxDatagramPacketLength);
                try{
                    udpSocket.receive(p);
                }catch(IOException ioe ){
                    System.err.println("In TCPRcv ByteRcvr: " + ioe);
                }

                Packet pkt = Packet.deserialize(b);
                if (!checkValidDataPacket(pkt)) {
                    System.out.println("Corrupted data received. Drop.");
                    continue;
                }

                if(Packet.checkFIN(pkt)) {
                    // Receive FIN. Go to closing connection state.
                    break;
                }
                
                //send to pkt manager
                if(pkt.getByteSeqNum()< packetManager.getRemoteSequenceNumber()+1 ){
                    //duplicated packet, do not add to manager queue
                    //reply ACK
                    Packet ackPckt = makeACKPacket(packetManager);
                    try{
                        packetManager.receiverSendUDP(ackPckt, udpSocket,  senderPort, senderIp);
                    }catch( IOException ioe){
                        System.out.println("In TCPRcv ByteRcvr thread: fail to send ACK reply when old packet received: " + ioe);
                        System.exit(1);
                    }
                }else{
                    //packets with larger sequence number 

                    //check dup
                    //update remote seq num if no dup && ==
                    //reply ACK 

                    if( ! packetManager.checkDupPacket(pkt.getByteSeqNum())){
                        PacketWithInfo pp = new PacketWithInfo(pkt);
                        packetManager.getQueue().add(pp);

                        if(pkt.getByteSeqNum() == packetManager.getRemoteSequenceNumber()+1){
                            int lastContinueByte = pkt.getByteSeqNum() + pkt.getDataLength() - 1;
                            ArrayList<PacketWithInfo> pkts = new ArrayList<>();
                            packetManager.searchContinuous( lastContinueByte, pkts);//search for continuous chunk after adding this packet, update remoteSequenceNumber
                        } 

                    }

                    //if larger seq number ( out-of-order packet): reply ACK without update remote seq num 
                    Packet ackPckt = makeACKPacket(packetManager);
                    try{
                        packetManager.receiverSendUDP(ackPckt, udpSocket,  senderPort, senderIp);
                    }catch( IOException ioe){
                        System.out.println("In TCPRcv ByteRcvr thread: fail to send ACK reply when new packet received: " + ioe);
                        System.exit(1);
                    }
                }
            } // end of while(true)

            // TODO: Close connection
        }

    }

    /** Thread 2: get contiguous packets and put it into buffer */
    private class PacketToBuffer implements Runnable {
        public void run() {

        }
    }
    
    /** Thread 3: composing the data and store it in the file system */
    private class BufferToFile implements Runnable {
        public void run(){
            
        }
    }

    /*
    This function check if the packet receive is a valid data packet 
    checking flags, ack and checksum 
    */
    public boolean checkValidDataPacket( Packet pkt){
        if( Packet.checkSYN(pkt)) return false; // Thread 1 has to handle FIN
        if( !Packet.checkACK(pkt)) return false; 
        // if(pkt.getACK() != this.packetManager.getLocalSequenceNumber() +1 ) return false; 
        // TODO: ^ What does this line do? Can we accept that sender does not receive our ACK? Or do you mean that pkt.getACK() should always be 1?
        return pkt.verifyChecksum();
    }

    /*
    This function return an ACK packet with the current 'Next Byte Expected' in the ackowledge field 
    */
    public static Packet makeACKPacket(PacketManager pkm){
        Packet ackPkt = new Packet(pkm.getLocalSequenceNumber());
        ackPkt.setACK(
            pkm.getRemoteSequenceNumber() == Integer.MAX_VALUE ? pkm.getRemoteSequenceNumber() + 1 : 0);
        // ackPkt.setTimeStampToCurrent(); 
        Packet.setFlag(ackPkt, false, false, true);
        Packet.calculateAndSetChecksum(ackPkt);
        return ackPkt; 
    }

   

    

   

    public void work() {
        
        try{
            this.udpSocket = new DatagramSocket( listenPort); //create new socket and bind to the specified port 
            //TODO: three way handshake
            // Send SYN + ACK and start thread 1


        }catch( SocketException se){
            System.out.println("In TCPRcv work(): " + se); 
        }

        // Thread 1: to rcv packet and put into packet manager
        // When the same thread sees the sender sent FIN, it needs to react accordingly

        // Thread 2: Get data from packet manager and make it a file

    }

}
