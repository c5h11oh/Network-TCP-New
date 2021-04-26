import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingQueue;

import Buffer.ReceiverBuffer;
import Exceptions.BufferInsufficientSpaceException;
import Exceptions.BufferSizeException;
import Packet.*;
import Statistics.Statistics;


//java TCPend -p <port> -m <mtu> -c <sws> -f <file name>
//<snd/rcv> <time> <flag-list> <seq-number> <number of bytes> <ack number>

public class TCPRcv{
    final ReceiverBuffer rcvBuffer;
    LinkedBlockingQueue<Packet> continuousPackets;
    PacketManager packetManager;
    int bufferSize;  // will be determined in construction: 1.5*sws*mtu
    DatagramSocket udpSocket;
    int listenPort; //local port 
    int mtu;
    int windowSize; 
    String filename; 
    FileOutputStream fileOstream;
    final static int maxDatagramPacketLength = 1518; // in byte
    //remote Ip and port 
    InetAddress senderIp; 
    int senderPort; 
    //Statistics statistics;
    boolean noMoreNewPacket = false;
    //long initTime;

    /****************************************************************************/
    /******************               Constructor              ******************/
    /****************************************************************************/
    public TCPRcv(int listenPort, int mtu, int windowSize, String filename, long initTime) throws BufferSizeException{
        this.listenPort = listenPort;
        this.mtu = mtu;
        this.windowSize = windowSize;
        this.filename = filename; 
        bufferSize = mtu * windowSize * 3 / 2;
        rcvBuffer = new ReceiverBuffer(bufferSize, mtu, windowSize);
        continuousPackets = new LinkedBlockingQueue<Packet>();
        packetManager = new PacketManager(windowSize, new RcvPacketComparator(), initTime);
        
        // Note: See PacketManager.java to get localSequenceNumber and remoteSequenceNumber's definition for detailed explanation.
        // packetManager's remoteSequenceNumber stores the last continuous byte received from the sender
        // its local seq num stores any sequence number of packet sent by the receiver (mostly not change besides after SYN and FIN)
        
    }

    /****************************************************************************/
    /******************               Connection              ******************/
    /****************************************************************************/

    /*
    This function tries to receive SYN from a sender and do the receiver part of the 3 way handshake procedure 
    Will update the senderIp and senderPort field for this connectoin 
    @Return true if successfully establish connection, false otherwise 
    */
    public boolean passiveConnect(){
    
        byte[] b = new byte[ maxDatagramPacketLength];
        DatagramPacket synUDP = new DatagramPacket(b, maxDatagramPacketLength);

        try{
            //try to receive SYN 
            udpSocket.receive(synUDP); 
            
            //check flag and checksum 
            Packet synPkt = Packet.deserialize(b);
            if(!Packet.checkSYN(synPkt) || Packet.checkACK(synPkt) || Packet.checkFIN(synPkt)){ return false;}
            if(! synPkt.verifyChecksum()){ 
                packetManager.getStatistics().incrementIncChecksum(1);
                return false;}

            //if valid syn, set remote sequence number as received (should be 0) 
            packetManager.setRemoteSequenceNumber(synPkt.getByteSeqNum());
            this.senderIp = synUDP.getAddress();
            this.senderPort = synUDP.getPort();

            //reply with SYN and ACK and incr loacl sequence number by 1 
            Packet sap = makeSAPacket(packetManager); 
            packetManager.receiverSendUDP(sap, udpSocket,  senderPort, senderIp);
            packetManager.increaseLocalSequenceNumber(1);

            //receive ACK 
            b = new byte[maxDatagramPacketLength];
            DatagramPacket a = new DatagramPacket( b, maxDatagramPacketLength);
            udpSocket.receive(a);
            Packet aPkt = Packet.deserialize(b);
            if(!Packet.checkACK(aPkt)){ return false;}
            if(Packet.checkSYN(aPkt) || Packet.checkFIN(aPkt)) { return false;}
            if(! synPkt.verifyChecksum()){ 
                packetManager.getStatistics().incrementIncChecksum(1);
                return false;}
            if(aPkt.getACK() != packetManager.getLocalSequenceNumber()){return false;}


        
        }catch(IOException ioe){
            System.err.println("Receiver fails to establish connection: " + ioe);
            return false;
        }

        return true;
    
    }

    /*
    This function will be called after receive a  FIN message 
    correct ACK should be checked at the sender side 
    @param finPkt: the FIN Packet received
    @Return true if successfully close, false otherwise 
    */
    public boolean passiveClose( Packet finPkt ){

        try{
        //reply ACK
        Packet a = packetManager.makeACKPacket();
        assert a.getACK() == finPkt.getByteSeqNum()+1 : "receiver close wrong ACK replied to FIN";
        packetManager.receiverSendUDP(a, udpSocket,  senderPort, senderIp);

        //reply FIN
        Packet f = packetManager.makeFINPacket();
        packetManager.receiverSendUDP(f,udpSocket,  senderPort, senderIp );

        //receive ACK
        byte[] b = new byte[maxDatagramPacketLength];
        DatagramPacket dg = new DatagramPacket(b, b.length);
        udpSocket.receive(dg);
        Packet a2 = Packet.deserialize(b);
        if(!a2.verifyChecksum()){
            packetManager.getStatistics().incrementIncChecksum(1);
            return false;}
        if(Packet.checkFIN(a2) || Packet.checkSYN(a2) || ! Packet.checkACK(a2)){return false;}
        if(a2.getACK() != packetManager.getLocalSequenceNumber() +1){return false;}


        }catch(IOException ioe){
            System.err.println("receiver passive close fails: " + ioe);
            return false; 
        }

        //close 
        udpSocket.close();
        return true;
    }




    /*********************************************************************/
    /******************** Runnable objects (Threads) *********************/
    /*********************************************************************/
    //Thread 1: receive byte, checksum, store to pkt manager and call pkt manager's function to reply ACK
    private class ByteRcvr implements Runnable{

        public void run(){
            Packet finPkt = null;
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
                if (!checkValidDataPacket(pkt, packetManager.getStatistics())) {
                    System.out.println("Corrupted data received. Drop.");
                    continue;
                }

                if(Packet.checkFIN(pkt)) {
                    // Receive FIN. Go to closing connection state.
                    finPkt = pkt; 
                    break;
                }
                
                /** 
                 * Check if the packet's sequence number is within 
                 * (remoteSequenceNumber, remoteSequenceNumber + windowSize * mtu]. 
                 * The sequence number may wrap. Check if the # of packets in `continuousPackets` is less than or equal to `windowSize`.
                 * If the sequence number is valid and `continuousPackets` has less than `windowSize` packets, put the packet into packet manager. and call updateContinuousInfo() to put continuous packets into `continuousPackets` and update `remoteSequenceNumber` accordingly; if not, do nothing. 
                 * Lastly, reply an ACK with the latest `remoteSequenceNumber`. 
                 */

                int lowerBound = packetManager.getRemoteSequenceNumber();
                int upperBound = lowerBound + windowSize * mtu;
                boolean overflow = false;
                if (upperBound < 0) { /* overflow */ 
                    overflow = true;
                    upperBound += Integer.MAX_VALUE; 
                    upperBound += 1; 
                }

                if ( (!overflow && (pkt.byteSeqNum <= lowerBound || pkt.byteSeqNum > upperBound)) ||
                     ( overflow &&  pkt.byteSeqNum <= lowerBound && pkt.byteSeqNum > upperBound )       ) {
                        // outside window packet. do nothing.
                }
                else if (continuousPackets.size() >= windowSize) {
                        // although new packet is in window range, continuousPackets has no space. do nothing.
                }
                else {
                        // new packet is in window range. continuousPackets has space. put it in packetManager. update `continuousPackets` and `remoteSequenceNumber`.
                    PacketWithInfo pwi = new PacketWithInfo(pkt);
                    packetManager.getQueue().add(pwi);
                    updateContinuousInfo();
                }

                // send ACK packet
                Packet ackPckt = packetManager.makeACKPacket(); // get `remoteSequenceNumber` from packetManager
                try{
                    packetManager.receiverSendUDP(ackPckt, udpSocket, senderPort, senderIp);
                }catch( IOException ioe){
                    System.out.println("In TCPRcv ByteRcvr thread: fail to send ACK reply when new packet received: " + ioe);
                    System.exit(1);
                }
                
            } // end of while(true) 
            

            // tell other threads in receiving side that no more packets will come
            synchronized (continuousPackets) {   
                // No more packets. Notify thread 2 in case it is waiting for new packet arriving.
                noMoreNewPacket = true;
                continuousPackets.notifyAll();
            }
            // After receiving FIN we reach here. Need to send appropriate packets to sender to close connection.
            
            passiveClose(finPkt);


        }
        /**
         * update `continuousPackets` and `remoteSequenceNumber` according to `remoteSequenceNumber`
         */
        private void updateContinuousInfo(){
            LinkedList<PacketWithInfo> pwiToBePutBack = new LinkedList<PacketWithInfo>();
            int seqNumPrevExamined = -1;
            int seqNumLookingFor = packetManager.getRemoteSequenceNumber() + 1;
            if (seqNumLookingFor < 0) { seqNumLookingFor = 0; } // wrap

            while( !packetManager.getQueue().isEmpty() ) {
                PacketWithInfo pwi = packetManager.getQueue().poll();
                
                if ( pwi.packet.byteSeqNum == seqNumPrevExamined ) {
                    // do nothing == throw this duplicate PacketWithInfo
                    //potential discarded disorder pkt
                    packetManager.getStatistics().incrementOutSeqDiscardCount();
                    continue;
                } 
                else if ( pwi.packet.byteSeqNum > seqNumLookingFor ) {
                    // put back this discontinued PacketWithInfo in the front
                    pwiToBePutBack.add(pwi);
                    break;
                }
                else if ( pwi.packet.byteSeqNum == seqNumLookingFor ) {
                    // the next continuous PacketWithInfo. put it in continuousPackets' tail
                    synchronized (continuousPackets) {
                        continuousPackets.add(pwi.packet);
                        continuousPackets.notifyAll();                            
                    }

                    // update the remote sequence number, the seq number we're looking for in the next iteration
                    packetManager.increaseRemoteSequenceNumber(pwi.packet.getDataLength()); 
                    seqNumLookingFor = packetManager.getRemoteSequenceNumber() + 1;
                    if (seqNumLookingFor < 0) { seqNumLookingFor = 0; } // wrap
                }
                else { // pwi.packet.byteSeqNum < seqNumLookingFor. 
                    // may be wrapped PacketWithInfo in our front. put back.
                    pwiToBePutBack.add(pwi);
                }
                // save current packet's sequence number for checking dup packets in the next iteration
                seqNumPrevExamined = pwi.packet.byteSeqNum;
            }

            // put back PacketWithInfo in pwiToBePutBack to packetManager
            try {
                while (true) {
                    packetManager.getQueue().add( pwiToBePutBack.remove() );
                }
            } catch (NoSuchElementException e) {}
        }

    }

    /** Thread 2: get continuous packets and put it into buffer */
    private class PacketToBuffer implements Runnable {
        public void run() {
            while ( !noMoreNewPacket ) {
                // Try to get packet from `continuousPackets`. If it is empty, wait.
                // continuousPackets itself is thread safe. We use synchronized to ensure `noMoreNewPacket`'s thread-safe attribute.
                synchronized(continuousPackets) {
                    while (continuousPackets.isEmpty()){
                        if (noMoreNewPacket) break;
                        try {
                            // continuousPackets.notifyAll(); // continuousPackets is a lock shared by thread 1 and thread 2. Thread 1 never sleeps. only thread 2 wait() on rcvBuffer, and thread 1 wakes it up.
                            continuousPackets.wait();
                        } catch (InterruptedException e) {}
                    }
                    if (noMoreNewPacket) break; // breaking outer while loop. go down.
                } // Automalically release continuousPacket lock here. See https://stackoverflow.com/questions/44327173/synchronized-statements-with-break

                // continuousPackets must be non-empty to reach here. 
                // Get continuous packets as many as possible and make them a byte array
                int bufFreeSize = rcvBuffer.checkFreeSpace(); // free space may increase after this call. Later if we want to wait(), we need to recheck free space beforehand to avoid t2 and t3 waiting for each other.
                int remainSize = bufFreeSize;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try {
                    while (continuousPackets.element().getDataLength() <= remainSize ) {
                        byte[] data = continuousPackets.remove().getData();
                        bytes.writeBytes(data);
                        remainSize -= data.length;
                    }
                } catch (NoSuchElementException e) {}
                
                // try to put bytes to rcvBuffer
                synchronized (rcvBuffer) {
                    
                    // if there is no space to put even one packet size data, `bytes` will be zero in size. wait thread 3 to notify
                    if (bytes.size() == 0) {
                        // now we hold the lock. recheck if free space is the same before going to wait().
                        if (bufFreeSize != rcvBuffer.checkFreeSpace()) {
                            // free space has changed. start from top
                            continue;
                        }
                        try {
                            rcvBuffer.notifyAll(); 
                            rcvBuffer.wait();
                        } catch (InterruptedException e) {
                            continue; // start from top
                        }
                    }
                
                    // there is space in rcvBuffer to put data
                    else {
                        try {
                            rcvBuffer.put(bytes.toByteArray());
                        } catch (BufferInsufficientSpaceException e) {
                            // Shouldn't be here. We've checked free space.
                            System.err.println("TCPRcv: Thread 2: insufficient buffer size: " + e);
                            System.exit(1);
                        }
                        //update data received in statistics when still have new packet coming 
                        packetManager.getStatistics().incrementValidDataByte(bytes.size());

                        rcvBuffer.notifyAll();
                    }
                } // release rcvBuffer lock
            }

            // no more new incoming packets. move all packets to rcvBuffer bytes
            while ( !continuousPackets.isEmpty() ) {
                int bufFreeSize = rcvBuffer.checkFreeSpace();
                int remainSize = bufFreeSize;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try {
                    while (continuousPackets.element().getDataLength() <= remainSize ) {
                        byte[] data = continuousPackets.remove().getData();
                        bytes.writeBytes(data);
                        remainSize -= data.length;
                    }
                } catch (NoSuchElementException e) {}

                synchronized (rcvBuffer) {
                    if (bytes.size() == 0) {
                        if (bufFreeSize != rcvBuffer.checkFreeSpace()) {
                            continue;
                        }
                        try {
                            rcvBuffer.notifyAll();
                            rcvBuffer.wait();
                        } catch (InterruptedException e) {
                            continue; // start from top
                        }
                    }
                    else {
                        try {
                            rcvBuffer.put(bytes.toByteArray());
                        } catch (BufferInsufficientSpaceException e) {
                            // Shouldn't be here. We've checked free space.
                            System.err.println("TCPRcv: Thread 2: insufficient buffer size: " + e);
                            System.exit(1);
                        }
                        packetManager.getStatistics().incrementValidDataByte(bytes.size());
                        rcvBuffer.notifyAll();
                    }
                } // release rcvBuffer lock
            }

            // all data are in rcvBuffer. tell thread 3 this good news in case it is waiting
            rcvBuffer.setNoMoreNewByteToTrue();
        }
    }
    
    /** Thread 3: retrieve data from rcvBuffer and store it to the file system */
    private class BufferToFile implements Runnable {
        public void run(){
            // Note that it is because we only have one putter (thread 2) and one getter (thread 3) so that we can use a single lock (rcvBuffer) to control.
            while ( rcvBuffer.getNoMoreNewByte() == false ) {
                byte[] b = rcvBuffer.waitAndGetData();

                // there is data. write all data into the file
                try {
                    fileOstream.write(b);
                } catch (IOException e) {
                    System.err.println("TCPRcv: BufferToFile: IOException when writing to file: " + e);
                    System.exit(1);
                }

                // notify thread 2 that rcvBuffer is now empty
                rcvBuffer.notifyAll();
            }

            // The buffer may contain last piece of data (or not)
            byte[] b = rcvBuffer.getData();
            if( b != null ) {
                try {
                    fileOstream.write(b);
                } catch (IOException e) {
                    System.err.println("TCPRcv: BufferToFile: IOException when writing to file: " + e);
                    System.exit(1);
                }
            }
        }
    }

    /****************************************************************************/
    /******************            Helper functions            ******************/
    /****************************************************************************/
    /*
    This function check if the packet receive is a valid data packet 
    checking flags, ack and checksum 
    */
    private boolean checkValidDataPacket( Packet pkt, Statistics stat){
        if( Packet.checkSYN(pkt)) return false; // Thread 1 has to handle FIN
        if( !Packet.checkACK(pkt)) return false; 
        // if(pkt.getACK() != this.packetManager.getLocalSequenceNumber() +1 ) return false; 
        // TODO: ^ What does this line do? Can we accept that sender does not receive our ACK? Or do you mean that pkt.getACK() should always be 1?
        //sohuld always be 1 before FIN? I think this line is not necessary beside debugging purpose 
        if(pkt.verifyChecksum()){
            return true; 
        }else{
            stat.incrementIncChecksum(1);
            return false; 
        }
        
    }

    
    /*
    This function return an SYN+ACK packet with the current 'Next Byte Expected' in the ackowledge field 
    NBE should be 1 
    */
    private static Packet makeSAPacket(PacketManager pkm){
        Packet sap = new Packet(pkm.getLocalSequenceNumber());
        sap.setACK(
            pkm.getRemoteSequenceNumber() == Integer.MAX_VALUE ? 0 :( pkm.getRemoteSequenceNumber() + 1) ) ;
        Packet.setFlag(sap, true, false ,true );
        Packet.calculateAndSetChecksum(sap); 
        return sap; 

    }

    

    /****************************************************************************/
    /******************          main work and statistics      ******************/
    /****************************************************************************/
    public void work() throws InterruptedException, IOException {
        
        try {
            fileOstream = new FileOutputStream(filename);
            this.udpSocket = new DatagramSocket( listenPort); //create new socket and bind to the specified port
            while( ! passiveConnect() ) {
                // use a while loop to check true, if false, set remote sequence number to 0
                //passiveConnect ++ remote seq num, but if connect not successful, the value should not be changed
                packetManager.setRemoteSequenceNumber(0);
            }
            
            // Send SYN + ACK and start thread 1

            // Thread 1: to rcv packet and put into packet manager. handle FIN and close connection
            Thread T1_storePacketAndACK = new Thread(new ByteRcvr());
            // Thread 2: Get data from packet manager and put them to rcvBuffer
            Thread T2_packetToBuffer = new Thread(new PacketToBuffer());
            // Thread 3: retrieve data from rcvBuffer and store it to the file system
            Thread T3_bufferToFile = new Thread(new BufferToFile());

            T1_storePacketAndACK.start();
            T2_packetToBuffer.start();
            T3_bufferToFile.start();

            T1_storePacketAndACK.join();
            T2_packetToBuffer.join();
            T3_bufferToFile.join();
            fileOstream.close();

        }
        catch (java.io.FileNotFoundException e) {
            System.err.println("TCPRcv: work(): the file \"" + filename + "\" exists but is a directory rather than a regular file, does not exist but cannot be created, or cannot be opened for any other reason. Abort.");
            System.exit(1);
        }
        catch( SocketException se){
            System.err.println("TCPRcv: work(): SocketException: " + se); 
            System.exit(1);
        }
    }

    // Get statistics
    public String getStatisticsString() {
        
        Statistics statistics = this.packetManager.getStatistics();
        return statistics.receiverStat();
    }
}
