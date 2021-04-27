package Packet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
// import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.lang.Math;

import Statistics.*;
import Timeout.*; 

public class PacketManager {
    private final int windowsSize; //in number of segments
    private PriorityBlockingQueue<PacketWithInfo> packetsWithInfo;
    private Statistics statistics;
    private int localSequenceNumber;
    private int remoteSequenceNumber; // Fill in `remoteSequenceNumber + 1` in my outgoing packet's ACK field
    //last continuous byte received for the receiver 
    private boolean allPacketsEnqueued;
    

    public PacketManager( int windowSize, Comparator<PacketWithInfo> cmp){
        this.windowsSize = windowSize;
        this.remoteSequenceNumber = this.localSequenceNumber = 0;
        statistics = new Statistics();
        this.allPacketsEnqueued = false;
        packetsWithInfo = new PriorityBlockingQueue<PacketWithInfo>(11, cmp);
    }

    /*********************************************************************/
    /**********************   Sender and Receiver   **********************/
    /*********************************************************************/

    public PriorityBlockingQueue<PacketWithInfo> getQueue(){
        return this.packetsWithInfo;
    }

    public synchronized void setRemoteSequenceNumber(int remoteSeq){
        this.remoteSequenceNumber = remoteSeq;
    }

    public synchronized int getRemoteSequenceNumber(){
        return this.remoteSequenceNumber;
    }

    public synchronized int getLocalSequenceNumber(){
        return this.localSequenceNumber;
    }

    public synchronized void setLocalSequenceNumber( int localSeq){
        this.localSequenceNumber = localSeq;
        return;
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

    /*
    This function scan through the queue and checking unexpired packets all time 
    retransmit and set new timeout during the process
    */
    public synchronized void checkExpire( DatagramSocket udpSocket, int remotePort, InetAddress remoteIp){
        //while ! all packet enqueued
            //if the queue not empty: cheking timout until find unexpired packet 
                //if unexpired pkt found 
            //if queue empty, sleep until sender buffer put() notify 
        //end while, another while loop to check until queue empty 

        while( !allPacketsEnqueued){
            if(this.packetsWithInfo.isEmpty()){
                //notify other thread to put data and wait 
                notifyAll();
                try{
                    wait();
                } catch (InterruptedException e) {}
            }

            //check packets and retransmit until find unexpired packets 
            //wait one timeout unit if unexpired found 
            boolean retransmitted = helperCheckExpire(udpSocket, remotePort, remoteIp);
            assert retransmitted:  "In timeout checker, retransmission error: ";     
            
        }
        //now all data enqueued 
        while( !this.packetsWithInfo.isEmpty()){
            //check timeout and retransmist 
            assert helperCheckExpire(udpSocket, remotePort, remoteIp): "In timeout checker, retransmission error: "; 
        }
        return ; 

    }

    private  synchronized boolean helperCheckExpire( DatagramSocket udpSocket, int remotePort, InetAddress remoteIp){

        PacketWithInfo head = this.packetsWithInfo.poll(); 
            
        //expire 
        if( (head.timeOut + head.packet.timeStamp) > System.nanoTime() ){
            //retransmit the packet 
           

            PacketWithInfo head2 = head.getResendPacketWithInfo(this.remoteSequenceNumber);
             //add the packet back to the manager 
            packetsWithInfo.add(head2); 
            //send UDP
            try{
            sendUDP( head2.packet, udpSocket, remotePort, remoteIp);
            }catch( IOException e ){  return false; }
            
        }else{
            //the current packet not timeout 
            long waitTime = head.timeOut /  (long) (1e6); // ns to ms 
            //put the packet back and wait for timeout time 
            this.packetsWithInfo.add(head);
            notifyAll();
            try{
                wait(waitTime);
            } catch (InterruptedException e) {}
        }
        return true; 

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
            packetsWithInfo.addAll(pkts);
            return true ; 
        }

        PacketWithInfo head = this.getQueue().poll(); // remove the packet with smallest seq number and add to pkts
        while( head.packet.getByteSeqNum() < lastContinueByte+1){
            pkts.add(head); 
            if(packetsWithInfo.size() == 0 ){
                remoteSequenceNumber=lastContinueByte;
                packetsWithInfo.addAll(pkts);
                return true ; 
    
            } else{
                head = packetsWithInfo.poll();
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
                packetsWithInfo.addAll(pkts);
            return true ;


        }

    }

    public void sendUDP( Packet pkt, DatagramSocket udpSocket, int remotePort, InetAddress remoteIp) throws IOException{

        byte[] data = Packet.serialize(pkt);
        DatagramPacket udpPkt = new DatagramPacket(data, data.length, remoteIp, remotePort);
        udpSocket.send(udpPkt); 



    }
}
