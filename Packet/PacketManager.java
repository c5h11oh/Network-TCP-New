package Packet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.PriorityBlockingQueue;

import java.util.Comparator;
import java.util.NoSuchElementException;

import Statistics.*;
import Exceptions.*;

public class PacketManager {
    private final int windowSize; //in number of segments
    private int inTransitPacket;
    private PriorityBlockingQueue<PacketWithInfo> queue;
    private Statistics statistics;
    private long programInitTime; //time in ms when TCPEnd init
    /** 
     * Sender: localSequenceNumber is the sequence number to be put on Packet.byteSeqNum. That is, the next packet's starting byte sequence number. Get this value with getLocalSequenceNumber(). Once a packet is made, localSequenceNumber needs to be incremented by data length using increaseLocalSequenceNumber(). Only use setLocalSequenceNumber() in initial setup phase. 
     * Receiver: Similarly, localSequenceNumber is also the next packet's starting sequence number. While the receiver never send data, this will be changed after sending SYN and FIN.
     * */
    private int localSequenceNumber;
    
    /**
     * Sender: This is the last received byte's sequence number. Fill in `remoteSequenceNumber + 1` in outgoing packet's ACK field
     * Receiver: stores the last continuous byte received from the sender
     */
    private int remoteSequenceNumber;
    private boolean allPacketsEnqueued;
    

    public PacketManager( int windowSize, Comparator<PacketWithInfo> cmp, long programInitTime){
        this.windowSize = windowSize;
        remoteSequenceNumber = 0;
        localSequenceNumber = 0;
        statistics = new Statistics();
        allPacketsEnqueued = false;
        queue = new PriorityBlockingQueue<PacketWithInfo>(11, cmp);
        inTransitPacket = 0;
        this.programInitTime = programInitTime;
    }

    /*********************************************************************/
    /**********************   Sender and Receiver   **********************/
    /*********************************************************************/

    public PriorityBlockingQueue<PacketWithInfo> getQueue(){
        return this.queue;
    }

    public synchronized void setRemoteSequenceNumber(int remoteSeq){
        this.remoteSequenceNumber = remoteSeq;
    }

    /**
     * Set remoteSequenceNumber by adding inc to it. Perform overflow check and wrap up value.
     * @param amount the amount to be added to remoteSequenceNumber
     */
    public synchronized void increaseRemoteSequenceNumber(int amount){
        this.remoteSequenceNumber += amount;
        if(this.remoteSequenceNumber < 0){
            this.remoteSequenceNumber += Integer.MAX_VALUE;
            this.remoteSequenceNumber += 1; 
            // add an additional one because has one more negative number than positive number
        }
    }

    public synchronized int getRemoteSequenceNumber(){
        return this.remoteSequenceNumber;
    }

    /**
     * See localSequenceNumber's description.
     */
    public synchronized int getLocalSequenceNumber(){
        return this.localSequenceNumber;
    }
    
    /**
     * See localSequenceNumber's description.
     */
    public synchronized void setLocalSequenceNumber(int localSeq){
        this.localSequenceNumber = localSeq;
        return;
    }

    /**
     * Set localSequenceNumber by adding inc to it. Perform overflow check and wrap up value. See localSequenceNumber's description.
     * @param amount the amount to be added to localSequenceNumber
     */
    public synchronized void increaseLocalSequenceNumber(int amount){
        this.localSequenceNumber += amount;
        if(this.localSequenceNumber < 0){
            this.localSequenceNumber += Integer.MAX_VALUE;
            this.localSequenceNumber += 1; 
            // add an additional one because has one more negative number than positive number
        }
    }

    public synchronized void setAllPacketsEnqueued(){
        this.allPacketsEnqueued = true;
    }

    public synchronized boolean isAllPacketsEnqueued(){
        return this.allPacketsEnqueued;
    }

    public Statistics getStatistics(){
        return this.statistics;
    }

    /*
    This function returns an ACK packet with the current 'Next Byte Expected' in the ackowledge field 
    Used for both sender and receiver 
    */
    public Packet makeACKPacket(Packet pktReceived){

        
        Packet ackPkt = new Packet(this.getLocalSequenceNumber());
        ackPkt.setACK(
        this.getRemoteSequenceNumber() == Integer.MAX_VALUE ? 0: this.getRemoteSequenceNumber() + 1 ) ;
    
        ackPkt.timeStamp = pktReceived.timeStamp;
        Packet.setFlag(ackPkt, false, false, true);
        Packet.calculateAndSetChecksum(ackPkt);
        return ackPkt; 
    }


    /*
    This function returns a FIN packet, sequence number and ACK consistent with current state of the packet manager
    */
    public Packet makeFINPacket(){
        Packet f = new Packet(this.getLocalSequenceNumber());
        Packet.setFlag(f, false, true, false);
        f.setACK( this.getRemoteSequenceNumber() + 1);
        Packet.calculateAndSetChecksum(f);
        return f; 
    }


    /*This function print out host out put
    @param p: the packet that we want to print output on
    @action: snd/ rcv depends on whether this packet is being sent/received
    */ 
    public void output(Packet p, String action){
        //snd 34.335 S - - - 0 0 0
        //<snd/rcv> <time> <flag-list> <seq-number> <number of bytes> <ack number> 
        double outputTime = (double)(System.currentTimeMillis() - this.programInitTime)/1000; //in sec
        String tStr = String.format("%.3f", outputTime);
        tStr = String.format("%8.8s", tStr);
        //String all = String.format()
        String syn = (Packet.checkSYN(p))? "S":"-" ; 
        String ack =(Packet.checkACK(p))? "A":"-" ; 
        String fin = (Packet.checkFIN(p))? "F":"-" ; 
        String d = "-";
        if(!Packet.checkSYN(p) && !Packet.checkFIN(p) && Packet.checkACK(p)){
            if(p.getDataLength()>0){
                d = "D";
            }
        }
        
        //8 %

        System.out.printf("%s %s %s %s %s %s %d %d %d\n", action, tStr, syn, ack, fin, d, p.getByteSeqNum(),p.getDataLength(), p.getACK());


    }

    /*********************************************************************/
    /**********************          Sender         **********************/
    /*********************************************************************/

    /**
     * To maintain appropriate sliding window, all sender threads (except three-way handshake and teardown) should send data via this method. 
     * this: If the data is new (not retransmit), increments `inTransitPacket` after sending the packet.
     * pwi: This method sets boolean `sent` to true after sending the packet.
     * pwi.packet: This method updates timestamp, ACK, and set appropriate flag, and then recalculate checksum.
     * 
     * How to call this method: thread 2 (new data) should use trySendNewData() to invoke this function, thread 3 (triple duplicate ACK) should use dupACKFastRetransmit(), and thread 4 (timeout) should use helperCheckExpire().
     * @param isNewData if the packet is new data, or a retransmit one
     * @param pwi PacketWithInfo
     * @param udpSocket
     * @param remotePort
     * @param remoteIp
     * @throws IOException
     */
    private synchronized void senderSendUDP( boolean isNewData, PacketWithInfo pwi, DatagramSocket udpSocket, int remotePort, InetAddress remoteIp) throws ExceedWindowSizeException, DebugException {
        // need to check if we will exceed window size if this is new data packet
        if((isNewData == true) && (inTransitPacket == windowSize)) {
            throw new ExceedWindowSizeException();
        }
        
        pwi.packet.setTimeStampToCurrent();
        Packet.setFlag(pwi.packet, false, false, true);
        pwi.packet.setACK(this.getRemoteSequenceNumber() +1 );
        Packet.calculateAndSetChecksum(pwi.packet);

        byte[] data = Packet.serialize(pwi.packet);
        DatagramPacket udpPkt = new DatagramPacket(data, data.length, remoteIp, remotePort);
        try {
            udpSocket.send(udpPkt);
            output(pwi.packet, "snd");
            pwi.sent = true;

            if (isNewData == true) {
                System.out.println("Increment inTransitPacket");
                ++inTransitPacket;
            }
            if (inTransitPacket > windowSize) {
                throw new DebugException();
            }
        } catch (IOException e) {
            System.err.println(Thread.currentThread() + ": " + getClass().getName() + "::senderSendUDP IOException when sending packet with seq number " + pwi.packet.byteSeqNum + ". Will skip this send and continue. Exception info: " + e);
        }

        notifyAll();
    }
    
    /*
    This function try to send a new Data packet and return the TCP pkt (Packet) sent successfully 
    Return null if not send  
    */
    public synchronized Packet trySendNewData(DatagramSocket udpSocket, int remotePort, InetAddress remoteIp) throws IOException, DebugException {
        int vacancy = windowSize - inTransitPacket;
        Packet lastSent = null; 
        
        if (vacancy < 0) {
            throw new DebugException();
        }
        while (vacancy == 0) {
            notifyAll();
            try {
                // System.out.println("Thread: " + Thread.currentThread().getName() + " is now going to sleep at " + this.getClass().getName() + "::trySendNewData()" );
                wait();
            } catch (InterruptedException e) {}
            // System.out.println("Thread: " + Thread.currentThread().getName() + " is now woken up from " + this.getClass().getName() + "::trySendNewData()" );
            // System.out.println("Thread: " + Thread.currentThread().getName() + ": before recalculate, vacancy = " + vacancy + ". (should be 0)");
            vacancy = windowSize - inTransitPacket;
            // System.out.println("Thread: " + Thread.currentThread().getName() + ": after recalculate, vacancy = " + vacancy + ".");
        }

        for(PacketWithInfo p : this.queue) {
            if((p.sent == false) && (vacancy > 0)) {
                try {
                    senderSendUDP(true, p, udpSocket, remotePort, remoteIp);
                }catch (ExceedWindowSizeException e) {
                    System.err.println("PacketManager: trySendNewData: abnormal: " + e);
                    System.exit(1);
                }
                // output(p.packet, "snd"); // senderSendUDP has it
                lastSent = p.packet; 
                --vacancy;
            }
            else if (vacancy == 0) break;
        }
        return lastSent; 
    }

    /**
     * Sender T2: Call this function if it needs to retransmit packet because of duplicate ACKs.
     * @throws IOException
     */
    public synchronized void dupACKFastRetransmit(PacketWithInfo pwi, DatagramSocket udpSocket, int remotePort, InetAddress remoteIp) throws DebugException {
        try {
            senderSendUDP(false, pwi, udpSocket, remotePort, remoteIp);
        }catch (ExceedWindowSizeException e) {
            System.err.println("PacketManager: dupACKFastRetransmit: abnormal: " + e);
            System.exit(1);
        }
        // output( pwi.packet, "snd"); // senderSendUDP has it
    }

    /**
     * Sender T2: Call this function whenever we remove one PacketWithInfo from PacketManager.queue
     */
    public synchronized void decrementInTransitPacket() throws DebugException {
        inTransitPacket -= 1;
        System.out.println("Decremented inTransitPacket to " + inTransitPacket);
        if ( inTransitPacket < 0 ) {
            throw new DebugException();
        }
        this.notifyAll();
    }
    
    /**
    Sender T3: This function scan through the queue and checking unexpired packets all time 
    retransmit and set new timeout during the process
    */
    public synchronized void checkExpire( DatagramSocket udpSocket, int remotePort, InetAddress remoteIp) throws IOException, NoSuchElementException, DebugException {
        //while ! all packet enqueued
            //if the queue not empty: cheking timout until find unexpired packet 
                //if unexpired pkt found 
            //if queue empty, sleep until sender buffer put() notify 
        //end while, another while loop to check until queue empty 

        while( !allPacketsEnqueued){

            
            if (this.queue.isEmpty()){
                // notify T2 to put packet to queue
                synchronized(this) {
                    notifyAll();
                    try{
                        // System.out.println("Thread: " + Thread.currentThread().getName() + " is now going to sleep at " + this.getClass().getName() + "::checkExpire()" );
                        wait();
                    } catch (InterruptedException e) {}
                    // System.out.println("Thread: " + Thread.currentThread().getName() + " is now woken up from " + this.getClass().getName() + "::checkExpire()" );
                }
            }

            if (this.queue.isEmpty()) {
                continue;
            }

            //check packets and retransmit until find unexpired packets 
            //wait one timeout unit if unexpired found 
            helperCheckExpire(udpSocket, remotePort, remoteIp);

            try{
                Thread.sleep(10);
            } catch (InterruptedException e) {}
        }
        
        // no more new packet will be put in queue. Deal with remaining packets in queue.
        while( !this.queue.isEmpty()){
            helperCheckExpire(udpSocket, remotePort, remoteIp);
        }
        return ; 

    }

    /**
     * Sender T3: Checking timeout. Only called by checkExpire()
     */
    private void helperCheckExpire( DatagramSocket udpSocket, int remotePort, InetAddress remoteIp) throws IOException, NoSuchElementException, DebugException {

        PacketWithInfo head = this.queue.element(); // May throw NoSuchElementException. Logically it shouldn't since we've checked the queue is not empty.
        
        long timeRemain = ((head.timeOut + head.packet.timeStamp) - System.nanoTime()) / 1000000; // in ms
        // check if the frontmost packet is timeout. If so, poll such timeout packet, make a "resend packet" from it, send such "resend packet", and put "resend packet" into the packetManager. If not, wait until the 
        if( timeRemain <= 0 ){ // timeout. retransmit the packet.
            // remove timeout packet
            this.queue.remove(); // May throw NoSuchElementException. Logically it shouldn't since we've checked the queue is not empty.
            
            PacketWithInfo head2 = head.getResendPacketWithInfo(this.remoteSequenceNumber);
            // add the packet back to the manager 
            queue.add(head2); 
            
            // send UDP
            try {
                senderSendUDP(false, head2, udpSocket, remotePort, remoteIp); // May throw IOException
            } catch (ExceedWindowSizeException e) {
                System.err.println("PacketManager: helperCheckExpire: abnormal: " + e);
                System.exit(1);
            }
            // output(head2.packet, "snd"); // senderSendUDP has it
            this.getStatistics().incrementRetransCount();

        }else{
            // the current packet not timeout 
            // System.out.printf("Curr time: %f, expire time: %f\n", (double)System.nanoTime()/1000000,  (double)(head.timeOut + head.packet.timeStamp)/1000000);
            // System.out.println("time out value: " + (head.timeOut / 1000000000) + " s" );
            notifyAll();
            try{
                // System.out.println("Thread: " + Thread.currentThread().getName() + " is now going to sleep at " + this.getClass().getName() + "::helperCheckExpire()" );
                wait(timeRemain); 
            } catch (InterruptedException e) {}
            // System.out.println("Thread: " + Thread.currentThread().getName() + " is now woken up from " + this.getClass().getName() + "::helperCheckExpire()" );

        }
    }

    /*********************************************************************/
    /**********************         Receiver        **********************/
    /*********************************************************************/

    /*
    this function check if a given seqNum has already had a packet with the seqNum present in packet manager's queue 
    AKA: check for duplicate packet 
    */
    public synchronized boolean checkDupPacket(int seqNum){
        for( PacketWithInfo pp: this.getQueue()){
            if(pp.packet.getACK() == seqNum){
                return true;
            }
        }
        return false; 
    }

     
    /*
    This function check if the packet manager contains packets with continous chunk of data after receiving a new packet 
    should always return true: finish checking packets 
    */
    // public synchronized boolean searchContinuous( int lastContinueByte, ArrayList<PacketWithInfo> pkts){
        
    //     if(this.getQueue().size() == 0 ){ //if all packts have been checked
    //         remoteSequenceNumber=lastContinueByte;
    //         queue.addAll(pkts);
    //         return true ; 
    //     }

    //     PacketWithInfo head = this.getQueue().poll(); // remove the packet with smallest seq number and add to pkts
    //     while( head.packet.getByteSeqNum() < lastContinueByte+1){
    //         pkts.add(head); 
    //         if(queue.size() == 0 ){
    //             remoteSequenceNumber=lastContinueByte;
    //             queue.addAll(pkts);
    //             return true ; 
    
    //         } else{
    //             head = queue.poll();
    //         }
    //     }

    //     if( head.packet.getByteSeqNum() == lastContinueByte+1){ 
    //         //if current seq number == next byte expect --> find continuous chunck
    //         // update lastContinueByte and recursively search 
    //         lastContinueByte = head.packet.getByteSeqNum()+ head.packet.getDataLength();
    //         pkts.add(head);
    //         return searchContinuous(lastContinueByte, pkts);
    //     }else{
    //         //if current seq number larger than next expected --> still not continuous --> return 
    //         pkts.add(head);
    //         remoteSequenceNumber=lastContinueByte;
    //             queue.addAll(pkts);
    //         return true ;
    //     }
    // }

    public void receiverSendUDP( Packet pkt, DatagramSocket udpSocket, int remotePort, InetAddress remoteIp) throws IOException{

        byte[] data = Packet.serialize(pkt);
        DatagramPacket udpPkt = new DatagramPacket(data, data.length, remoteIp, remotePort);
        udpSocket.send(udpPkt);
        output(pkt, "snd"); //receiver send ack 

        
    }

}
