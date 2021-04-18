package Packet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.NoSuchElementException;

import Statistics.*;

public class PacketManager {
    private final int windowsSize; //in number of segments
    private PriorityBlockingQueue<PacketWithInfo> queue;
    private Statistics statistics;
    /** 
     * Sender: localSequenceNumber is the sequence number to be put on Packet.byteSeqNum. Get this value with getLocalSequenceNumber(). Once a packet is made, localSequenceNumber needs to be incremented by data length using incrementLocalSequenceNumber(). Only use setLocalSequenceNumber() in initial setup phase. 
     * */
    private int localSequenceNumber;
    
    /**
     * Sender: This is the last received byte's sequence number. Fill in `remoteSequenceNumber + 1` in outgoing packet's ACK field
     * Receiver: This is the last contiguous byte received
     */
    private int remoteSequenceNumber;
    private boolean allPacketsEnqueued;
    

    public PacketManager( int windowSize, Comparator<PacketWithInfo> cmp){
        this.windowsSize = windowSize;
        this.remoteSequenceNumber = this.localSequenceNumber = 0;
        statistics = new Statistics();
        this.allPacketsEnqueued = false;
        queue = new PriorityBlockingQueue<PacketWithInfo>(11, cmp);
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
     * @param inc the amount to be added to localSequenceNumber
     */
    public synchronized void incrementLocalSequenceNumber(int inc){
        this.localSequenceNumber += inc;
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

    /*********************************************************************/
    /**********************          Sender         **********************/
    /*********************************************************************/

    /**
    Sender T4: This function scan through the queue and checking unexpired packets all time 
    retransmit and set new timeout during the process
    */
    public synchronized void checkExpire( DatagramSocket udpSocket, int remotePort, InetAddress remoteIp) throws IOException {
        //while ! all packet enqueued
            //if the queue not empty: cheking timout until find unexpired packet 
                //if unexpired pkt found 
            //if queue empty, sleep until sender buffer put() notify 
        //end while, another while loop to check until queue empty 

        while( !allPacketsEnqueued){
            if(this.queue.isEmpty()){
                // notify T2 to put packet to queue
                notifyAll();
                try{
                    wait();
                } catch (InterruptedException e) {}
            }

            //check packets and retransmit until find unexpired packets 
            //wait one timeout unit if unexpired found 
            try {
                helperCheckExpire(udpSocket, remotePort, remoteIp);
            }
            catch (NoSuchElementException e) {
                System.err.println("PacketManager.checkExpire: queue is empty! Message: " + e);
            }
        }
        
        // no more new packet will be put in queue. Deal with remaining packets in queue.
        while( !this.queue.isEmpty()){
            try {
                helperCheckExpire(udpSocket, remotePort, remoteIp);
            }
            catch (NoSuchElementException e) {
                System.err.println("PacketManager.checkExpire: queue is empty! Message: " + e);
            }
        }
        return ; 

    }

    private synchronized void helperCheckExpire( DatagramSocket udpSocket, int remotePort, InetAddress remoteIp) throws IOException, NoSuchElementException {

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
            sendUDP( head2.packet, udpSocket, remotePort, remoteIp); // May throw IOException
            
        }else{
            // the current packet not timeout 
            notifyAll();
            try{
                wait(timeRemain); 
            } catch (InterruptedException e) {}
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
    public synchronized boolean searchContinuous( int lastContinueByte, ArrayList<PacketWithInfo> pkts){
        
        if(this.getQueue().size() == 0 ){ //if all packts have been checked
            remoteSequenceNumber=lastContinueByte;
            queue.addAll(pkts);
            return true ; 
        }

        PacketWithInfo head = this.getQueue().poll(); // remove the packet with smallest seq number and add to pkts
        while( head.packet.getByteSeqNum() < lastContinueByte+1){
            pkts.add(head); 
            if(queue.size() == 0 ){
                remoteSequenceNumber=lastContinueByte;
                queue.addAll(pkts);
                return true ; 
    
            } else{
                head = queue.poll();
            }
        }

        if( head.packet.getByteSeqNum() == lastContinueByte+1){ 
            //if current seq number == next byte expect --> find continuous chunck
            // update lastContinueByte and recursively search 
            lastContinueByte = head.packet.getByteSeqNum()+ head.packet.getDataLength();
            pkts.add(head);
            return searchContinuous(lastContinueByte, pkts);
        }else{
            //if current seq number larger than next expected --> still not continuous --> return 
            pkts.add(head);
            remoteSequenceNumber=lastContinueByte;
                queue.addAll(pkts);
            return true ;


        }

    }

    public void sendUDP( Packet pkt, DatagramSocket udpSocket, int remotePort, InetAddress remoteIp) throws IOException{

        byte[] data = Packet.serialize(pkt);
        DatagramPacket udpPkt = new DatagramPacket(data, data.length, remoteIp, remotePort);
        udpSocket.send(udpPkt); 



    }
}
