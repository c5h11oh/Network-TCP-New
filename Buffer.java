import Exceptions.*;

public class Buffer {
    protected byte[] buf;
    protected final int bufferSize; // in byte
    protected final int windowSize; // in byte
    public Buffer(int bufferSize, int windowSize) throws BufferNegativeSizeException {
        if(bufferSize < 0 || windowSize < 0){
            throw new BufferNegativeSizeException();
        }
        this.bufferSize = bufferSize;
        this.windowSize = windowSize;
        this.buf = new byte[bufferSize];
    }
}
