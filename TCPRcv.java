import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;

import Buffer.ReceiverBuffer;
import Exceptions.BufferSizeException;
import Packet.*;


//java TCPend -p <port> -m <mtu> -c <sws> -f <file name>

public class TCPRcv{
    ReceiverBuffer rcvrBuffer;
    PacketManager packetManager;
    int bufferSize = 64 * 1024;
    DatagramSocket udpSocket;
    int listenPort; //local port 
    int mtu;
    int windowSize; 
    String filename; 
    final int maxDatagramPacketLength = 1518; // in byte
    //remote Ip and port 
    InetAddress senderIp; 
    int senderPort; 

    public TCPRcv(int listenPort, int mtu, int windowSize, String filename) throws BufferSizeException{
        this.listenPort = listenPort;
        this.mtu = mtu;
        this.windowSize = windowSize;
        this.filename = filename; 
        rcvrBuffer = new ReceiverBuffer(bufferSize, mtu, windowSize);
        packetManager = new PacketManager(windowSize, new RcvPacketComparator());
        //packetManager's remoteSequenceNumber stores the last continuous byte received from the sender
        //its local seq num stores any sequence number of packet sent by the receiver (mostly not change besides after SYN and FIN)
        
    }

    //Thread 1: receive byte, checksum, store to pkt manager and call pkt manager's function to reply ACK
    private class ByteRcvr implements Runnable{

        

        public void run(){
            //receiving new UDP packet 
            byte[] b = new byte[maxDatagramPacketLength];
            DatagramPacket p = new DatagramPacket(b, maxDatagramPacketLength);
            try{
                udpSocket.receive(p);
            }catch(IOException ioe ){
                System.out.println("In TCPRcv ByteRcvr: " + ioe);
            }

            Packet pkt = Packet.deserialize(b);
            assert checkValidDataPacket(pkt, packetManager): "corrupted data packet received";
            
            //send to pkt manager
            if(pkt.getByteSeqNum()< packetManager.getRemoteSequenceNumber()+1 ){
                //duplicated packet, do not add to manager queue
                //reply ACK
                Packet ackPckt = makeACKPacket(packetManager);
                try{
                    packetManager.sendUDP(ackPckt, udpSocket,  senderPort, senderIp);
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
                        int lastContinueByte = pkt.getByteSeqNum() + pkt.getDataLength();
                        ArrayList<PacketWithInfo> pkts = new ArrayList<>();
                        packetManager.searchContinuous( lastContinueByte, pkts);//search for continuous chunk after adding this packet, update remoteSequenceNumber
                    } 

                }

                //if larger seq number ( out-of-order packet): reply ACK without update remote seq num 
                Packet ackPckt = makeACKPacket(packetManager);
                try{
                    packetManager.sendUDP(ackPckt, udpSocket,  senderPort, senderIp);
                }catch( IOException ioe){
                    System.out.println("In TCPRcv ByteRcvr thread: fail to send ACK reply when new packet received: " + ioe);
                    System.exit(1);
                }


                
            }
            
            //TODO: do not need to worry about ack count and resend count? 



        }

    }

    // Thread 2: composing the data and store it in the file system
    private class StoreFile implements Runnable {
        public void run(){
            
        }
    }

    /*
    This function check if the packet receive is a valid data packet 
    checking flags, ack and checksum 
    */
    public static boolean checkValidDataPacket( Packet pkt, PacketManager pkm){
        if(Packet.checkFIN( pkt) || Packet.checkSYN(pkt)) return false; 
        if( !Packet.checkACK(pkt)) return false; 
        if(pkt.getACK() != pkm.getLocalSequenceNumber() +1 ) return false; 
        boolean ck = pkt.verifyChecksum();
        return ck; 
    }

    /*
    This function return an ACK packet with the current 'Next Byte Expected' in the ackowledge field 
    */
    public static Packet makeACKPacket(PacketManager pkm){
        Packet ackPkt = new Packet(pkm.getLocalSequenceNumber());
        ackPkt.setACK(pkm.getRemoteSequenceNumber() + 1);
        ackPkt.setTimeStampToCurrent();
        Packet.setFlag(ackPkt, false, false, true);
        Packet.calculateAndSetChecksum(ackPkt);
        return ackPkt; 
    }

   

    

   

    public void work() {
        
        try{
            this.udpSocket = new DatagramSocket( listenPort); //create new socket and bind to the specified port 
            //TODO: three way handshake



        }catch( SocketException se){
            System.out.println("In TCPRcv work(): " + se); 
        }

        //thread to rcv packet and send to packet manager
        //thread to get data from packet manager and send to app 

        //reply fin and close 
    }

}
