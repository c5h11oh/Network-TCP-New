import Packet.Packet;

public class Timeout {
    /**
     * All timeouts are in nanoseconds.
     */

    private double a;
    private double b;
    private long estRTT;
    private long estDEV;
    private long timeout;

    /**
     * Timeout constructor. Set RTT as initTimeout/2
     * @param a EWMA coefficient for estimating RTT (ERTT := a*ERTT + (1-a)*SRTT)
     * @param b EWMA coefficient for estimating deviation (EDEV := b*EDEV + (1-b)*SDEV)
     * @param initTimeout Initial timeout value (in ns)
     */
    public Timeout(double a, double b, long initTimeout){
        this.a = a;
        this.b = b;
        this.timeout = initTimeout;
        this.estRTT = initTimeout / 2;
        this.estDEV = 0;
        System.out.println("Timeout: initially" + this.timeout/1000000 + "milliseconds.");
    }
    
    public long getTimeout(){
        return timeout;
    }

    public long getTimeoutInMilli(){
        return timeout / 1000000;
    }

    public void setTimeout( long nanoTimeOut){
        this.timeout = nanoTimeOut;
    }

    public void update(Packet packet){
        if(packet.getByteSeqNum() == 0) {
            // This is the first received packet (in 3-way handshake, SYN+ACK)
            estRTT = System.nanoTime() - packet.getTimeStamp();
            estDEV = 0;
            timeout = 2 * estRTT;
        }
        else{
            long sampleRTT = System.nanoTime() - packet.getTimeStamp();
            long sampleDEV = Math.abs(sampleRTT - estRTT);
            estRTT = (long)(a * estRTT + (1-a) * sampleRTT);
            estDEV = (long)(b * estDEV + (1-b) * sampleDEV);
            timeout = estRTT + 4 * estDEV;
        }
        System.out.println("Timeout: updated timeout: " + this.timeout / 1000000 + "milliseconds.");
    }

}
