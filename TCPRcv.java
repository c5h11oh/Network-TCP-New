import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;


import Buffer.ReceiverBuffer;
import Exceptions.BufferSizeException;
import Packet.*;


//java TCPend -p <port> -m <mtu> -c <sws> -f <file name>
public class TCPRcv{
    ReceiverBuffer rcvrBuffer;
    PacketManager packetManager;
    int bufferSize = 64 * 1024;
    DatagramSocket udpSocket;
    int listenPort;
    int mtu;
    int windowSize; 
    String filename; 
    final int maxDatagramPacketLength = 1518; // in byte

    public TCPRcv(int listenPort, int mtu, int windowSize, String filename) throws BufferSizeException{
        this.listenPort = listenPort;
        this.mtu = mtu;
        this.windowSize = windowSize;
        this.filename = filename; 
        rcvrBuffer = new ReceiverBuffer(bufferSize, mtu, windowSize);
        packetManager = new PacketManager(windowSize, new RcvPacketComparator());
        
    }

    //Thread 1: receive byte, checksum, store to pkt manager and call pkt manager's function to reply ACK
    private class ByteRcvr implements Runnable{

        public void run(){
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
            PacketWithInfo pp = new PacketWithInfo(pkt);
            packetManager.getQueue().add(pp);
            //TODO: do not need to worry about ack count and resend count right? 



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

    public void work() {
        
        try{
         this.udpSocket = new DatagramSocket( listenPort);
        //establish connection


        }catch( SocketException se){
            System.out.println("In TCPRcv work(): " + se); 
        }

        //thread to rcv packet and send to packet manager
        //thread to get from packet manager and send to app 

        //reply fin and close 
    }

}
